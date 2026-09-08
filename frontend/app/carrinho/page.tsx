'use client';

/**
 * Carrinho.
 *
 * Client Component sem alternativa: `/cart` mora no Redis atrelado ao usuário e a API só
 * autentica por `Authorization: Bearer`, token que existe no navegador e não no servidor
 * de render.
 *
 * A tela não decide venda nenhuma — quem revalida preço e estoque é o `POST /orders`. O que
 * ela faz é mostrar, antes de o cliente chegar lá, tudo que faria o checkout recusar.
 */

import { useState } from 'react';
import Link from 'next/link';
import { ApiError } from '@/lib/api';
import { ERROR_CODES } from '@/lib/errors';
import { brl } from '@/lib/format';
import { useApi } from '@/lib/auth';
import { StoreHeader } from '@/components/chrome';
import { Banner, Button, Card, Empty, Money, Spinner } from '@/components/ui';
import {
  carregarCarrinho, esvaziar, indisponiveis, mudarQuantidade, removerLinha, semEstoque,
  useCarrinho,
} from '@/components/compra/dados';
import { LinhaCarrinho, Rodape, Secao, VoltarAoCatalogo } from '@/components/compra/pecas';
import { ParedeDeEntrada } from '@/components/compra/entrar';

export default function CarrinhoPage() {
  return (
    <>
      <StoreHeader />
      <main className="container" style={{ padding: '36px 20px 64px' }}>
        <ParedeDeEntrada motivo="O carrinho fica guardado na sua conta — entre para vê-lo e fechar o pedido.">
          <Conteudo />
        </ParedeDeEntrada>
      </main>
    </>
  );
}

function Conteudo() {
  const call = useApi();
  const { carrinho, setCarrinho, carregando, erro, recarregar } = useCarrinho(call, true);

  /** productId em operação, ou `'tudo'` quando o carrinho inteiro está sendo esvaziado. */
  const [ocupado, setOcupado] = useState<string | null>(null);
  const [falha, setFalha] = useState<string | null>(null);

  /**
   * Toda escrita é seguida de releitura: quantidade é intenção, e o estoque pode ter mudado
   * entre o clique e a resposta. Aplicar o número da tela sem reler seria fingir que sabemos.
   */
  const executar = async (chave: string, acao: () => Promise<void>) => {
    setOcupado(chave);
    setFalha(null);
    try {
      await acao();
    } catch (e) {
      const api = e instanceof ApiError ? e : null;
      setFalha(
        api?.is(ERROR_CODES.INSUFFICIENT_STOCK)
          ? 'O estoque não cobre essa quantidade. Ajustamos o carrinho ao que existe.'
          : 'Não conseguimos atualizar o carrinho agora. Tente de novo.',
      );
    }
    try {
      setCarrinho(await carregarCarrinho(call));
    } catch {
      /* a mensagem acima já explica; recarregar de novo é problema do botão */
    }
    setOcupado(null);
  };

  if (carregando && !carrinho) {
    return (
      <div style={{ padding: '80px 0', display: 'flex', justifyContent: 'center' }}>
        <Spinner size={22} />
      </div>
    );
  }

  if (erro && !carrinho) {
    return (
      <Banner
        tone="danger"
        title="Não conseguimos abrir seu carrinho"
        action={<Button variant="ghost" onClick={recarregar}>Tentar de novo</Button>}
      >
        A conexão com a loja falhou. Nada foi perdido — os itens continuam guardados na sua conta.
      </Banner>
    );
  }

  if (!carrinho || !carrinho.linhas.length) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'center' }}>
        <Empty
          title="Seu carrinho está vazio"
          hint="Os itens ficam guardados por 7 dias depois de adicionados."
        />
        <VoltarAoCatalogo />
      </div>
    );
  }

  const faltando = semEstoque(carrinho);
  const fora = indisponiveis(carrinho);
  // Um produto zerado aparece nas duas listas; o cliente só precisa ver a linha uma vez.
  const bloqueios = [...fora, ...faltando.filter((l) => !fora.includes(l))];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>
      <h1 style={{ margin: 0, fontSize: 28, fontWeight: 700, letterSpacing: '-.02em' }}>
        Seu carrinho
      </h1>

      {falha ? (
        <p role="alert" style={{ margin: 0, fontSize: 14, color: 'var(--danger)' }}>{falha}</p>
      ) : null}

      {bloqueios.length ? (
        <Banner tone="danger" title="Alguns itens não podem ir para o pedido">
          <p style={{ margin: '0 0 6px' }}>
            Ajuste a quantidade ou remova estes itens antes de fechar:
          </p>
          <ul style={{ margin: 0, paddingLeft: 18 }}>
            {bloqueios.map((l) => (
              <li key={l.productId}>
                <strong>{l.product?.name ?? 'Produto que saiu do catálogo'}</strong>
                {l.product && l.product.stock > 0 && l.product.stock < l.quantity ? (
                  <>
                    {' '}— você pediu <span className="mono">{l.quantity}</span> e restam{' '}
                    <span className="mono">{l.product.stock}</span>.
                  </>
                ) : (
                  ' — indisponível no momento.'
                )}
              </li>
            ))}
          </ul>
        </Banner>
      ) : null}

      <Secao
        titulo={`${carrinho.itens} ${carrinho.itens === 1 ? 'item' : 'itens'}`}
        acao={
          <Button
            variant="ghost"
            disabled={ocupado !== null}
            onClick={() => executar('tudo', () => esvaziar(call))}
          >
            Esvaziar carrinho
          </Button>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {carrinho.linhas.map((linha) => (
            <LinhaCarrinho
              key={linha.productId}
              linha={linha}
              ocupado={ocupado !== null}
              onQuantidade={(q) =>
                executar(linha.productId, () => mudarQuantidade(call, linha.productId, q))
              }
              onRemover={() =>
                executar(linha.productId, () => removerLinha(call, linha.productId))
              }
            />
          ))}
        </div>
      </Secao>

      <Card style={{ padding: 22, display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 16 }}>
          <span style={{ fontSize: 15, color: 'var(--ink2)' }}>Mercadoria</span>
          <Money value={carrinho.subtotal} size={20} />
        </div>

        {/* O frete só existe depois do `POST /orders`: é ele que cota a distância até o CEP. */}
        <p style={{ margin: 0, fontSize: 13, color: 'var(--ink3)', lineHeight: 1.6 }}>
          O frete é calculado no checkout, pela distância até o endereço que você escolher.
          Até lá, <span className="mono">{brl(carrinho.subtotal)}</span> é só a mercadoria.
        </p>

        <Rodape>
          {bloqueios.length ? (
            <Button disabled>Fechar pedido</Button>
          ) : (
            <Link href="/checkout">
              <Button>Fechar pedido</Button>
            </Link>
          )}
          <VoltarAoCatalogo />
          {ocupado !== null ? <Spinner size={16} /> : null}
        </Rodape>
      </Card>
    </div>
  );
}
