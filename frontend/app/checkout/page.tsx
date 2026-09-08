'use client';

/**
 * Checkout.
 *
 * A ordem dos passos é a mesma que o backend usa para recusar, e de propósito: perfil, depois
 * endereço, depois o pedido. Quem chega ao botão já passou pelas duas recusas baratas
 * (403 `CHECKOUT_BLOCKED` e 404 de endereço), e as caras — preço, estoque, frete — são as
 * únicas que ainda podem aparecer.
 *
 * O que esta tela nunca faz é somar. Mercadoria, frete e total vêm prontos do pedido criado
 * (`itemsTotal`, `shippingCost`, `total`), e `total` é exatamente o que o Mercado Pago cobra.
 */

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ApiError } from '@/lib/api';
import { ERROR_CODES } from '@/lib/errors';
import { brl } from '@/lib/format';
import { useApi, useAuth } from '@/lib/auth';
import type { Address, Order } from '@/lib/types';
import { StoreHeader } from '@/components/chrome';
import { Banner, Button, Card, Empty, FreightLine, Money, Spinner } from '@/components/ui';
import {
  carregarCarrinho, divergencias, semEstoque, useCarrinho,
  type LinhaDetalhada,
} from '@/components/compra/dados';
import { MiniItem, Secao, VoltarAoCatalogo } from '@/components/compra/pecas';
import { ParedeDeEntrada } from '@/components/compra/entrar';
import { PerfilForm } from '@/components/compra/perfil-form';
import { PrecoMudou } from '@/components/compra/preco-mudou';
import { ErroCheckout } from '@/components/compra/erros';
import { Enderecos, linhaDaCidade, linhaDaRua } from '@/components/compra/enderecos';

export default function CheckoutPage() {
  return (
    <>
      <StoreHeader />
      <main className="container" style={{ maxWidth: 780, padding: '36px 20px 64px' }}>
        <ParedeDeEntrada motivo="O pedido é fechado na sua conta — entre para continuar.">
          <Fluxo />
        </ParedeDeEntrada>
      </main>
    </>
  );
}

function Titulo({ children }: { children: React.ReactNode }) {
  return (
    <h1 style={{ margin: 0, fontSize: 28, fontWeight: 700, letterSpacing: '-.02em' }}>{children}</h1>
  );
}

