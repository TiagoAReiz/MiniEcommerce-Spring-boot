/**
 * O que o painel precisa e `lib/types.ts` ainda não descreve.
 *
 * Fica aqui, e não em `lib/`, porque a fundação é território de outro agente. Quando estes
 * tipos subirem para `lib/types.ts`, este arquivo some — nada aqui é decisão de design, é
 * transcrição do backend (`ShipmentResponse`, `ProductRequest`, `OrderStatus`).
 */

import type { OrderStatus, ProductHighlight, ProductSpec } from '@/lib/types';

/* ---------------------------------------------------------------- envio */

/**
 * A forma real de `ShipmentResponse` no backend.
 *
 * `lib/types.ts` descreve um envio com `trackingCode`, `carrier` e `deliveredAt`, que o
 * backend não tem, e não descreve `estimatedDeliveryAt` nem `shipped`, que ele tem. Usar o
 * tipo da fundação aqui daria código que compila e lê `undefined` em produção.
 */
export interface AdminShipment {
  id: string;
  addressId: string | null;
  estimatedDeliveryAt: string | null;
  shippedAt: string | null;
  shipped: boolean;
  createdAt: string;
}

/* ---------------------------------------------------------------- produto */

/** O corpo de `POST /products` e `PUT /products/{id}`. Os limites são os do backend. */
export interface ProductPayload {
  name: string;
  description: string | null;
  price: number;
  stock: number;
  category: string | null;
  /** No máximo 3 — o quarto vira 400, não é descartado em silêncio. */
  highlights: ProductHighlight[];
  /** No máximo 30, na ordem digitada. */
  specs: ProductSpec[];
  /** Só no `PUT`. Ausente deixa o valor atual como está. */
  active?: boolean;
}

export const MAX_HIGHLIGHTS = 3;
export const MAX_SPECS = 30;

/** Limites de tamanho declarados nas anotações do `ProductRequest`. */
export const LIMITS = {
  name: 255,
  category: 60,
  highlightValue: 24,
  highlightUnit: 24,
  specLabel: 60,
  specValue: 160,
} as const;

/* ---------------------------------------------------------------- pedido */

export const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING: 'Aguardando pagamento',
  PAID: 'Pago',
  SHIPPED: 'Enviado',
  DELIVERED: 'Entregue',
  CANCELLED: 'Cancelado',
};

export const STATUS_TONE: Record<OrderStatus, 'neutral' | 'ok' | 'accent' | 'danger'> = {
  PENDING: 'accent',
  PAID: 'ok',
  SHIPPED: 'ok',
  DELIVERED: 'neutral',
  CANCELLED: 'danger',
};

/**
 * As transições que o painel oferece — que são menos do que o enum aceita, de propósito.
 *
 * `PENDING → PAID` passa no `canTransitionTo`, mas é do webhook do Mercado Pago e de mais
 * ninguém: marcar pago na mão dá baixa num pedido que ninguém pagou, e a reconciliação
 * nunca mais reclama. Por isso não aparece aqui.
 *
 * `PAID → SHIPPED` também não aparece: a rota certa é `PATCH /shipments/{id}`, que carimba
 * `shippedAt` e move o pedido junto. Usar `PATCH /orders/{id}/status` deixaria um pedido
 * `SHIPPED` sem data de despacho — o `changeStatus` não exige envio, só o contrato exige.
 */
export const OFFERED_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  PENDING: ['CANCELLED'],
  PAID: ['CANCELLED'],
  SHIPPED: ['DELIVERED'],
  DELIVERED: [],
  CANCELLED: [],
};

/**
 * Pedido cancelado que ainda carrega `expiresAt` foi abandonado, não cancelado à mão.
 *
 * Toda saída deliberada de `PENDING` zera a coluna; só a varredura de reserva a preserva.
 * A data no passado é a evidência — e o que estava no carrinho continua nos `items`.
 */
export const isAbandoned = (status: OrderStatus, expiresAt: string | null) =>
  status === 'CANCELLED' && expiresAt !== null;
