'use client';

/**
 * A tela para onde o cliente volta do Mercado Pago.
 *
 * A regra que ela existe para respeitar: **o pagamento é confirmado por webhook, nunca pelo
 * retorno do gateway**. O Mercado Pago devolve o cliente para cá em qualquer desfecho —
 * aprovado, recusado, pendente — e o nosso servidor pode não ter processado a notificação
 * ainda. Então esta tela mostra "processando" e pergunta ao backend, em vez de acreditar na
 * volta. Só `PAID` (ou adiante) confirma.
 */

import Link from 'next/link';
import { useCallback, useState } from 'react';
import { ApiError } from '@/lib/api';
import { useApi, useAuth } from '@/lib/auth';
import { ERROR_CODES } from '@/lib/errors';
import { Button, Spinner } from '@/components/ui';
import { Aviso, Bloco, CabecalhoPedido, Reserva, Resumo, Tela, VoltarPedidos } from './pecas';
import { POLL_MS, TETO_MS, usePedidoWatch } from './usePedidoWatch';
import type { CheckoutLink, Desfecho } from './contrato';
import { idCurto } from './contrato';

const SEGUNDOS = Math.round(POLL_MS / 1000);
const MINUTOS_TETO = Math.round(TETO_MS / 60000);

export function TelaPagamento({ orderId, desfecho }: { orderId: string; desfecho: Desfecho }) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <Tela>
        <div style={{ display: 'flex', justifyContent: 'center', padding: 56 }}>
          <Spinner size={24} />
        </div>
      </Tela>
    );
  }

  if (!user) {
    return (
      <Tela>
        <Aviso title="Entre para acompanhar o pagamento">
          Este pedido é seu, mas precisamos da sua sessão para consultá-lo.{' '}
          <Link href="/conta">Entrar</Link>.
        </Aviso>
      </Tela>
    );
  }

  return (
    <Tela>
      <Acompanhamento orderId={orderId} desfecho={desfecho} />
    </Tela>
  );
}

