/**
 * Os `code` estáveis do contrato, e o que a interface deve fazer com cada um.
 *
 * Reagir ao `code` e não ao `detail`: o segundo é texto para humano e muda sem aviso.
 */

export interface ApiErrorBody {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  errors?: { field: string; message: string }[];
}

export const ERROR_CODES = {
  /** Falta CPF ou telefone. Levar ao perfil ANTES de tentar o checkout. */
  CHECKOUT_BLOCKED: 'CHECKOUT_BLOCKED',
  /** Carrinho vazio ou expirado (TTL de 7 dias no Redis). */
  EMPTY_CART: 'EMPTY_CART',
  /** O estoque acabou entre adicionar ao carrinho e fechar. */
  INSUFFICIENT_STOCK: 'INSUFFICIENT_STOCK',
  /** O preço mudou desde que o item entrou no carrinho. Exige confirmação explícita. */
  PRICE_CHANGED: 'PRICE_CHANGED',
  /** A loja não definiu o CEP de origem — e por isso não vende. Problema do dono. */
  SHIPPING_ORIGIN_NOT_CONFIGURED: 'SHIPPING_ORIGIN_NOT_CONFIGURED',
  ORDER_ALREADY_PAID: 'ORDER_ALREADY_PAID',
  CPF_ALREADY_USED: 'CPF_ALREADY_USED',
  VALIDATION_ERROR: 'VALIDATION_ERROR',
} as const;

/** Mensagens de fallback, para quando a tela não tem tratamento dedicado. */
export const ERROR_MESSAGES: Record<string, string> = {
  [ERROR_CODES.CHECKOUT_BLOCKED]: 'Complete seu CPF e telefone antes de fechar o pedido.',
  [ERROR_CODES.EMPTY_CART]: 'Seu carrinho está vazio ou expirou.',
  [ERROR_CODES.INSUFFICIENT_STOCK]: 'O estoque acabou enquanto você comprava.',
  [ERROR_CODES.PRICE_CHANGED]: 'O preço mudou desde que o item entrou no carrinho.',
  [ERROR_CODES.SHIPPING_ORIGIN_NOT_CONFIGURED]:
    'A loja ainda não está aceitando pedidos.',
  [ERROR_CODES.ORDER_ALREADY_PAID]: 'Este pedido já foi pago.',
  [ERROR_CODES.CPF_ALREADY_USED]: 'Este CPF já está em uso por outra conta.',
};
