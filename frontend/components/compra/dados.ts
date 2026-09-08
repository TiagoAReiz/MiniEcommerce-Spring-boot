'use client';

/**
 * Leitura e escrita do carrinho, num lugar só.
 *
 * As telas de carrinho e de checkout precisam exatamente das mesmas coisas — linhas com
 * produto resolvido, divergência de preço, falta de estoque — e duplicar isso significaria
 * duas respostas diferentes para "o preço mudou?".
 */

import { useCallback, useEffect, useState } from 'react';
import type { RequestOptions } from '@/lib/api';
import { ApiError } from '@/lib/api';
import type { Cart, CartLine, Product } from '@/lib/types';

/** A assinatura do que `useApi()` devolve. */
export type Chamada = <T>(path: string, options?: RequestOptions) => Promise<T>;

/**
 * `GET /cart` já devolve o produto inteiro em cada linha, então não há o que hidratar. O
 * `product` continua opcional aqui só para a linha sobreviver ao dia em que o produto sumir
 * do catálogo — sem ela na tela, o usuário não teria como removê-la do carrinho.
 */
type LinhaBruta = Omit<CartLine, 'product'> & { product?: Product | null };

export interface LinhaDetalhada {
  productId: string;
  quantity: number;
  /** Preço congelado quando o item entrou no carrinho. */
  unitPrice: number;
  /** Null quando o produto saiu do catálogo entre adicionar e olhar o carrinho. */
  product: Product | null;
}

export interface CarrinhoDetalhado {
  linhas: LinhaDetalhada[];
  /** Mercadoria pelos preços congelados. Frete só existe depois do `POST /orders`. */
  subtotal: number;
  itens: number;
}

/** Uma linha cujo preço atual difere do congelado — a causa do 409 `PRICE_CHANGED`. */
export interface Divergencia {
  linha: LinhaDetalhada;
  product: Product;
  /** Positiva quando encareceu. */
  diferenca: number;
}

export const VAZIO: CarrinhoDetalhado = { linhas: [], subtotal: 0, itens: 0 };

/* ------------------------------------------------------------------ leitura */

export async function carregarCarrinho(call: Chamada): Promise<CarrinhoDetalhado> {
  const cart = await call<Cart>('/cart');
  const brutas = (cart?.lines ?? []) as LinhaBruta[];

  // Em paralelo: cada produto é uma rota independente e cacheada por 10 min no Redis.
  const linhas = await Promise.all(
    brutas.map(async (l): Promise<LinhaDetalhada> => {
      return {
        productId: l.product?.id ?? '',
        quantity: l.quantity,
        unitPrice: l.unitPrice,
        product: l.product ?? null,
      };
    }),
  );

  return {
    linhas,
    subtotal: linhas.reduce((s, l) => s + l.unitPrice * l.quantity, 0),
    itens: linhas.reduce((s, l) => s + l.quantity, 0),
  };
}

/** O que faria o checkout recusar com `PRICE_CHANGED`, calculado antes de tentar. */
export function divergencias(c: CarrinhoDetalhado): Divergencia[] {
  return c.linhas.flatMap((linha) =>
    linha.product && linha.product.price !== linha.unitPrice
      ? [{ linha, product: linha.product, diferenca: linha.product.price - linha.unitPrice }]
      : [],
  );
}

/** O que faria o checkout recusar com `INSUFFICIENT_STOCK`. */
export function semEstoque(c: CarrinhoDetalhado): LinhaDetalhada[] {
  return c.linhas.filter((l) => l.product && l.product.stock < l.quantity);
}

/** Produto desativado ou zerado: `sellable` já resume as duas condições. */
export function indisponiveis(c: CarrinhoDetalhado): LinhaDetalhada[] {
  return c.linhas.filter((l) => !l.product || !l.product.sellable);
}

export const fotoCapa = (p: Product | null) =>
  p ? (p.photos.find((f) => f.isCover) ?? p.photos[0])?.url ?? null : null;

/* ------------------------------------------------------------------ escrita */

export async function mudarQuantidade(call: Chamada, productId: string, quantity: number) {
  if (quantity <= 0) return removerLinha(call, productId);
  await call(`/cart/items/${productId}`, { method: 'PATCH', body: { quantity } });
}

export async function removerLinha(call: Chamada, productId: string) {
  await call(`/cart/items/${productId}`, { method: 'DELETE' });
}

export async function esvaziar(call: Chamada) {
  await call('/cart', { method: 'DELETE' });
}

/**
 * Recongela a linha pelo preço atual — o "aceitar o novo preço" da tela de PRICE_CHANGED.
 *
 * O `unitPrice` nunca vem do cliente: quem lê o preço é o servidor. Então tentamos o PATCH
 * (uma requisição, preserva a linha) e **conferimos** se ele recongelou. Se não recongelou,
 * refazemos a linha com DELETE + POST, que o contrato garante ler o preço de `products`.
 * A ordem importa: o caminho destrutivo é o segundo, não o primeiro.
 */
export async function aceitarPrecoNovo(
  call: Chamada,
  productId: string,
  quantity: number,
): Promise<CarrinhoDetalhado> {
  await mudarQuantidade(call, productId, quantity);

  let carrinho = await carregarCarrinho(call);
  const ainda = carrinho.linhas.find(
    (l) => l.productId === productId && l.product && l.product.price !== l.unitPrice,
  );
  if (!ainda) return carrinho;

  await removerLinha(call, productId);
  try {
    await call('/cart/items', { method: 'POST', body: { productId, quantity } });
  } finally {
    // Mesmo se o POST falhar (estoque acabou nesse meio-tempo), a tela precisa do
    // carrinho de verdade — e não do estado anterior, que já não existe mais.
    carrinho = await carregarCarrinho(call);
  }
  return carrinho;
}

/* ------------------------------------------------------------------ hook */

export function useCarrinho(call: Chamada, ativo: boolean) {
  const [carrinho, setCarrinho] = useState<CarrinhoDetalhado | null>(null);
  const [carregandoInterno, setCarregando] = useState(true);
  const [erro, setErro] = useState<ApiError | null>(null);

  const recarregar = useCallback(async () => {
    setCarregando(true);
    try {
      setCarrinho(await carregarCarrinho(call));
      setErro(null);
    } catch (e) {
      // Carrinho expirado no Redis é 200 com lista vazia, não erro. Aqui é falha de rede
      // ou sessão morta — as duas merecem aviso, não uma tela de carrinho vazio mentindo.
      setErro(e instanceof ApiError ? e : new ApiError(0, null));
    } finally {
      setCarregando(false);
    }
  }, [call]);

  // Inativo não carrega nada, então "não está carregando" é derivado e não escrito: mandar
  // um efeito zerar a bandeira renderiza uma vez com ela ligada antes de corrigir.
  const carregando = ativo ? carregandoInterno : false;

  useEffect(() => {
    if (!ativo) return;
    let vivo = true;

    // Nada de ligar a bandeira aqui: ela já nasce ligada, e escrevê-la de forma síncrona no
    // efeito custaria um render em cascata a cada montagem.
    (async () => {
      try {
        const c = await carregarCarrinho(call);
        if (vivo) setCarrinho(c);
      } catch (e) {
        if (vivo) setErro(e instanceof ApiError ? e : new ApiError(0, null));
      } finally {
        if (vivo) setCarregando(false);
      }
    })();

    return () => {
      vivo = false;
    };
  }, [call, ativo]);

  return { carrinho, setCarrinho, carregando, erro, recarregar };
}