function Acompanhamento({ orderId, desfecho }: { orderId: string; desfecho: Desfecho }) {
  const call = useApi();
  const { pedido, pagamento, fase, erro, conferindo, conferirAgora, reiniciar } =
    usePedidoWatch(orderId);

  const [abrindo, setAbrindo] = useState(false);
  const [erroCobranca, setErroCobranca] = useState<string | null>(null);

  /**
   * Abre uma cobrança nova e manda o cliente para o gateway.
   *
   * Serve tanto para quem voltou com falha quanto para quem fechou a aba antes de pagar. Cada
   * chamada cria uma cobrança nova e estica a reserva para 24 h — o que não muda é que
   * ninguém aqui promete pagamento: quem volta cai de novo em "processando".
   */
  const tentarDeNovo = useCallback(async () => {
    setAbrindo(true);
    setErroCobranca(null);
    try {
      const link = await call<CheckoutLink>(`/orders/${orderId}/payments`, { method: 'POST' });
      // Sai do app de propósito: o checkout é do Mercado Pago, não nosso.
      window.location.href = link.checkoutUrl;
    } catch (e) {
      if (e instanceof ApiError && e.is(ERROR_CODES.ORDER_ALREADY_PAID)) {
        // O pedido saiu de PENDING enquanto o cliente lia a tela. Reler é a resposta certa.
        await conferirAgora();
      } else if (e instanceof ApiError && e.status === 502) {
        setErroCobranca('O Mercado Pago não respondeu. Tente de novo em instantes.');
      } else if (e instanceof ApiError && e.status === 404) {
        setErroCobranca('Não encontramos este pedido.');
      } else {
        setErroCobranca('Não conseguimos abrir a cobrança agora.');
      }
      setAbrindo(false);
    }
  }, [call, conferirAgora, orderId]);

  if (fase === 'lendo') {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 56 }}>
        <Spinner size={24} />
      </div>
    );
  }

  // Pedido de outra pessoa responde 404 igual a pedido inexistente. A tela repete o silêncio:
  // dizer "sem permissão" confirmaria que o pedido existe.
  if (fase === 'ausente' || !pedido) {
    return (
      <>
        <Aviso tone="neutral" title="Pedido não encontrado">
          O endereço pode estar errado, ou este pedido não é da sua conta.
        </Aviso>
        <VoltarPedidos />
      </>
    );
  }

  const pago = fase === 'confirmado';
  const canceladoComPagamento = fase === 'cancelado' && pagamento?.paid === true;

  return (
    <>
      <VoltarPedidos />
      <CabecalhoPedido pedido={pedido} />

      {/* ---- o desfecho que o gateway carimbou na volta, que não decide nada ---- */}
      {!pago && desfecho === 'falha' ? (
        <Aviso
          tone="danger"
          title="O pagamento não foi concluído"
          action={
            <Button onClick={tentarDeNovo} disabled={abrindo}>
              {abrindo ? <Spinner size={14} /> : null}
              Tentar de novo
            </Button>
          }
        >
          O Mercado Pago recusou ou você saiu antes de terminar. Nada foi cobrado, e os itens
          continuam reservados enquanto a contagem abaixo estiver correndo.
        </Aviso>
      ) : null}

      {!pago && desfecho === 'pendente' ? (
        <Aviso title="Pagamento em análise no Mercado Pago">
          Boleto e Pix levam alguns instantes — às vezes mais. Assim que a aprovação chegar ao
          nosso servidor, este pedido muda sozinho para pago.
        </Aviso>
      ) : null}

      {/* ---- o estado real, que vem do backend ---- */}
      {fase === 'processando' ? (
        <Bloco>
          <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
            <span style={{ marginTop: 3, animation: 'pulse 1.8s ease-in-out infinite' }}>
              <Spinner size={20} />
            </span>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, flexGrow: 1 }}>
              <p style={{ margin: 0, fontSize: 17, fontWeight: 600 }}>
                Estamos confirmando seu pagamento
              </p>
              <p style={{ margin: 0, fontSize: 14, lineHeight: 1.7, color: 'var(--ink2)' }}>
                A confirmação chega do Mercado Pago direto ao nosso servidor, e não pelo seu
                navegador — por isso a tela do gateway pode já ter dito &ldquo;aprovado&rdquo; enquanto o
                pedido ainda aparece como aguardando. Estamos perguntando a cada{' '}
                <span className="mono">{SEGUNDOS}s</span>.
              </p>
              <p className="mono" style={{ margin: 0, fontSize: 12, color: 'var(--ink3)' }}>
                pode fechar esta página · nada se perde
              </p>
            </div>
          </div>
        </Bloco>
      ) : null}

      {fase === 'parado' ? (
        <Aviso
          title="Ainda não recebemos a confirmação"
          action={
            <Button variant="ghost" onClick={conferirAgora} disabled={conferindo}>
              {conferindo ? <Spinner size={14} /> : null}
              Atualizar
            </Button>
          }
        >
          Paramos de perguntar depois de {MINUTOS_TETO} minutos para não deixar a tela girando
          à toa. Isso não significa que deu errado: se o pagamento foi aprovado, a
          reconciliação do servidor ainda o encontra dentro da janela abaixo, e o pedido muda
          sozinho. Atualize quando quiser, ou volte depois em Meus pedidos.
          {erro ? (
            <p style={{ margin: '8px 0 0', color: 'var(--danger)', fontSize: 13 }}>{erro}</p>
          ) : null}
        </Aviso>
      ) : null}

      {pago ? (
        <Aviso tone="ok" title="Pagamento confirmado">
          O Mercado Pago aprovou e o nosso servidor liquidou o pedido{' '}
          <span className="mono">{idCurto(pedido.id)}</span>. A partir daqui você acompanha o
          envio em <Link href={`/pedidos/${pedido.id}`}>Meus pedidos</Link>.
        </Aviso>
      ) : null}

      {canceladoComPagamento ? (
        <Aviso tone="danger" title="Pagamento aprovado, mas o pedido já tinha sido liberado">
          A reserva de estoque venceu antes de a aprovação chegar, e os itens voltaram para a
          prateleira. O valor foi cobrado: guarde o número{' '}
          <span className="mono">{idCurto(pedido.id)}</span> e fale com a loja para o estorno
          ou a reposição.
        </Aviso>
      ) : null}

      {fase === 'cancelado' && !canceladoComPagamento ? (
        <Aviso tone="neutral" title="Este pedido não está mais aberto para pagamento">
          {pedido.expiresAt
            ? 'A reserva de estoque venceu e os itens voltaram ao catálogo. Nada foi cobrado.'
            : 'O pedido foi cancelado. Nada foi cobrado.'}{' '}
          <Link href="/">Ver o catálogo</Link>.
        </Aviso>
      ) : null}

      {erroCobranca ? (
        <Aviso tone="danger" title="Não deu para abrir a cobrança">
          {erroCobranca}
        </Aviso>
      ) : null}

      {/* ---- a janela de 24 h que a cobrança aberta comprou ---- */}
      {fase === 'processando' || fase === 'parado' ? (
        <Bloco>
          <Reserva
            expiresAt={pedido.expiresAt}
            nota="Abrir a cobrança esticou a reserva de 30 minutos para 24 horas: é o prazo em que um pagamento aprovado ainda é recuperável, mesmo que a notificação se perca."
            onFim={conferirAgora}
          />
        </Bloco>
      ) : null}

      <Bloco titulo="Valores">
        <Resumo pedido={pedido} />
      </Bloco>

      {/* ---- ação, só enquanto o pedido de fato aceita pagamento ---- */}
      {pedido.status === 'PENDING' && desfecho !== 'falha' ? (
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
          <Button variant="ghost" onClick={tentarDeNovo} disabled={abrindo}>
            {abrindo ? <Spinner size={14} /> : null}
            Pagar de novo no Mercado Pago
          </Button>
          <span style={{ fontSize: 13, color: 'var(--ink3)', flex: '1 1 240px', lineHeight: 1.6 }}>
            Use se você não chegou a concluir o pagamento. Se já pagou, espere — cobrar duas
            vezes é pior que esperar.
          </span>
        </div>
      ) : null}

      {fase === 'confirmado' || fase === 'cancelado' ? (
        <div>
          <Button variant="ghost" onClick={reiniciar} style={{ display: 'none' }} />
        </div>
      ) : null}
    </>
  );
}
