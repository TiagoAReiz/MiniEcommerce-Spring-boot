'use client';

/**
 * Visão geral.
 *
 * O aviso de CEP ausente vem primeiro, e não é decoração: sem origem, `POST /orders` responde
 * 409 e **nenhum outro sintoma aparece** no painel. O catálogo abre, o estoque continua lá, a
 * lista de pedidos simplesmente para de crescer. Sem este banner, o dono descobre pelo cliente.
 *
 * O resto é resumo do que `GET /orders` devolve, e só. Nada aqui é calculado a partir de coisa
 * que o backend não entrega: não existe rota de faturamento, de ticket médio nem de conversão,
 * então esses números não aparecem inventados a partir de uma página de pedidos.
 */

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { ApiError } from '@/lib/api';
import { useApi } from '@/lib/auth';
import { brl } from '@/lib/format';
import type { OrderStatus, Page } from '@/lib/types';
import { Card, Spinner } from '@/components/ui';
import { OriginBanner, useOriginZip } from '@/components/admin/origin';
import { isAbandoned } from '@/components/admin/contract';
import { ORDERS_SCOPE_NOTE, type AdminOrder } from '@/components/admin/orders';
import { ErrorNote, PageHead, Section, Stat } from '@/components/admin/primitives';

/** Uma página grande: o resumo é do que couber nela, e o texto diz exatamente isso. */
const AMOSTRA = 100;

interface Resumo {
  total: number;
  visiveis: number;
  porStatus: Record<OrderStatus, number>;
  abandonados: number;
  receita: number;
}

/** Pedido pago é pedido que virou dinheiro; cancelado e pendente não entram na soma. */
const FATURADO: OrderStatus[] = ['PAID', 'SHIPPED', 'DELIVERED'];

function resumir(pagina: Page<AdminOrder>): Resumo {
  const porStatus: Record<OrderStatus, number> = {
    PENDING: 0, PAID: 0, SHIPPED: 0, DELIVERED: 0, CANCELLED: 0,
  };
  let abandonados = 0;
  let receita = 0;

  for (const pedido of pagina.content) {
    porStatus[pedido.status] += 1;
    if (isAbandoned(pedido.status, pedido.expiresAt)) abandonados += 1;
    if (FATURADO.includes(pedido.status)) receita += pedido.total;
  }

  return {
    total: pagina.totalElements,
    visiveis: pagina.content.length,
    porStatus,
    abandonados,
    receita,
  };
}

export default function VisaoGeralPage() {
  const call = useApi();
  const cepOrigem = useOriginZip();
  const [resumo, setResumo] = useState<Resumo | null>(null);
  const [erro, setErro] = useState<unknown>(null);

  useEffect(() => {
    let vivo = true;
    call<Page<AdminOrder>>(`/orders/all?page=0&size=${AMOSTRA}`)
      .then((pagina) => { if (vivo) setResumo(resumir(pagina)); })
      .catch((e: unknown) => { if (vivo) setErro(e); });
    return () => { vivo = false; };
  }, [call]);

  return (
    <>
      <OriginBanner />

      <PageHead
        title="Visão geral"
        hint="O estado da loja e o que os pedidos visíveis por esta conta mostram."
      />

      <div style={{ display: 'flex', flexDirection: 'column', gap: 32 }}>
        <Section
          title="A loja está vendendo?"
          hint="É a única pergunta do painel cuja resposta errada não produz erro em lugar nenhum."
        >
          <Card style={{ padding: '18px 20px', display: 'flex', alignItems: 'center', gap: 18, flexWrap: 'wrap' }}>
            <span
              aria-hidden
              style={{
                width: 10, height: 10, borderRadius: '50%', flexShrink: 0,
                background: cepOrigem ? 'var(--ok)' : 'var(--danger)',
              }}
            />
            <div style={{ flexGrow: 1, minWidth: 220 }}>
              <p style={{ margin: 0, fontSize: 16, fontWeight: 600, color: cepOrigem ? 'var(--ok)' : 'var(--danger)' }}>
                {cepOrigem ? 'Vendendo' : 'Parada'}
              </p>
              <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--ink2)', lineHeight: 1.6 }}>
                {cepOrigem ? (
                  <>
                    Frete calculado a partir do CEP <span className="mono">{cepOrigem}</span>. Mudar
                    a origem reprecifica só os pedidos futuros — cotação já aceita nunca é recalculada.
                  </>
                ) : (
                  'Sem CEP de origem, todo checkout é recusado com 409 e só o cliente vê o erro.'
                )}
              </p>
            </div>
            <Link href="/admin/config" style={{ fontSize: 14, color: 'var(--ink2)' }}>
              {cepOrigem ? 'Mudar o CEP' : 'Definir o CEP'} →
            </Link>
          </Card>
        </Section>

        <Section
          title="Pedidos"
          hint={ORDERS_SCOPE_NOTE}
        >
          <ErrorNote error={erro} />

          {!resumo && !erro ? (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '32px 0' }}>
              <Spinner size={22} />
            </div>
          ) : null}

          {resumo ? (
            <>
              <div
                style={{
                  display: 'grid', gap: 12,
                  gridTemplateColumns: 'repeat(auto-fit, minmax(168px, 1fr))',
                }}
              >
                <Stat
                  label="Pedidos visíveis"
                  value={String(resumo.total)}
                  hint={
                    resumo.total > resumo.visiveis
                      ? `resumo sobre os ${resumo.visiveis} mais recentes`
                      : undefined
                  }
                />
                <Stat
                  label="Aguardando pagamento"
                  value={String(resumo.porStatus.PENDING)}
                  tone={resumo.porStatus.PENDING > 0 ? 'accent' : 'neutral'}
                  hint="com reserva de estoque correndo"
                />
                <Stat
                  label="Pagos, a despachar"
                  value={String(resumo.porStatus.PAID)}
                  tone={resumo.porStatus.PAID > 0 ? 'ok' : 'neutral'}
                  hint="esperando envio criado"
                />
                <Stat label="A caminho" value={String(resumo.porStatus.SHIPPED)} />
                <Stat label="Entregues" value={String(resumo.porStatus.DELIVERED)} />
                <Stat
                  label="Abandonados"
                  value={String(resumo.abandonados)}
                  tone={resumo.abandonados > 0 ? 'danger' : 'neutral'}
                  hint="reserva vencida, não cancelado à mão"
                />
                <Stat
                  label="Somado nos pagos"
                  value={brl(resumo.receita)}
                  hint="pago, enviado e entregue · com frete"
                />
              </div>

              <p style={{ margin: 0, fontSize: 12, color: 'var(--ink3)', lineHeight: 1.7 }}>
                Os números saem dos <span className="mono">{resumo.visiveis}</span> pedidos desta
                página, somando <span className="mono">total</span> (mercadoria + frete), que é o
                valor congelado no checkout. Não há rota de faturamento no backend: isto é uma
                soma do que está na tela, não contabilidade.{' '}
                <Link href="/admin/pedidos">Ver a lista</Link>.
              </p>
            </>
          ) : null}

          {erro instanceof ApiError && erro.status === 404 ? (
            <p style={{ margin: 0, fontSize: 13, color: 'var(--ink3)' }}>
              A rota de pedidos não respondeu como esperado. Confira a versão do backend.
            </p>
          ) : null}
        </Section>
      </div>
    </>
  );
}
