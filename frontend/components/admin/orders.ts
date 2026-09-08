/**
 * A forma real do pedido que o backend devolve, onde ela discorda de `lib/types.ts`.
 *
 * `OrderItemResponse` traz o **produto inteiro** em `product`, não um `productId`. Quem
 * escrever `item.productId` compila contra o tipo da fundação e lê `undefined` na tela — o
 * mesmo tipo de armadilha que `AdminShipment` resolve em `contract.ts`.
 *
 * Fica em arquivo próprio, e não junto de `contract.ts`, só para não reescrever arquivo que
 * já estava pronto. Quando os dois subirem para `lib/types.ts`, os dois somem.
 */

import type { Order, Product } from '@/lib/types';

export interface AdminOrderItem {
  id: string;
  /** Null quando o produto sumiu do catálogo — o pedido continua legível sem ele. */
  product: Product | null;
  quantity: number;
  /** O preço congelado no checkout, não o preço de hoje. */
  unitPrice: number;
  subtotal: number;
}

export type AdminOrder = Omit<Order, 'items'> & { items: AdminOrderItem[] };

/**
 * O que `GET /orders` realmente lista.
 *
 * O controller chama `findByUserIdOrderByCreatedAtDesc(sub)`: a rota devolve os pedidos de
 * **quem chama**, e o `sub` de um token de dono é o id do dono — ela devolveria só o que o
 * dono comprou. A listagem da loja é `GET /orders/all`, fechada para OWNER.
 *
 * A busca por id continua, e não é redundante: achar um pedido específico numa loja com
 * muitas páginas é mais rápido pelo id que o cliente informa do que paginando.
 */
export const ORDERS_SCOPE_NOTE =
  'Todos os pedidos da loja, mais recentes primeiro. Use a busca por id para abrir '
  + 'direto um pedido que o cliente informou.';
