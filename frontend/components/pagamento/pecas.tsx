'use client';

/**
 * As peças que pagamento e pedidos repetem.
 *
 * Ficam aqui porque as duas áreas são minhas e mostram os mesmos números; não é fundação, é
 * o vocabulário destas telas. Nada de hex literal — só `var(--…)`.
 */

import Link from 'next/link';
import { Card, Countdown, FreightLine, Money, Tag } from '@/components/ui';
import { StoreHeader } from '@/components/chrome';
import { dateTime, secondsUntil } from '@/lib/format';
import type { Order, OrderStatus } from '@/lib/types';
import { STATUS_LABEL, STATUS_TONE, idCurto } from './contrato';

/* ---------------------------------------------------------------- moldura */

export function Tela({ children, largura = 760 }: { children: React.ReactNode; largura?: number }) {
  return (
    <>
      <StoreHeader />
      <main className="container" style={{ padding: '32px 20px 64px' }}>
        <div style={{ maxWidth: largura, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 20 }}>
          {children}
        </div>
      </main>
    </>
  );
}

/* ---------------------------------------------------------------- avisos */

/**
 * O `Banner` da fundação só tem `accent` e `danger`, e a confirmação de pagamento precisa
 * de verde. Mesma anatomia, um tom a mais — quando `ok` subir para `components/ui.tsx`,
 * este componente some.
 */
export function Aviso({
  tone = 'accent', title, children, action,
}: {
  tone?: 'accent' | 'danger' | 'ok' | 'neutral';
  title: string;
  children?: React.ReactNode;
  action?: React.ReactNode;
}) {
  const fg = {
    accent: 'var(--accent)', danger: 'var(--danger)', ok: 'var(--ok)', neutral: 'var(--ink3)',
  }[tone];
  const bg = {
    accent: 'var(--accent-soft)', danger: 'var(--danger-soft)',
    ok: 'var(--surface2)', neutral: 'var(--surface2)',
  }[tone];

  return (
    <div
      style={{
        display: 'flex', gap: 18, alignItems: 'flex-start', flexWrap: 'wrap',
        background: bg, border: `1px solid ${fg}`, borderRadius: 'var(--radius)', padding: '18px 20px',
      }}
    >
      <div style={{ flexGrow: 1, minWidth: 220 }}>
        <p style={{ margin: 0, fontSize: 16, fontWeight: 600, color: fg }}>{title}</p>
        {children ? (
          <div style={{ marginTop: 6, fontSize: 14, lineHeight: 1.6, color: 'var(--ink2)' }}>
            {children}
          </div>
        ) : null}
      </div>
      {action}
    </div>
  );
}

/* ---------------------------------------------------------------- status */

export function StatusTag({ status }: { status: OrderStatus }) {
  return <Tag tone={STATUS_TONE[status]}>{STATUS_LABEL[status]}</Tag>;
}

/* ---------------------------------------------------------------- reserva */

/**
 * A reserva de estoque correndo.
 *
 * O prazo é 30 min no checkout e 24 h assim que a cobrança abre — o `<Countdown>` já troca
 * para horas sozinho. Mostrar isso não é enfeite: é o cliente saber quanto tempo o produto
 * continua sendo dele enquanto o webhook não chega.
 */
export function Reserva({
  expiresAt, nota, onFim,
}: { expiresAt: string | null; nota?: string; onFim?: () => void }) {
  const restante = secondsUntil(expiresAt);
  if (expiresAt === null || restante === null) return null;

  if (restante === 0) {
    return (
      <p style={{ margin: 0, fontSize: 13, color: 'var(--ink3)' }}>
        A reserva de estoque venceu em {dateTime(expiresAt)}.
      </p>
    );
  }

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <span
          className="mono"
          style={{ fontSize: 11, letterSpacing: '.08em', textTransform: 'uppercase', color: 'var(--ink3)' }}
        >
          Reserva de estoque
        </span>
        <Countdown until={expiresAt} onEnd={onFim} />
      </div>
      <p style={{ margin: 0, fontSize: 13, color: 'var(--ink2)', flex: '1 1 260px', lineHeight: 1.6 }}>
        {nota ?? 'Os itens continuam separados para você até este prazo.'}
      </p>
    </div>
  );
}

/* ---------------------------------------------------------------- valores */

/**
 * Mercadoria, frete e total em três linhas.
 *
 * Os três números vêm prontos: `itemsTotal` existe exatamente para o front não subtrair
 * frete de total. Somar qualquer coisa aqui é como esta tela e a cobrança passariam a
 * divergir por arredondamento — e `total` é o que o Mercado Pago cobrou.
 */
export function Resumo({ pedido }: { pedido: Order }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 16 }}>
        <span style={{ fontSize: 14, color: 'var(--ink2)' }}>
          Mercadoria
          <span style={{ color: 'var(--ink3)' }}> · {pedido.itemCount} {pedido.itemCount === 1 ? 'item' : 'itens'}</span>
        </span>
        <Money value={pedido.itemsTotal} size={15} />
      </div>

      <FreightLine cost={pedido.shippingCost} distanceKm={pedido.shippingDistanceKm} />

      <div style={{ height: 1, background: 'var(--line)' }} />

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 16 }}>
        <span style={{ fontSize: 15, fontWeight: 500 }}>Total</span>
        <Money value={pedido.total} size={22} />
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------- cabeçalho do pedido */

export function CabecalhoPedido({ pedido, titulo }: { pedido: Order; titulo?: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4, flexGrow: 1, minWidth: 200 }}>
        <h1 style={{ margin: 0, fontSize: 24, fontWeight: 600, letterSpacing: '-.01em' }}>
          {titulo ?? 'Pedido'} <span className="mono" style={{ fontWeight: 500 }}>{idCurto(pedido.id)}</span>
        </h1>
        <span className="mono" style={{ fontSize: 12, color: 'var(--ink3)' }}>
          {dateTime(pedido.createdAt)}
        </span>
      </div>
      <StatusTag status={pedido.status} />
    </div>
  );
}

/* ---------------------------------------------------------------- navegação */

export function VoltarPedidos() {
  return (
    <Link href="/pedidos" style={{ fontSize: 14, color: 'var(--ink3)' }}>
      ← Meus pedidos
    </Link>
  );
}

/** Caixa neutra com respiro padrão, para não repetir padding em toda tela. */
export function Bloco({
  children, titulo,
}: { children: React.ReactNode; titulo?: string }) {
  return (
    <Card style={{ padding: '20px 22px' }}>
      {titulo ? (
        <p
          className="mono"
          style={{
            margin: '0 0 14px', fontSize: 11, letterSpacing: '.08em',
            textTransform: 'uppercase', color: 'var(--ink3)',
          }}
        >
          {titulo}
        </p>
      ) : null}
      {children}
    </Card>
  );
}
