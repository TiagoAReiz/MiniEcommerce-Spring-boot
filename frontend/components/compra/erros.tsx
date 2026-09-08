'use client';

/**
 * O que cada recusa do checkout vira na tela.
 *
 * A interface reage ao `code`, nunca ao `detail` — `detail` é texto para humano e muda sem
 * aviso. E o texto que aparece é escrito daqui, não repassado do servidor: o cliente precisa
 * saber o que fazer, e o `detail` foi escrito para quem lê log.
 */

import Link from 'next/link';
import { ApiError } from '@/lib/api';
import { ERROR_CODES } from '@/lib/errors';
import { Banner, Button } from '@/components/ui';
import type { LinhaDetalhada } from './dados';

export function ErroCheckout({
  erro, faltando, onTentarDeNovo,
}: {
  erro: ApiError;
  /** Linhas cujo estoque não cobre a quantidade, recalculadas depois da recusa. */
  faltando?: LinhaDetalhada[];
  onTentarDeNovo?: () => void;
}) {
  /* A loja não configurou o CEP de origem. Não é culpa do cliente e não há o que ele faça —
     então nada de detalhe técnico, nada de botão que só vai falhar de novo. */
  if (erro.is(ERROR_CODES.SHIPPING_ORIGIN_NOT_CONFIGURED)) {
    return (
      <Banner tone="danger" title="A loja não está aceitando pedidos no momento">
        Não conseguimos calcular a entrega agora, então nenhum pedido pode ser fechado. Seu
        carrinho está guardado — tente de novo mais tarde.
      </Banner>
    );
  }

  if (erro.is(ERROR_CODES.EMPTY_CART)) {
    return (
      <Banner
        tone="danger"
        title="Seu carrinho está vazio"
        action={
          <Link href="/">
            <Button variant="ghost">Ver o catálogo</Button>
          </Link>
        }
      >
        Ou os itens foram retirados, ou o carrinho ficou parado tempo demais e expirou.
      </Banner>
    );
  }

  if (erro.is(ERROR_CODES.INSUFFICIENT_STOCK)) {
    return (
      <Banner
        tone="danger"
        title="O estoque acabou enquanto você comprava"
        action={
          onTentarDeNovo ? (
            <Button variant="ghost" onClick={onTentarDeNovo}>Tentar de novo</Button>
          ) : undefined
        }
      >
        {faltando && faltando.length ? (
          <>
            <p style={{ margin: '0 0 6px' }}>Ajuste a quantidade destes itens no carrinho:</p>
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              {faltando.map((l) => (
                <li key={l.productId}>
                  <strong>{l.product?.name ?? 'Produto'}</strong> — você pediu{' '}
                  <span className="mono">{l.quantity}</span> e restam{' '}
                  <span className="mono">{l.product?.stock ?? 0}</span>.
                </li>
              ))}
            </ul>
          </>
        ) : (
          'Um dos itens do carrinho não tem mais a quantidade que você pediu. Revise o carrinho.'
        )}
      </Banner>
    );
  }

  if (erro.status === 404) {
    return (
      <Banner tone="danger" title="Endereço não encontrado">
        O endereço escolhido não existe mais. Escolha outro ou cadastre um novo.
      </Banner>
    );
  }

  return (
    <Banner
      tone="danger"
      title="Não conseguimos fechar o pedido"
      action={
        onTentarDeNovo ? (
          <Button variant="ghost" onClick={onTentarDeNovo}>Tentar de novo</Button>
        ) : undefined
      }
    >
      Nada foi cobrado e seu carrinho continua guardado. Tente de novo em instantes.
    </Banner>
  );
}
