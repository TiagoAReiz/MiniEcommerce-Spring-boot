'use client';

/**
 * Pedidos.
 *
 * Duas coisas moldam esta tela, e as duas vêm do backend, não de gosto:
 *
 * 1. **O painel só oferece as transições que o backend aceita** (`OFFERED_TRANSITIONS`).
 *    `PENDING → PAID` é do webhook do Mercado Pago e de mais ninguém — marcar pago na mão dá
 *    baixa num pedido que ninguém pagou. `PAID → SHIPPED` sai pelo envio, em
 *    `PATCH /shipments/{id}`, que carimba `shippedAt` e move o pedido junto; fazer pelo
 *    `PATCH /orders/{id}/status` deixaria um pedido despachado sem data de despacho.
 *
 * 2. **Cancelado com `expiresAt` é abandono, não decisão.** Toda saída deliberada de `PENDING`
 *    zera a coluna; só a varredura de reserva a preserva. Os dois casos aparecem diferentes
 *    porque significam coisas diferentes: um é venda perdida, o outro é operação normal.
 */

import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '@/lib/api';
import { useApi } from '@/lib/auth';
import { brl, dateTime } from '@/lib/format';
import type { OrderStatus, Page } from '@/lib/types';
import { Button, Card, Empty, FreightLine, Input, Spinner, Tag } from '@/components/ui';
import { OriginBanner } from '@/components/admin/origin';
import {
  OFFERED_TRANSITIONS, STATUS_LABEL, STATUS_TONE, isAbandoned, type AdminShipment,
} from '@/components/admin/contract';
import { ORDERS_SCOPE_NOTE, type AdminOrder } from '@/components/admin/orders';
import { ErrorNote, OkNote, Pager, PageHead, Section } from '@/components/admin/primitives';

const TAMANHO = 20;

/** O verbo da transição, que é sempre mais claro que "mover para Cancelado". */
const ACAO: Record<OrderStatus, string> = {
  PENDING: 'Voltar para pendente',
  PAID: 'Marcar como pago',
  SHIPPED: 'Marcar como enviado',
  DELIVERED: 'Marcar como entregue',
  CANCELLED: 'Cancelar pedido',
};

/**
 * `<input type="date">` devolve "2026-09-05"; o backend espera um `OffsetDateTime`.
 * Meio-dia local, e não meia-noite, para o fuso não empurrar a data para o dia anterior.
 */
