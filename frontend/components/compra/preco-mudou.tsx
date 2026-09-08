'use client';

/**
 * A tela do 409 `PRICE_CHANGED`.
 *
 * O carrinho vive 7 dias no Redis, e nesse tempo o preço pode mudar. O backend recusa o
 * checkout em vez de cobrar o valor novo calado — e é justamente por isso que isto aqui é uma
 * tela, e não um toast: um aviso que some sozinho não é decisão do cliente, é notificação.
 *
 * Cada item divergente tem duas saídas explícitas, e nenhuma delas é "continuar assim":
 * aceitar o preço de agora, ou tirar o item do carrinho. Só quando não sobra divergência é
 * que o checkout pode ser tentado de novo.
 */

import { useState } from 'react';
import { brl } from '@/lib/format';
import { ApiError } from '@/lib/api';
import { ERROR_CODES } from '@/lib/errors';
import { Banner, Button, Card, Spinner } from '@/components/ui';
import {
  aceitarPrecoNovo, carregarCarrinho, divergencias, fotoCapa, removerLinha,
  type CarrinhoDetalhado, type Chamada, type Divergencia,
} from './dados';
import { Foto } from './pecas';

function Valor({ rotulo, valor, cor }: { rotulo: string; valor: string; cor?: string }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 92 }}>
      <span style={{ fontSize: 11, color: 'var(--ink3)' }}>{rotulo}</span>
      <span className="mono" style={{ fontSize: 16, fontWeight: 500, color: cor ?? 'var(--ink)' }}>
        {valor}
      </span>
    </div>
  );
}

function ItemDivergente({
  d, ocupado, onAceitar, onRemover,
}: {
  d: Divergencia;
  ocupado: boolean;
  onAceitar: () => void;
  onRemover: () => void;
}) {
  const encareceu = d.diferenca > 0;
  // Âmbar é urgência/preço, verde é o que joga a favor de quem compra.
  const corDiferenca = encareceu ? 'var(--accent)' : 'var(--ok)';
  const sinal = encareceu ? '+' : '−';
  const total = Math.abs(d.diferenca) * d.linha.quantity;

  return (
    <Card style={{ padding: 18, display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
        <Foto url={fotoCapa(d.product)} alt={d.product.name} size={56} />
        <div style={{ flexGrow: 1, minWidth: 0 }}>
          <p style={{ margin: 0, fontSize: 15, fontWeight: 500 }}>{d.product.name}</p>
          <span className="mono" style={{ fontSize: 12, color: 'var(--ink3)' }}>
            {d.linha.quantity} {d.linha.quantity === 1 ? 'unidade' : 'unidades'}
          </span>
        </div>
      </div>

      <div
        style={{
          display: 'flex', gap: 24, flexWrap: 'wrap', alignItems: 'flex-end',
          padding: '14px 16px', background: 'var(--surface2)', borderRadius: 8,
        }}
      >
        <Valor rotulo="Preço de antes" valor={brl(d.linha.unitPrice)} cor="var(--ink3)" />
        <Valor rotulo="Preço de agora" valor={brl(d.product.price)} />
        <Valor
          rotulo={d.linha.quantity > 1 ? 'Diferença por unidade' : 'Diferença'}
          valor={`${sinal} ${brl(Math.abs(d.diferenca))}`}
          cor={corDiferenca}
        />
        {d.linha.quantity > 1 ? (
          <Valor rotulo="Diferença na linha" valor={`${sinal} ${brl(total)}`} cor={corDiferenca} />
        ) : null}
      </div>

      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        <Button onClick={onAceitar} disabled={ocupado}>
          {ocupado ? <Spinner size={14} /> : null}
          Aceitar {brl(d.product.price)}
        </Button>
        <Button variant="ghost" onClick={onRemover} disabled={ocupado}>
          Remover do carrinho
        </Button>
      </div>
    </Card>
  );
}

export function PrecoMudou({
  call, carrinho, onAtualizado, onContinuar, rotuloContinuar,
}: {
  call: Chamada;
  carrinho: CarrinhoDetalhado;
  onAtualizado: (c: CarrinhoDetalhado) => void;
  onContinuar: () => void;
  rotuloContinuar: string;
}) {
  const [ocupado, setOcupado] = useState<string | null>(null);
  const [falha, setFalha] = useState<string | null>(null);

  const lista = divergencias(carrinho);

  const executar = async (productId: string, acao: () => Promise<CarrinhoDetalhado>) => {
    setOcupado(productId);
    setFalha(null);
    try {
      onAtualizado(await acao());
    } catch (e) {
      // O caso real: o estoque acabou enquanto o item era refeito pelo preço novo.
      const api = e instanceof ApiError ? e : null;
      setFalha(
        api?.is(ERROR_CODES.INSUFFICIENT_STOCK)
          ? 'O estoque acabou enquanto atualizávamos este item. Ele saiu do carrinho.'
          : 'Não conseguimos atualizar o carrinho agora. Tente de novo.',
      );
      try {
        onAtualizado(await carregarCarrinho(call));
      } catch {
        /* a mensagem acima já diz o que houve */
      }
    } finally {
      setOcupado(null);
    }
  };

  if (!lista.length) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
        <Banner title="Preços conferidos">
          Todos os itens estão pelo preço de agora. Nada foi cobrado até aqui.
        </Banner>
        {falha ? <p style={{ margin: 0, fontSize: 14, color: 'var(--danger)' }}>{falha}</p> : null}
        <div>
          <Button onClick={onContinuar}>{rotuloContinuar}</Button>
        </div>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <Banner title={lista.length === 1 ? 'O preço de um item mudou' : `O preço de ${lista.length} itens mudou`}>
        Seu carrinho ficou parado e o preço mudou nesse meio-tempo.{' '}
        <strong>Nada foi cobrado</strong> — o pedido só é fechado depois que você decidir item
        por item.
      </Banner>

      {falha ? (
        <p style={{ margin: 0, fontSize: 14, color: 'var(--danger)' }}>{falha}</p>
      ) : null}

      {lista.map((d) => (
        <ItemDivergente
          key={d.linha.productId}
          d={d}
          ocupado={ocupado === d.linha.productId}
          onAceitar={() =>
            executar(d.linha.productId, () =>
              aceitarPrecoNovo(call, d.linha.productId, d.linha.quantity),
            )
          }
          onRemover={() =>
            executar(d.linha.productId, async () => {
              await removerLinha(call, d.linha.productId);
              return carregarCarrinho(call);
            })
          }
        />
      ))}

      <p style={{ margin: 0, fontSize: 13, color: 'var(--ink3)' }}>
        Falta{lista.length === 1 ? '' : 'm'} {lista.length}{' '}
        {lista.length === 1 ? 'item' : 'itens'} para decidir.
      </p>
    </div>
  );
}
