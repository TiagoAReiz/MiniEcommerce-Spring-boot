/**
 * O que estas telas precisam do contrato e `lib/types.ts` ainda não cobre.
 *
 * Fica aqui, e não na fundação, porque a fundação é compartilhada por quatro frentes e
 * ninguém deve editá-la em paralelo. Se um dia isto subir para `lib/types.ts`, some com o
 * arquivo — nada aqui é decisão de tela, é transcrição de contrato.
 */

import type { OrderStatus } from '@/lib/types';

/**
 * A resposta de `POST /orders/{id}/payments`.
 *
 * Repare no que ela **não** tem: o id do nosso `payment`. Ela devolve `gatewayReference`, que
 * é o id da preferência no Mercado Pago, e isso não serve para `GET /payments/{id}`. Quem
 * quiser acompanhar a cobrança tem que reler o pedido — `order.paymentId` é gravado na mesma
 * transação que cria a linha, antes de o gateway ser chamado.
 */
export interface CheckoutLink {
  checkoutUrl: string;
  gatewayReference: string;
  amount: number;
}

/** Como o cliente voltou do Mercado Pago. Nenhum destes valores prova pagamento. */
export type Desfecho = 'sucesso' | 'falha' | 'pendente' | null;

type Query = Record<string, string | string[] | undefined>;

const primeiro = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v);

/**
 * Lê o desfecho que o Mercado Pago carimba na volta.
 *
 * Serve só para escolher o texto da tela. `sucesso` aqui significa "o cliente diz ter pago",
 * não "o pedido está pago" — quem responde isso é o webhook, e a tela espera por ele.
 */
export function lerDesfecho(query: Query): Desfecho {
  const bruto = (primeiro(query.status) ?? primeiro(query.collection_status) ?? '').toLowerCase();

  if (bruto === 'approved' || bruto === 'success') return 'sucesso';
  if (bruto === 'pending' || bruto === 'in_process' || bruto === 'in_mediation') return 'pendente';
  if (bruto === 'failure' || bruto === 'rejected' || bruto === 'cancelled' || bruto === 'null') {
    return 'falha';
  }
  return null;
}

/** O `external_reference` da preferência é o id do nosso `payment`. */
export const lerReferencia = (query: Query) =>
  primeiro(query.external_reference) ?? primeiro(query.preference_id) ?? null;

/* ---------------------------------------------------------------- status do pedido */

export const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING: 'aguardando',
  PAID: 'pago',
  SHIPPED: 'enviado',
  DELIVERED: 'entregue',
  CANCELLED: 'cancelado',
};

/** `ok` para o que deu certo, `accent` para o que ainda corre, `neutral` para o que morreu. */
export const STATUS_TONE: Record<OrderStatus, 'neutral' | 'ok' | 'accent'> = {
  PENDING: 'accent',
  PAID: 'ok',
  SHIPPED: 'ok',
  DELIVERED: 'ok',
  CANCELLED: 'neutral',
};

/** Os pedidos que o cliente ainda pode pagar. */
export const aindaPagavel = (status: OrderStatus) => status === 'PENDING';

/** O id inteiro não cabe em lugar nenhum e ninguém decora um UUID. Os 8 primeiros bastam. */
export const idCurto = (id: string) => id.replace(/-/g, '').slice(0, 8).toUpperCase();