function Fluxo() {
  const { user, refresh } = useAuth();
  const call = useApi();
  const router = useRouter();
  const { carrinho, setCarrinho, carregando, erro: erroCarrinho, recarregar } = useCarrinho(call, true);

  const [endereco, setEndereco] = useState<Address | null>(null);
  /** A tela do 409 `PRICE_CHANGED`. Sai dela só por escolha explícita item a item. */
  const [precos, setPrecos] = useState(false);
  const [erro, setErro] = useState<ApiError | null>(null);
  const [faltando, setFaltando] = useState<LinhaDetalhada[]>([]);
  const [enviando, setEnviando] = useState(false);
  const [pedido, setPedido] = useState<Order | null>(null);
  /** O backend disse `CHECKOUT_BLOCKED` mesmo com `canCheckout` verdadeiro na tela. */
  const [perfilPendente, setPerfilPendente] = useState(false);

  // O pedido criado já reservou estoque e tem prazo. Levar ao pagamento é a continuação
  // natural, e a rota de pagamento é quem sabe abrir a cobrança.
  useEffect(() => {
    if (pedido) router.push(`/pagamento/${pedido.id}`);
  }, [pedido, router]);

  const fechar = async () => {
    if (!endereco || !carrinho) return;

    // Divergência de preço é recusa certa. Perguntar antes evita gastar uma cotação de frete
    // para receber 409 e mostrar a mesma tela no fim.
    if (divergencias(carrinho).length) {
      setPrecos(true);
      return;
    }

    setEnviando(true);
    setErro(null);
    setFaltando([]);
    try {
      setPedido(await call<Order>('/orders', { method: 'POST', body: { addressId: endereco.id } }));
    } catch (e) {
      const api = e instanceof ApiError ? e : new ApiError(0, null);

      // As recusas de 409 falam do estado real do carrinho, não do que estava na tela: sem
      // reler, a lista de itens em falta seria a de antes da recusa.
      let atual = carrinho;
      try {
        atual = await carregarCarrinho(call);
        setCarrinho(atual);
      } catch {
        /* seguimos com o que temos; a mensagem abaixo não depende disso */
      }

      if (api.is(ERROR_CODES.PRICE_CHANGED)) {
        setPrecos(true);
      } else if (api.is(ERROR_CODES.CHECKOUT_BLOCKED)) {
        setPerfilPendente(true);
        await refresh();
      } else {
        if (api.is(ERROR_CODES.INSUFFICIENT_STOCK)) setFaltando(semEstoque(atual));
        setErro(api);
      }
    } finally {
      setEnviando(false);
    }
  };

  /* --------------------------------------------------------------- pedido criado */

  if (pedido) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
        <Titulo>Pedido criado</Titulo>
        <Card style={{ padding: 22, display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}>
            <span style={{ fontSize: 14, color: 'var(--ink2)' }}>Mercadoria</span>
            <span className="mono" style={{ fontSize: 15 }}>{brl(pedido.itemsTotal)}</span>
          </div>

          <FreightLine cost={pedido.shippingCost} distanceKm={pedido.shippingDistanceKm} />

          <div
            style={{
              display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 16,
              paddingTop: 14, borderTop: '1px solid var(--line)',
            }}
          >
            <span style={{ fontSize: 15, fontWeight: 600 }}>Total</span>
            <Money value={pedido.total} size={20} />
          </div>
        </Card>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 14, color: 'var(--ink3)' }}>
          <Spinner size={16} /> Abrindo o pagamento…
        </div>
        <p style={{ margin: 0, fontSize: 13, color: 'var(--ink3)' }}>
          Se nada acontecer, <Link href={`/pagamento/${pedido.id}`}>abra a cobrança por aqui</Link>.
        </p>
      </div>
    );
  }

  /* --------------------------------------------------------------- carrinho */

  if (carregando && !carrinho) {
    return (
      <div style={{ padding: '80px 0', display: 'flex', justifyContent: 'center' }}>
        <Spinner size={22} />
      </div>
    );
  }

  if (erroCarrinho && !carrinho) {
    return (
      <Banner
        tone="danger"
        title="Não conseguimos abrir seu carrinho"
        action={<Button variant="ghost" onClick={recarregar}>Tentar de novo</Button>}
      >
        Sem ele não há o que fechar. Seus itens continuam guardados na conta.
      </Banner>
    );
  }

  if (!carrinho || !carrinho.linhas.length) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'center' }}>
        <Empty
          title="Não há nada para fechar"
          hint="Seu carrinho está vazio ou ficou parado tempo demais e expirou."
        />
        <VoltarAoCatalogo />
      </div>
    );
  }

  /* --------------------------------------------------------------- passo 1: perfil */

  if (!user?.canCheckout || perfilPendente) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <span className="mono" style={{ fontSize: 11, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink3)' }}>
            Passo 1 de 2
          </span>
          <Titulo>Antes de fechar</Titulo>
        </div>
        <PerfilForm
          titulo="Falta o seu CPF e o seu telefone"
          descricao="O CPF vai na nota fiscal e o telefone serve para falarmos sobre a entrega. Sem os dois, o pedido não pode ser fechado."
          rotuloBotao="Salvar e continuar"
          onSalvo={() => setPerfilPendente(false)}
        />
        <p style={{ margin: 0, fontSize: 13, color: 'var(--ink3)' }}>
          Seu carrinho continua guardado. Nada foi cobrado.
        </p>
      </div>
    );
  }

  /* --------------------------------------------------------------- preço mudou */

  if (precos) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
        <Titulo>Confira os preços</Titulo>
        <PrecoMudou
          call={call}
          carrinho={carrinho}
          onAtualizado={setCarrinho}
          onContinuar={() => {
            setPrecos(false);
            setErro(null);
          }}
          rotuloContinuar="Voltar ao resumo do pedido"
        />
      </div>
    );
  }

  /* --------------------------------------------------------------- passo 2: fechar */

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 30 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <span className="mono" style={{ fontSize: 11, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink3)' }}>
          Passo 2 de 2
        </span>
        <Titulo>Fechar pedido</Titulo>
      </div>

      {erro ? (
        <ErroCheckout
          erro={erro}
          faltando={faltando}
          /* Sem "tentar de novo" quando falta estoque: repetir o mesmo pedido dá o mesmo 409, e
             quem resolve é o carrinho. Nas outras recusas insistir faz sentido — e no caso do
             frete o próprio `ErroCheckout` ignora a ação, porque a loja é que está fora do ar. */
          onTentarDeNovo={
            erro.is(ERROR_CODES.INSUFFICIENT_STOCK)
              ? undefined
              : () => {
                  setErro(null);
                  setFaltando([]);
                  void fechar();
                }
          }
        />
      ) : null}

      <Enderecos
        onEscolher={setEndereco}
        escolhidoId={endereco?.id ?? null}
        titulo="Endereço de entrega"
        descricao="O frete é cotado pela distância entre a loja e este CEP."
      />

      <Secao titulo={`Conferência · ${carrinho.itens} ${carrinho.itens === 1 ? 'item' : 'itens'}`}>
        <Card style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 14 }}>
          {carrinho.linhas.map((l) => <MiniItem key={l.productId} linha={l} />)}
        </Card>
      </Secao>

      <Card style={{ padding: 22, display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}>
          <span style={{ fontSize: 14, color: 'var(--ink2)' }}>Mercadoria</span>
          <span className="mono" style={{ fontSize: 15 }}>{brl(carrinho.subtotal)}</span>
        </div>

        {/* O frete só nasce no `POST /orders`: é ele que cota a distância até o CEP escolhido.
            Mostrar um número estimado aqui seria inventar o que ainda não foi medido. */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
            <span style={{ fontSize: 14, color: 'var(--ink2)' }}>Frete</span>
            <span className="mono" style={{ fontSize: 11, color: 'var(--ink3)' }}>
              cotado ao fechar
            </span>
          </div>
          <span className="mono" style={{ fontSize: 15, color: 'var(--ink3)' }}>—</span>
        </div>

        <div
          style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 16,
            paddingTop: 14, borderTop: '1px solid var(--line)',
          }}
        >
          <span style={{ fontSize: 15, fontWeight: 600 }}>Total</span>
          <span className="mono" style={{ fontSize: 14, color: 'var(--ink3)' }}>
            mercadoria + frete, na próxima tela
          </span>
        </div>

        {/* Não existe seletor de forma de pagamento: quem coleta isso é o Checkout Pro. */}
        <p style={{ margin: 0, fontSize: 13, color: 'var(--ink2)', lineHeight: 1.6 }}>
          A forma de pagamento — Pix, cartão ou boleto — você escolhe na tela do Mercado Pago,
          depois de confirmar. Nada é cobrado até lá.
        </p>

        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
          <Button onClick={fechar} disabled={!endereco || enviando}>
            {enviando ? <Spinner size={14} /> : null}
            {enviando ? 'Fechando…' : 'Fechar pedido'}
          </Button>
          <Link href="/carrinho">
            <Button variant="ghost">Voltar ao carrinho</Button>
          </Link>
        </div>

        {!endereco ? (
          <p style={{ margin: 0, fontSize: 13, color: 'var(--ink3)' }}>
            Escolha ou cadastre um endereço para continuar.
          </p>
        ) : (
          <p style={{ margin: 0, fontSize: 13, color: 'var(--ink3)' }}>
            Entrega em <strong>{linhaDaRua(endereco)}</strong> — {linhaDaCidade(endereco)}.
          </p>
        )}
      </Card>
    </div>
  );
}
