'use client';

/**
 * A origem do frete, carregada uma vez e compartilhada por todo o painel.
 *
 * Uma chamada só resolve duas perguntas. `GET /owners/origin` exige `role=OWNER`, então a
 * resposta diz ao mesmo tempo **se quem está logado é dono** (200 contra 401/403) e **se a
 * loja está vendendo** (`zipCode` nulo ou não). O token não carrega o papel de forma legível
 * pelo front — `lib/types.ts` não expõe `role` em `User` —, e sondar a própria rota que
 * interessa é mais honesto que adivinhar por um campo que não existe.
 *
 * O estado mora em contexto para que salvar o CEP em `/admin/config` apague o banner de
 * todas as telas na hora, sem recarregar a página.
 */

import Link from 'next/link';
import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { ApiError } from '@/lib/api';
import { useApi, useAuth } from '@/lib/auth';
import { cep } from '@/lib/format';
import type { ShippingOrigin } from '@/lib/types';
import { Banner, Card, Spinner } from '@/components/ui';

type OriginState =
  /** Sondando. */
  | { kind: 'loading' }
  /** Sem sessão. Cortesia de interface: quem barra de verdade é o backend. */
  | { kind: 'anon' }
  /** 403 — token válido, papel de `USER`. */
  | { kind: 'forbidden' }
  /** 500 — não existe linha em `owners`. É deploy quebrado, não erro de quem chamou. */
  | { kind: 'broken' }
  /** Rede fora, CORS, gateway. */
  | { kind: 'offline'; error: unknown }
  | { kind: 'ready'; zipCode: string | null };

interface OriginContext {
  state: OriginState;
  /** Depois do `PUT`, para o banner sumir sem nova volta ao servidor. */
  apply: (zipCode: string) => void;
  reload: () => void;
}

const Ctx = createContext<OriginContext | null>(null);

