'use client';

/**
 * Entrar com o Google.
 *
 * O backend só aceita `POST /auth/google` com um ID token do Google, então o trabalho aqui é
 * obter esse token pelo Google Identity Services e entregá-lo ao `signInWithGoogle` — o resto
 * da sessão é problema do `lib/auth`.
 *
 * Toda tela de compra é autenticada, e nenhuma delas deve mostrar erro para quem só não
 * entrou ainda: sem sessão, é esta tela que aparece.
 */
import Link from 'next/link';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '@/lib/auth';
import { Banner, Card, Spinner } from '@/components/ui';

interface Credencial { credential?: string }

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize(config: {
            client_id: string;
            callback: (r: Credencial) => void;
            auto_select?: boolean;
          }): void;
          renderButton(el: HTMLElement, options: Record<string, unknown>): void;
        };
      };
    };
  }
}

const SRC = 'https://accounts.google.com/gsi/client';

/** Injeta o script uma vez só — StrictMode roda o efeito duas vezes em desenvolvimento. */
function carregarGsi(): Promise<void> {
  if (typeof window === 'undefined') return Promise.resolve();
  if (window.google?.accounts?.id) return Promise.resolve();

  return new Promise((resolve, reject) => {
    const existente = document.querySelector<HTMLScriptElement>(`script[src="${SRC}"]`);
    if (existente) {
      existente.addEventListener('load', () => resolve());
      existente.addEventListener('error', () => reject(new Error('gsi')));
      if (window.google?.accounts?.id) resolve();
      return;
    }
    const s = document.createElement('script');
    s.src = SRC;
    s.async = true;
    s.defer = true;
    s.onload = () => resolve();
    s.onerror = () => reject(new Error('gsi'));
    document.head.appendChild(s);
  });
}

export function Entrar({ motivo }: { motivo?: string }) {
  const { signInWithGoogle } = useAuth();
  const alvo = useRef<HTMLDivElement | null>(null);
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

  const [estadoInterno, setEstado] =
    useState<'carregando' | 'pronto' | 'entrando' | 'falhou'>('carregando');

  // Sem client id não existe login possível. Isso é conclusão sobre o ambiente, não um estado
  // que evolui, então sai derivado do render em vez de escrito por um efeito.
  const estado = clientId ? estadoInterno : 'falhou';
  const [mensagem, setMensagem] = useState<string | null>(null);


  // O callback do Google é registrado uma vez; a ref mantém a versão atual da função sem
  // forçar um `initialize` a cada render.
  const entrar = useRef(signInWithGoogle);
  useEffect(() => {
    entrar.current = signInWithGoogle;
  }, [signInWithGoogle]);

  const receber = useCallback(async (r: Credencial) => {
    if (!r.credential) return;
    setEstado('entrando');
    setMensagem(null);
    try {
      await entrar.current(r.credential);
      // Não trocamos de estado: o provedor recarrega a árvore com a sessão pronta.
    } catch {
      setEstado('falhou');
      setMensagem('O Google confirmou quem você é, mas a nossa API recusou a entrada. Tente de novo.');
    }
  }, []);

  useEffect(() => {
    if (!clientId) return;
    let vivo = true;
    carregarGsi()
      .then(() => {
        if (!vivo || !alvo.current || !window.google) return;
        window.google.accounts.id.initialize({ client_id: clientId, callback: receber });
        alvo.current.innerHTML = ''; // evita dois botões no efeito duplo do StrictMode
        window.google.accounts.id.renderButton(alvo.current, {
          type: 'standard',
          theme: 'outline',
          size: 'large',
          shape: 'rectangular',
          text: 'signin_with',
          locale: 'pt-BR',
          width: 280,
        });
        setEstado('pronto');
      })
      .catch(() => {
        if (!vivo) return;
        setEstado('falhou');
        setMensagem('Não conseguimos carregar o login do Google. Verifique sua conexão.');
      });
    return () => { vivo = false; };
  }, [clientId, receber]);

  if (!clientId) {
    return (
      <Banner tone="danger" title="Entrada indisponível">
        O login com Google não está configurado nesta instalação da loja.
      </Banner>
    );
  }

  return (
    <Card style={{ padding: 28, display: 'flex', flexDirection: 'column', gap: 16, alignItems: 'flex-start' }}>
      <div>
        <h1 style={{ margin: 0, fontSize: 20, fontWeight: 600 }}>Entre para continuar</h1>
        <p style={{ margin: '8px 0 0', fontSize: 14, color: 'var(--ink2)', lineHeight: 1.6 }}>
          {motivo ?? 'Sua conta guarda o carrinho, os endereços e os pedidos.'}
        </p>
      </div>

      {/* O botão é renderizado pelo Google dentro desta div. */}
      <div ref={alvo} style={{ minHeight: 44 }} />

      {estado === 'carregando' ? (
        <span style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--ink3)' }}>
          <Spinner size={14} /> Preparando a entrada…
        </span>
      ) : null}
      {estado === 'entrando' ? (
        <span style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--ink3)' }}>
          <Spinner size={14} /> Entrando…
        </span>
      ) : null}
      {mensagem ? (
        <span style={{ fontSize: 13, color: 'var(--danger)' }}>{mensagem}</span>
      ) : null}
    </Card>
  );
}

/**
 * Casca das telas de CLIENTE: carrinho, checkout e conta.
 *
 * Pergunta pelo papel, não por `user`. O dono tem sessão válida e mesmo assim `user` é null —
 * ele não tem linha em `users`, o `sub` dele é id de owner. Decidir por `user` deixava o
 * `Entrar` preso em "Entrando…" para sempre depois de um login de dono bem-sucedido: ele
 * troca de estado contando que a árvore o substitua, e a substituição nunca vinha.
 */
export function ParedeDeEntrada({
  motivo, children,
}: { motivo?: string; children: React.ReactNode }) {
  const { role, loading } = useAuth();

  if (loading) {
    return (
      <div style={{ padding: '80px 0', display: 'flex', justifyContent: 'center' }}>
        <Spinner size={22} />
      </div>
    );
  }

  if (!role) return <Entrar motivo={motivo} />;

  // Dono não compra por esta conta: sem perfil de cliente, o checkout recusaria mais adiante
  // com um erro que não explica nada. Melhor dizer aqui onde ele deveria estar.
  if (role === 'OWNER') {
    return (
      <Card style={{ padding: '36px 30px', maxWidth: 560, margin: '40px auto', textAlign: 'center' }}>
        <p style={{ margin: 0, fontSize: 18, fontWeight: 600 }}>Esta é a conta da loja</p>
        <p style={{ margin: '10px 0 0', fontSize: 14, lineHeight: 1.7, color: 'var(--ink2)' }}>
          O dono administra pelo painel; comprar exige uma conta de cliente. Entre com outro
          e-mail para usar o carrinho.
        </p>
        <div style={{ marginTop: 22, display: 'flex', gap: 12, justifyContent: 'center' }}>
          <Link
            href="/admin"
            style={{
              height: 46, padding: '0 22px', borderRadius: 'var(--radius)',
              display: 'inline-flex', alignItems: 'center',
              background: 'var(--ink)', color: 'var(--bg)', fontSize: 15, fontWeight: 500,
            }}
          >
            Abrir o painel
          </Link>
          <Link
            href="/"
            style={{
              height: 46, padding: '0 22px', borderRadius: 'var(--radius)',
              display: 'inline-flex', alignItems: 'center',
              border: '1px solid var(--line)', color: 'var(--ink)', fontSize: 15,
            }}
          >
            Ver a loja
          </Link>
        </div>
      </Card>
    );
  }

  return <>{children}</>;
}
