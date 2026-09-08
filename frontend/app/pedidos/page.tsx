'use client';

/**
 * Meus pedidos.
 *
 * Client Component porque `/orders` é autenticado, e a API autentica por header — não há
 * token no servidor para buscar. Sem sessão a tela pede login em vez de mostrar erro: não
 * ter entrado ainda não é uma falha.
 *
 * A ordem vem pronta do backend (`findByUserIdOrderByCreatedAtDesc`), então a página não
 * reordena nada — reordenar aqui só criaria uma segunda verdade sobre "mais recente".
 */

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { ApiError } from '@/lib/api';
import { useApi, useAuth } from '@/lib/auth';
import { dateTime } from '@/lib/format';
import type { Order, Page } from '@/lib/types';
import { Button, Card, Empty, Money, Spinner } from '@/components/ui';
import { Aviso, StatusTag, Tela } from '@/components/pagamento/pecas';
import { idCurto } from '@/components/pagamento/contrato';

const TAMANHO = 10;

export default function PedidosPage() {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <Tela largura={860}>
        <div style={{ display: 'flex', justifyContent: 'center', padding: 56 }}>
          <Spinner size={24} />
        </div>
      </Tela>
    );
  }

  if (!user) {
    return (
      <Tela largura={860}>
        <Cabecalho />
        <Aviso title="Entre para ver seus pedidos">
          Seus pedidos ficam na sua conta. <Link href="/conta">Entrar</Link>.
        </Aviso>
      </Tela>
    );
  }

  return (
    <Tela largura={860}>
      <Cabecalho />
      <Lista />
    </Tela>
  );
}

function Cabecalho() {
  return (
    <h1 style={{ margin: 0, fontSize: 26, fontWeight: 600, letterSpacing: '-.01em' }}>
      Meus pedidos
    </h1>
  );
}

/* ---------------------------------------------------------------- a lista paginada */

function Lista() {
  const call = useApi();

  const [indice, setIndice] = useState(0);
  const [pagina, setPagina] = useState<Page<Order> | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  /** Muda para pedir uma releitura da mesma página. Trocar de página já refaz sozinho. */
  const [ciclo, setCiclo] = useState(0);

  // Quem liga o spinner é o clique, que é evento; a busca só mexe no estado depois da
  // resposta. `vivo` evita escrever em componente que já saiu da tela.
  useEffect(() => {
    let vivo = true;

    const buscar = async () => {
      try {
        const lida = await call<Page<Order>>(`/orders?page=${indice}&size=${TAMANHO}`);
        if (!vivo) return;
        setPagina(lida);
        setErro(null);
      } catch (e) {
        if (!vivo) return;
        setErro(
          e instanceof ApiError && e.status === 401
            ? 'Sua sessão expirou. Entre de novo para ver seus pedidos.'
            : 'Não conseguimos carregar seus pedidos agora.',
        );
      } finally {
        if (vivo) setCarregando(false);
      }
    };

    void buscar();

    return () => {
      vivo = false;
    };
  }, [call, indice, ciclo]);

  if (carregando && !pagina) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 56 }}>
        <Spinner size={24} />
      </div>
    );
  }

  if (erro) {
    return (
      <Aviso
        tone="danger"
        title="Não deu para carregar"
        action={
          <Button
            variant="ghost"
            onClick={() => {
              setCarregando(true);
              setCiclo((c) => c + 1);
            }}
          >
            Tentar de novo
          </Button>
        }
      >
        {erro}
      </Aviso>
    );
  }

  if (!pagina || pagina.content.length === 0) {
    return (
      <Empty
        title="Nenhum pedido ainda"
        hint="Quando você fechar uma compra, ela aparece aqui com o status e o valor."
      />
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {pagina.content.map((pedido) => (
        <Linha key={pedido.id} pedido={pedido} />
      ))}

      <Paginacao
        pagina={pagina}
        carregando={carregando}
        onIr={(n) => {
          setCarregando(true);
          setIndice(n);
        }}
      />
    </div>
  );
}

/* ---------------------------------------------------------------- uma linha */

/**
 * `itemCount` é a soma das quantidades e `items.length` é quantos produtos diferentes —
 * dois números distintos, então a linha só mostra o segundo quando ele acrescenta algo.
 * O nome do produto não vem no pedido; ele aparece no detalhe, que busca o catálogo.
 */
function resumoDosItens(pedido: Order) {
  const unidades = `${pedido.itemCount} ${pedido.itemCount === 1 ? 'item' : 'itens'}`;
  const produtos = pedido.items.length;
  return produtos > 1 && produtos !== pedido.itemCount
    ? `${unidades} · ${produtos} produtos`
    : unidades;
}

function Linha({ pedido }: { pedido: Order }) {
  return (
    <Link href={`/pedidos/${pedido.id}`} style={{ color: 'var(--ink)' }}>
      <Card
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 18,
          flexWrap: 'wrap',
          padding: '16px 18px',
          minHeight: 44,
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 150 }}>
          <span className="mono" style={{ fontSize: 15, fontWeight: 500 }}>
            {idCurto(pedido.id)}
          </span>
          <span className="mono" style={{ fontSize: 12, color: 'var(--ink3)' }}>
            {dateTime(pedido.createdAt)}
          </span>
        </div>

        <span style={{ fontSize: 14, color: 'var(--ink2)', flexGrow: 1, minWidth: 120 }}>
          {resumoDosItens(pedido)}
        </span>

        <StatusTag status={pedido.status} />
        <Money value={pedido.total} size={17} />
      </Card>
    </Link>
  );
}

/* ---------------------------------------------------------------- páginas */

function Paginacao({
  pagina, carregando, onIr,
}: { pagina: Page<Order>; carregando: boolean; onIr: (n: number) => void }) {
  const primeira = pagina.page <= 0;
  const ultima = pagina.page >= pagina.totalPages - 1;

  if (primeira && ultima) {
    return (
      <p className="mono" style={{ margin: '4px 0 0', fontSize: 12, color: 'var(--ink3)' }}>
        {pagina.totalElements} {pagina.totalElements === 1 ? 'pedido' : 'pedidos'}
      </p>
    );
  }

  return (
    <div
      style={{
        display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap', marginTop: 8,
      }}
    >
      <Button variant="ghost" disabled={primeira || carregando} onClick={() => onIr(pagina.page - 1)}>
        ← Anteriores
      </Button>
      <Button variant="ghost" disabled={ultima || carregando} onClick={() => onIr(pagina.page + 1)}>
        Mais antigos →
      </Button>
      <span className="mono" style={{ fontSize: 12, color: 'var(--ink3)' }}>
        {carregando ? 'carregando…' : `página ${pagina.page + 1} de ${pagina.totalPages} · ${pagina.totalElements} pedidos`}
      </span>
    </div>
  );
}