export function OriginProvider({ children }: { children: React.ReactNode }) {
  const call = useApi();
  const { token, loading: authLoading } = useAuth();
  /*
   * O que a busca trouxe, carimbado com o ciclo em que foi buscada. O estado visível é
   * DERIVADO no render, não copiado para cá por um efeito: "sem token é anônimo" é conclusão
   * sobre o que já se sabe, não um evento. Escrevê-la num efeito faria a árvore renderizar
   * uma vez com o valor velho antes de se corrigir.
   */
  const [buscado, setBuscado] = useState<{ ciclo: number; state: OriginState } | null>(null);
  const [nonce, setNonce] = useState(0);

  const state = useMemo<OriginState>(
    () =>
      authLoading
        ? { kind: 'loading' }
        : !token
          ? { kind: 'anon' }
          : buscado && buscado.ciclo === nonce
            ? buscado.state
            : { kind: 'loading' },
    [authLoading, token, buscado, nonce],
  );

  useEffect(() => {
    if (authLoading || !token) return;

    let alive = true;
    const guardar = (s: OriginState) => {
      if (alive) setBuscado({ ciclo: nonce, state: s });
    };

    call<ShippingOrigin>('/owners/origin')
      .then((origin) => guardar({ kind: 'ready', zipCode: origin?.zipCode ?? null }))
      .catch((e: unknown) => {
        if (e instanceof ApiError) {
          if (e.status === 401) return guardar({ kind: 'anon' });
          if (e.status === 403) return guardar({ kind: 'forbidden' });
          if (e.status === 500) return guardar({ kind: 'broken' });
        }
        guardar({ kind: 'offline', error: e });
      });

    return () => {
      alive = false;
    };
  }, [call, token, authLoading, nonce]);

  const value = useMemo<OriginContext>(
    () => ({
      state,
      apply: (zipCode: string) => setBuscado({ ciclo: nonce, state: { kind: 'ready', zipCode } }),
      reload: () => setNonce((n) => n + 1),
    }),
    [state, nonce],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useOrigin(): OriginContext {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useOrigin precisa estar dentro de <OriginProvider>');
  return ctx;
}

/** Atalho para as telas que só querem saber se a loja vende. */
export function useShippingConfigured(): boolean | null {
  const { state } = useOrigin();
  if (state.kind !== 'ready') return null;
  return state.zipCode !== null;
}

/* ---------------------------------------------------------------- porta */

const CTA: React.CSSProperties = {
  height: 46, padding: '0 22px', borderRadius: 'var(--radius)', flexShrink: 0,
  display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
  background: 'var(--ink)', color: 'var(--bg)', fontSize: 15, fontWeight: 500,
};

function Notice({
  title, children, cta,
}: { title: string; children: React.ReactNode; cta?: { href: string; label: string } }) {
  return (
    <Card style={{ padding: '40px 32px', maxWidth: 560, margin: '48px auto', textAlign: 'center' }}>
      <p style={{ margin: 0, fontSize: 18, fontWeight: 600 }}>{title}</p>
      <div style={{ marginTop: 10, fontSize: 14, lineHeight: 1.7, color: 'var(--ink2)' }}>
        {children}
      </div>
      {cta ? (
        <div style={{ marginTop: 22 }}>
          <Link href={cta.href} style={CTA}>{cta.label}</Link>
        </div>
      ) : null}
    </Card>
  );
}

/**
 * Mostra o painel só para quem é dono.
 *
 * Não é segurança — a rota continua acessível e o HTML continua sendo servido. É cortesia:
 * quem cai aqui sem papel de dono vê uma frase em vez de quatro telas de erro 403. A recusa
 * que vale acontece no backend, em toda chamada.
 */
export function AdminGate({ children }: { children: React.ReactNode }) {
  const { state, reload } = useOrigin();

  if (state.kind === 'loading') {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: '80px 0' }}>
        <Spinner size={24} />
      </div>
    );
  }

  if (state.kind === 'anon') {
    return (
      <Notice title="Entre para abrir o painel" cta={{ href: '/conta', label: 'Entrar' }}>
        O painel do dono precisa de uma sessão com papel de <strong>OWNER</strong>. A sessão
        dura uma hora e não se renova sozinha, então isto também aparece quando o token expira
        no meio do trabalho.
      </Notice>
    );
  }

  if (state.kind === 'forbidden') {
    return (
      <Notice title="Esta conta não é a dona da loja" cta={{ href: '/', label: 'Ir para a loja' }}>
        Você está autenticado, mas com papel de <strong>USER</strong>. O painel pertence à
        conta semeada como dona da loja — a que tem o e-mail do operador na tabela{' '}
        <span className="mono">owners</span>.
      </Notice>
    );
  }

  if (state.kind === 'broken') {
    return (
      <Notice title="A loja não está instalada">
        O servidor respondeu <span className="mono">500</span> em{' '}
        <span className="mono">GET /owners/origin</span>: não existe linha em{' '}
        <span className="mono">owners</span>. Isso é a migration <span className="mono">V2</span>,
        que semeia o dono, não tendo rodado — não é nada que dê para corrigir por esta tela.
      </Notice>
    );
  }

  if (state.kind === 'offline') {
    return (
      <Notice title="Não deu para falar com o servidor">
        <p style={{ margin: 0 }}>A API não respondeu. Verifique se o backend está no ar.</p>
        <button
          type="button"
          onClick={reload}
          style={{ ...CTA, marginTop: 18, cursor: 'pointer', border: 'none' }}
        >
          Tentar de novo
        </button>
      </Notice>
    );
  }

  return <>{children}</>;
}

/* ---------------------------------------------------------------- aviso */

/**
 * O aviso de loja parada.
 *
 * Sem CEP de origem, todo `POST /orders` é recusado com 409 e **nenhum outro sintoma
 * aparece**: o catálogo abre, o carrinho enche, e a falha só é vista pelo cliente. Por isso
 * o aviso é `danger` e vai em cima de cada tela do painel, não só na visão geral.
 */
export function OriginBanner() {
  const { state } = useOrigin();
  if (state.kind !== 'ready' || state.zipCode !== null) return null;

  return (
    <div style={{ marginBottom: 24 }}>
      <Banner
        tone="danger"
        title="A loja não está vendendo"
        action={<Link href="/admin/config" style={CTA}>Definir o CEP</Link>}
      >
        O frete é calculado pela distância entre o CEP da loja e o do cliente, e esse CEP
        ainda não foi definido. Enquanto isso, <strong>todo pedido é recusado</strong> com{' '}
        <span className="mono">SHIPPING_ORIGIN_NOT_CONFIGURED</span> — o catálogo abre normal,
        o carrinho enche normal, e só o cliente vê o erro. Leva um minuto para resolver.
      </Banner>
    </div>
  );
}

/** O CEP formatado, ou null. Usado pelo cartão de status da visão geral. */
export function useOriginZip(): string | null {
  const { state } = useOrigin();
  return state.kind === 'ready' && state.zipCode ? cep(state.zipCode) : null;
}