function comoInstante(dia: string): string | null {
  if (!dia) return null;
  const d = new Date(`${dia}T12:00:00`);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

/* ---------------------------------------------------------------- um pedido */

function CartaoPedido({
  pedido, aoAtualizar,
}: { pedido: AdminOrder; aoAtualizar: (p: AdminOrder) => void }) {
  const call = useApi();
  const [ocupado, setOcupado] = useState(false);
  const [erro, setErro] = useState<unknown>(null);
  const [ok, setOk] = useState<string | null>(null);
  const [envio, setEnvio] = useState<AdminShipment | null>(null);
  const [previsao, setPrevisao] = useState('');

  const abandonado = isAbandoned(pedido.status, pedido.expiresAt);
  const transicoes = OFFERED_TRANSITIONS[pedido.status];

  /** Relê o pedido depois de qualquer escrita: o status pode ter mudado por tabela. */
  const releitura = useCallback(async () => {
    try {
      aoAtualizar(await call<AdminOrder>(`/orders/${pedido.id}`));
    } catch {
      // A escrita já valeu; falhar a releitura só deixa a linha desatualizada até recarregar.
    }
  }, [call, pedido.id, aoAtualizar]);

  async function mudarStatus(alvo: OrderStatus) {
    setOcupado(true);
    setErro(null);
    setOk(null);
    try {
      const atualizado = await call<AdminOrder>(`/orders/${pedido.id}/status`, {
        method: 'PATCH',
        body: { status: alvo },
      });
      aoAtualizar(atualizado);
      setOk(
        alvo === 'CANCELLED'
          ? 'Pedido cancelado. O estoque reservado voltou para a prateleira.'
          : `Pedido agora está em ${STATUS_LABEL[alvo].toLowerCase()}.`,
      );
    } catch (e) {
      setErro(e);
    } finally {
      setOcupado(false);
    }
  }

  async function criarEnvio() {
    setOcupado(true);
    setErro(null);
    setOk(null);
    try {
      const criado = await call<AdminShipment>(`/orders/${pedido.id}/shipment`, {
        method: 'POST',
        body: { estimatedDeliveryAt: comoInstante(previsao) },
      });
      setEnvio(criado);
      setOk('Envio criado. O endereço veio do pedido, não deste formulário.');
      await releitura();
    } catch (e) {
      setErro(e);
    } finally {
      setOcupado(false);
    }
  }

  async function verEnvio() {
    setOcupado(true);
    setErro(null);
    try {
      setEnvio(await call<AdminShipment>(`/orders/${pedido.id}/shipment`));
    } catch (e) {
      setErro(e);
    } finally {
      setOcupado(false);
    }
  }

  /**
   * Despachar é `PATCH /shipments/{id}`, não `PATCH /orders/{id}/status`: essa rota carimba
   * `shippedAt` e move o pedido para `SHIPPED` junto. É por isso que `PAID → SHIPPED` não
   * aparece entre as transições oferecidas — ela existe, só não é por ali.
   */
  async function despachar(shipmentId: string) {
    setOcupado(true);
    setErro(null);
    setOk(null);
    try {
      setEnvio(await call<AdminShipment>(`/shipments/${shipmentId}`, { method: 'PATCH', body: {} }));
      setOk('Envio despachado. O pedido foi junto para enviado.');
      await releitura();
    } catch (e) {
      setErro(e);
    } finally {
      setOcupado(false);
    }
  }

  return (
    <Card style={{ padding: '16px 18px', display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        <Tag tone={STATUS_TONE[pedido.status]}>{STATUS_LABEL[pedido.status]}</Tag>
        {abandonado ? <Tag tone="danger">abandonado</Tag> : null}
        <span className="mono" style={{ fontSize: 12, color: 'var(--ink3)' }}>{pedido.id}</span>
        <span style={{ marginLeft: 'auto', fontSize: 12, color: 'var(--ink3)' }}>
          {dateTime(pedido.createdAt)}
        </span>
      </div>

      {abandonado ? (
        <p style={{ margin: 0, fontSize: 13, color: 'var(--ink2)', lineHeight: 1.6 }}>
          Ninguém cancelou: a reserva venceu em{' '}
          <span className="mono">{pedido.expiresAt ? dateTime(pedido.expiresAt) : '—'}</span> e a
          varredura devolveu o estoque. Os itens abaixo são o que o cliente ia levar.
        </p>
      ) : null}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {pedido.items.map((item) => (
          <div
            key={item.id}
            style={{ display: 'flex', justifyContent: 'space-between', gap: 16, fontSize: 13 }}
          >
            <span style={{ color: 'var(--ink2)', minWidth: 0 }}>
              <span className="mono">{item.quantity}×</span>{' '}
              {item.product?.name ?? 'produto removido do catálogo'}
            </span>
            <span className="mono" style={{ flexShrink: 0, color: 'var(--ink3)' }}>
              {brl(item.unitPrice)} · {brl(item.subtotal)}
            </span>
          </div>
        ))}
      </div>

      <div
        style={{
          display: 'flex', flexDirection: 'column', gap: 8,
          borderTop: '1px solid var(--line)', paddingTop: 12,
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14 }}>
          <span style={{ color: 'var(--ink2)' }}>Mercadoria</span>
          <span className="mono" style={{ fontSize: 15 }}>{brl(pedido.itemsTotal)}</span>
        </div>
        <FreightLine cost={pedido.shippingCost} distanceKm={pedido.shippingDistanceKm} />
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14 }}>
          <span style={{ fontWeight: 500 }}>Total</span>
          <span className="mono" style={{ fontSize: 17, fontWeight: 600 }}>{brl(pedido.total)}</span>
        </div>
      </div>

      {envio ? (
        <div
          style={{
            fontSize: 13, color: 'var(--ink2)', lineHeight: 1.7,
            border: '1px solid var(--line)', borderRadius: 8, padding: '10px 12px',
          }}
        >
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
            <Tag tone={envio.shipped ? 'ok' : 'accent'}>
              {envio.shipped ? 'despachado' : 'envio criado'}
            </Tag>
            <span className="mono" style={{ fontSize: 11, color: 'var(--ink3)' }}>{envio.id}</span>
          </div>
          <div style={{ marginTop: 6 }}>
            Previsão:{' '}
            <span className="mono">
              {envio.estimatedDeliveryAt ? dateTime(envio.estimatedDeliveryAt) : 'não informada'}
            </span>
            {envio.shippedAt ? (
              <> · despachado em <span className="mono">{dateTime(envio.shippedAt)}</span></>
            ) : null}
          </div>
        </div>
      ) : null}

      <ErrorNote error={erro} />
      {ok ? <OkNote>{ok}</OkNote> : null}

      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
        {transicoes.map((alvo) => (
          <Button
            key={alvo}
            variant={alvo === 'CANCELLED' ? 'ghost' : 'primary'}
            style={
              alvo === 'CANCELLED'
                ? { height: 44, color: 'var(--danger)', borderColor: 'var(--danger)' }
                : { height: 44 }
            }
            disabled={ocupado}
            onClick={() => mudarStatus(alvo)}
          >
            {ACAO[alvo]}
          </Button>
        ))}

        {pedido.status === 'PAID' && !pedido.shipmentId ? (
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <Input
              type="date"
              value={previsao}
              onChange={(e) => setPrevisao(e.target.value)}
              aria-label="Previsão de entrega"
              className="mono"
              disabled={ocupado}
              style={{ width: 168 }}
            />
            <Button style={{ height: 44 }} disabled={ocupado} onClick={criarEnvio}>
              Criar envio
            </Button>
          </div>
        ) : null}

        {pedido.shipmentId && !envio ? (
          <Button variant="ghost" style={{ height: 44 }} disabled={ocupado} onClick={verEnvio}>
            Ver envio
          </Button>
        ) : null}

        {envio && !envio.shipped ? (
          <Button style={{ height: 44 }} disabled={ocupado} onClick={() => despachar(envio.id)}>
            Marcar como despachado
          </Button>
        ) : null}

        {ocupado ? <Spinner size={18} /> : null}

        {transicoes.length === 0 && pedido.status === 'DELIVERED' ? (
          <span style={{ fontSize: 12, color: 'var(--ink3)' }}>
            Entregue é estado terminal — e é o que libera a avaliação do cliente.
          </span>
        ) : null}
        {transicoes.length === 0 && pedido.status === 'CANCELLED' ? (
          <span style={{ fontSize: 12, color: 'var(--ink3)' }}>
            Cancelado não volta atrás. O estoque já foi devolvido.
          </span>
        ) : null}
        {pedido.status === 'PENDING' ? (
          <span style={{ fontSize: 12, color: 'var(--ink3)' }}>
            Pagar é só pelo webhook do gateway — o painel não marca pago na mão.
          </span>
        ) : null}
      </div>
    </Card>
  );
}

/* ---------------------------------------------------------------- tela */

export default function PedidosPage() {
  const call = useApi();

  const [pagina, setPagina] = useState<Page<AdminOrder> | null>(null);
  const [indice, setIndice] = useState(0);
  const [carregando, setCarregando] = useState(true);
  const [erroLista, setErroLista] = useState<unknown>(null);

  const [idBusca, setIdBusca] = useState('');
  const [avulso, setAvulso] = useState<AdminOrder | null>(null);
  const [erroBusca, setErroBusca] = useState<unknown>(null);
  const [buscando, setBuscando] = useState(false);

  // O "carregando" é ligado por quem troca de página, não aqui: setState dentro do efeito
  // encadeia um render extra a cada busca.
  useEffect(() => {
    let vivo = true;
    call<Page<AdminOrder>>(`/orders/all?page=${indice}&size=${TAMANHO}`)
      .then((p) => { if (vivo) { setPagina(p); setErroLista(null); } })
      .catch((e: unknown) => { if (vivo) setErroLista(e); })
      .finally(() => { if (vivo) setCarregando(false); });
    return () => { vivo = false; };
  }, [call, indice]);

  const irPara = useCallback((p: number) => {
    setCarregando(true);
    setIndice(p);
  }, []);

  const substituir = useCallback((atualizado: AdminOrder) => {
    setPagina((atual) =>
      atual
        ? { ...atual, content: atual.content.map((p) => (p.id === atualizado.id ? atualizado : p)) }
        : atual);
    setAvulso((atual) => (atual && atual.id === atualizado.id ? atualizado : atual));
  }, []);

  async function buscarPorId(e: React.FormEvent) {
    e.preventDefault();
    const id = idBusca.trim();
    if (!id) return;

    setBuscando(true);
    setErroBusca(null);
    setAvulso(null);
    try {
      setAvulso(await call<AdminOrder>(`/orders/${id}`));
    } catch (err) {
      setErroBusca(err);
    } finally {
      setBuscando(false);
    }
  }

  return (
    <>
      <OriginBanner />

      <PageHead
        title="Pedidos"
        hint="Situação, itens, frete e as ações que o backend aceita para cada estado."
      />

      <div style={{ display: 'flex', flexDirection: 'column', gap: 30 }}>
        <Section
          title="Abrir um pedido pelo id"
          hint={ORDERS_SCOPE_NOTE}
        >
          <form onSubmit={buscarPorId} style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <Input
              value={idBusca}
              onChange={(e) => setIdBusca(e.target.value)}
              placeholder="0193xxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
              aria-label="Id do pedido"
              className="mono"
              style={{ flexGrow: 1, minWidth: 260, maxWidth: 420 }}
            />
            <Button type="submit" variant="ghost" disabled={buscando}>
              {buscando ? 'Buscando…' : 'Abrir'}
            </Button>
            {avulso ? (
              <Button type="button" variant="ghost" onClick={() => { setAvulso(null); setIdBusca(''); }}>
                Fechar
              </Button>
            ) : null}
          </form>

          {erroBusca instanceof ApiError && erroBusca.status === 404 ? (
            <p style={{ margin: 0, fontSize: 13, color: 'var(--danger)' }}>
              Nenhum pedido com esse id. A rota responde 404 tanto para inexistente quanto para
              id malformado.
            </p>
          ) : (
            <ErrorNote error={erroBusca} />
          )}

          {avulso ? <CartaoPedido pedido={avulso} aoAtualizar={substituir} /> : null}
        </Section>

        <Section title="Pedidos desta conta">
          <ErrorNote error={erroLista} />

          {carregando && !pagina ? (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '40px 0' }}>
              <Spinner size={22} />
            </div>
          ) : null}

          {pagina && pagina.content.length === 0 ? (
            <Empty
              title="Nenhum pedido nesta lista"
              hint="Lista vazia aqui não significa loja sem vendas: esta rota devolve os pedidos de quem chama. Use a busca por id acima para abrir o pedido de um cliente."
            />
          ) : null}

          {pagina && pagina.content.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {pagina.content.map((pedido) => (
                <CartaoPedido key={pedido.id} pedido={pedido} aoAtualizar={substituir} />
              ))}
            </div>
          ) : null}

          {pagina ? (
            <Pager page={pagina.page} totalPages={pagina.totalPages} onChange={irPara} />
          ) : null}
        </Section>
      </div>
    </>
  );
}
