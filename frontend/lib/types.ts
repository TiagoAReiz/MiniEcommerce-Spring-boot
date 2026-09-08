/**
 * Os DTOs que o backend Spring devolve, transcritos.
 *
 * Fonte de verdade: `docs/api-contracts.md` na raiz do repositório. Quando divergir, o
 * contrato ganha — isto aqui é uma cópia, não a definição.
 */

export type OrderStatus = 'PENDING' | 'PAID' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProductHighlight {
  /** O número grande do card: "144", "2 TB", "1 ms". */
  value: string;
  /** A unidade ao lado, tipografada menor: "Hz", "capacidade". */
  unit: string | null;
}

export interface ProductSpec {
  label: string;
  value: string;
}

export interface ProductPhoto {
  id: string;
  url: string;
  position: number;
  isCover: boolean;
}

export interface Product {
  id: string;
  name: string;
  description: string | null;
  price: number;
  stock: number;
  active: boolean;
  /** `active && stock > 0`. É o que decide se o produto pode ir ao carrinho. */
  sellable: boolean;
  category: string | null;
  /** Sempre array — o backend nunca devolve null aqui (V6). */
  highlights: ProductHighlight[];
  specs: ProductSpec[];
  photos: ProductPhoto[];
  createdAt: string;
}

export interface CartLine {
  /**
   * O produto vem inteiro, como em `OrderItem` — `CartLineResponse.product` é um
   * `ProductResponse`. Ler `productId` daqui devolve `undefined`, e a requisição seguinte
   * vira `/cart/items/undefined`.
   */
  product: Product;
  quantity: number;
  /** Congelado quando o item entrou no carrinho; é contra ele que o checkout compara. */
  unitPrice: number;
  subtotal: number;
}

export interface Cart {
  lines: CartLine[];
  total: number;
  itemCount: number;
}

export interface OrderItem {
  id: string;
  /**
   * O produto vem inteiro, não como id — `OrderItemResponse.product` é um `ProductResponse`.
   * Escrever `item.productId` compila contra um tipo errado e lê `undefined` na tela.
   */
  product: Product;
  quantity: number;
  unitPrice: number;
  /** Congelado na compra. Nunca releia o preço do catálogo para exibir histórico. */
  subtotal: number;
}

export interface Order {
  id: string;
  status: OrderStatus;
  /** Mercadoria + frete. É exatamente o que o Mercado Pago cobra. */
  total: number;
  /** Só a mercadoria. Existe para o front não ter que subtrair nada. */
  itemsTotal: number;
  shippingCost: number;
  /** Null quando o frete não foi medido: valeu a tarifa fixa de contingência. */
  shippingDistanceKm: number | null;
  itemCount: number;
  addressId: string | null;
  paymentId: string | null;
  shipmentId: string | null;
  items: OrderItem[];
  createdAt: string;
  /** Fim da reserva de estoque. 30 min no checkout, 24 h quando a cobrança abre. */
  expiresAt: string | null;
}

export interface Address {
  id: string;
  zipCode: string;
  street: string;
  streetNumber: string | null;
  neighborhood: string;
  city: string;
  state: string;
  country: string;
  isPrimary: boolean;
  createdAt: string;
}

export interface User {
  id: string;
  name: string;
  email: string;
  cpf: string | null;
  phone: string | null;
  photoUrl: string | null;
  /** Falso enquanto faltar CPF ou telefone — e sem isso o checkout recusa. */
  canCheckout: boolean;
  createdAt: string;
}

export interface Payment {
  id: string;
  amount: number;
  paid: boolean;
  checkoutUrl?: string;
}

export interface Review {
  id: string;
  orderItemId: string;
  productId: string;
  rating: number;
  comment: string | null;
  createdAt: string;
}

export interface Shipment {
  id: string;
  orderId: string;
  trackingCode: string | null;
  carrier: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
}

export interface ShippingOrigin {
  /** Null enquanto a loja não configurou origem — e, enquanto for null, não vende. */
  zipCode: string | null;
}
