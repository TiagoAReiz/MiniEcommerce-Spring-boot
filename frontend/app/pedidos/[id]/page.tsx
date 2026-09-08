'use client';

/**
 * Um pedido.
 *
 * Duas coisas que esta tela existe para dizer direito:
 *
 * 1. Pedido de outra pessoa responde **404**, igual a pedido inexistente. A tela repete o
 *    silêncio do backend — dizer "sem permissão" confirmaria que o pedido existe.
 * 2. Pedido `CANCELLED` que ainda tem `expiresAt` foi **abandonado**, não cancelado à mão.
 *    O backend preserva a data de propósito, e é o que separa uma venda perdida de uma
 *    desistência: qualquer saída deliberada de `PENDING` apaga `expires_at`.
 */

import Link from 'next/link';
import { use, useCallback, useEffect, useState } from 'react';
import { ApiError } from '@/lib/api';
import { useApi, useAuth } from '@/lib/auth';
import { brl, dateTime, secondsUntil } from '@/lib/format';
import type { Order, OrderItem } from '@/lib/types';
import { Button, Money, Spinner } from '@/components/ui';
import {
  Aviso, Bloco, CabecalhoPedido, Reserva, Resumo, Tela, VoltarPedidos,
} from '@/components/pagamento/pecas';
import { aindaPagavel } from '@/components/pagamento/contrato';

export default function PedidoPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <Tela>
        <div style={{ display: 'flex', justifyContent: 'center', padding: 56 }}>
          <Spinner size={24} />
        </div>
      </Tela>
    );
  }

  if (!user) {
    return (
      <Tela>
        <VoltarPedidos />
        <Aviso title="Entre para ver este pedido">
          Precisamos da sua sessão para consultá-lo. <Link href="/conta">Entrar</Link>.
        </Aviso>
      </Tela>
    );
  }

  return (
    <Tela>
      <Detalhe id={id} />
    </Tela>
  );
}

/* ---------------------------------------------------------------- leitura */

type Fase = 'lendo' | 'pronto' | 'ausente' | 'erro';

function Detalhe({ id }: { id: string }) {
  const call = useApi();

  const [pedido, setPedido] = useState<Order | null>(null);
  const [fase, setFase] = useState<Fase>('lendo');
  /** Muda para pedir uma releitura — o botão de erro e o fim da reserva usam isto. */
  const [ciclo, setCiclo] = useState(0);

  // Estável de propósito: o `<Countdown>` da reserva guarda `onFim` numa dependência de
  // efeito, e uma identidade nova a cada segundo reiniciaria o intervalo sem parar.
  const reler = useCallback(() => setCiclo((c) => c + 1), []);

  useEffect(() => {
    let vivo = true;

    const ler = async () => {
      try {
        const atual = await call<Order>(`/orders/${id}`);
        if (!vivo) return;
        setPedido(atual);
        setFase('pronto');
      } catch (e) {
        if (!vivo) return;
        // 404 cobre "não existe" e "não é seu". A tela não separa os dois de propósito.
        setFase(e instanceof ApiError && e.status === 404 ? 'ausente' : 'erro');
      }
    };

    void ler();

    return () => {
      vivo = false;
    };
  }, [call, id, ciclo]);

  if (fase === 'lendo') {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 56 }}>
        <Spinner size={24} />
      </div>
    );
  }

  if (fase === 'erro') {
    return (
      <>
        <VoltarPedidos />
        <Aviso
          tone="danger"
          title="Não deu para carregar o pedido"
          action={
            <Button variant="ghost" onClick={reler}>
              Tentar de novo
            </Button>
          }
        >
          Não conseguimos falar com o servidor agora.
        </Aviso>
      </>
    );
  }

  if (fase === 'ausente' || !pedido) {
    return (
      <>
        <Aviso tone="neutral" title="Pedido não encontrado">
          O endereço pode estar errado, ou este pedido não é da sua conta.
        </Aviso>
        <VoltarPedidos />
      </>
    );
  }

  const reservaCorrendo = pedido.status === 'PENDING' && (secondsUntil(pedido.expiresAt) ?? 0) > 0;
  // A data preservada é o que separa reserva vencida de cancelamento deliberado.
  const abandonado = pedido.status === 'CANCELLED' && pedido.expiresAt !== null;

  return (
    <>
      <VoltarPedidos />
      <CabecalhoPedido pedido={pedido} />

      {pedido.status === 'CANCELLED' ? (
        <Aviso
          tone="neutral"
          title={abandonado ? 'A reserva de estoque venceu' : 'Pedido cancelado'}
        >
          {abandonado ? (
            <>
              O pagamento não chegou até{' '}
              <span className="mono">{dateTime(pedido.expiresAt as string)}</span> e os itens
              voltaram ao catálogo. Nada foi cobrado — o pedido fica aqui só como registro.
            </>
          ) : (
            'O pedido foi cancelado e o estoque devolvido. Nada foi cobrado.'
          )}{' '}
          <Link href="/">Ver o catálogo</Link>.
        </Aviso>
      ) : null}

      {reservaCorrendo ? (
        <Bloco>
          <Reserva expiresAt={pedido.expiresAt} onFim={reler} />
        </Bloco>
      ) : null}

      {aindaPagavel(pedido.status) ? (
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
          <Link href={`/pagamento/${pedido.id}`}>
            <Button>Pagar este pedido</Button>
          </Link>
          <span style={{ fontSize: 13, color: 'var(--ink3)', flex: '1 1 220px', lineHeight: 1.6 }}>
            Se você já pagou, abra mesmo assim: a tela acompanha a confirmação do Mercado Pago.
          </span>
        </div>
      ) : null}

      <Bloco titulo="Itens">
        <Itens itens={pedido.items} />
      </Bloco>

      <Bloco titulo="Valores">
        <Resumo pedido={pedido} />
      </Bloco>
    </>
  );
}

/* ---------------------------------------------------------------- itens */

/**
 * O produto vem embutido em cada item (`OrderItemResponse.product`), então não há busca a
 * fazer aqui. O preço mostrado continua sendo o congelado no pedido, nunca o do catálogo —
 * é isso que mantém o histórico certo depois de o preço mudar.
 */
function Itens({ itens }: { itens: OrderItem[] }) {
  if (!itens.length) {
    return <p style={{ margin: 0, fontSize: 14, color: 'var(--ink3)' }}>Sem itens.</p>;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {itens.map((item, i) => (
        <div
          key={item.id}
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'baseline',
            gap: 20,
            flexWrap: 'wrap',
            padding: '12px 0',
            borderBottom: i === itens.length - 1 ? 'none' : '1px solid var(--line)',
          }}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 3, flex: '1 1 220px' }}>
            <Link
              href={`/produtos/${item.product.id}`}
              style={{ fontSize: 15, color: 'var(--ink)', minHeight: 22 }}
            >
              {item.product.name}
            </Link>
            <span className="mono" style={{ fontSize: 12, color: 'var(--ink3)' }}>
              {item.quantity} × {brl(item.unitPrice)}
            </span>
          </div>
          <Money value={item.subtotal} size={15} />
        </div>
      ))}
    </div>
  );
}
