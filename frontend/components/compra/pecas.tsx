'use client';

/** Peças visuais que carrinho e checkout repetem. */

import Link from 'next/link';
import { brl } from '@/lib/format';
import { Button, Card, Money, Tag } from '@/components/ui';
import { fotoCapa, type LinhaDetalhada } from './dados';

/* ------------------------------------------------------------------ foto */

export function Foto({ url, alt, size = 76 }: { url: string | null; alt: string; size?: number }) {
  return (
    <div
      style={{
        width: size, height: size, flexShrink: 0, borderRadius: 8, overflow: 'hidden',
        background: 'var(--surface2)', border: '1px solid var(--line)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}
    >
      {url ? (
        // next/image exigiria configurar domínios remotos em next.config, que não é meu.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={url} alt={alt} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
      ) : (
        <span className="mono" style={{ fontSize: 10, color: 'var(--ink3)' }}>sem foto</span>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ título de seção */

export function Secao({
  titulo, acao, children,
}: { titulo: string; acao?: React.ReactNode; children: React.ReactNode }) {
  return (
    <section style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        <h2 style={{ margin: 0, fontSize: 15, fontWeight: 600 }}>{titulo}</h2>
        <div style={{ marginLeft: 'auto' }}>{acao}</div>
      </div>
      {children}
    </section>
  );
}

/* ------------------------------------------------------------------ quantidade */

function Passo({
  label, onClick, disabled,
}: { label: string; onClick: () => void; disabled?: boolean }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      aria-label={label === '−' ? 'Diminuir quantidade' : 'Aumentar quantidade'}
      style={{
        width: 44, height: 44, flexShrink: 0, borderRadius: 8, fontSize: 18,
        background: 'transparent', color: 'var(--ink)', border: '1px solid var(--line)',
        cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.4 : 1,
      }}
    >
      {label}
    </button>
  );
}

/* ------------------------------------------------------------------ linha do carrinho */

export function LinhaCarrinho({
  linha, ocupado, onQuantidade, onRemover,
}: {
  linha: LinhaDetalhada;
  ocupado: boolean;
  onQuantidade: (q: number) => void;
  onRemover: () => void;
}) {
  const p = linha.product;
  const nome = p?.name ?? 'Produto indisponível';
  const precoMudou = !!p && p.price !== linha.unitPrice;
  const faltaEstoque = !!p && p.stock < linha.quantity;
  const subtotal = linha.unitPrice * linha.quantity;

  return (
    <Card style={{ padding: 16, display: 'flex', gap: 16, alignItems: 'flex-start' }}>
      <Foto url={fotoCapa(p)} alt={nome} />

      <div style={{ flexGrow: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 8 }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          {p ? (
            <Link href={`/produtos/${p.id}`} style={{ fontSize: 15, fontWeight: 500, color: 'var(--ink)' }}>
              {nome}
            </Link>
          ) : (
            <span style={{ fontSize: 15, fontWeight: 500, color: 'var(--ink3)' }}>{nome}</span>
          )}
          <span className="mono" style={{ fontSize: 12, color: 'var(--ink3)' }}>
            {brl(linha.unitPrice)} cada
          </span>
        </div>

        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {precoMudou ? <Tag tone="accent">preço mudou</Tag> : null}
          {faltaEstoque ? <Tag tone="danger">só {p!.stock} em estoque</Tag> : null}
          {p && !p.sellable && !faltaEstoque ? <Tag tone="danger">indisponível</Tag> : null}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 2 }}>
          <Passo label="−" onClick={() => onQuantidade(linha.quantity - 1)} disabled={ocupado} />
          <span
            className="mono"
            aria-live="polite"
            style={{ minWidth: 34, textAlign: 'center', fontSize: 15 }}
          >
            {linha.quantity}
          </span>
          <Passo label="+" onClick={() => onQuantidade(linha.quantity + 1)} disabled={ocupado} />
          <button
            onClick={onRemover}
            disabled={ocupado}
            style={{
              height: 44, padding: '0 12px', marginLeft: 6, borderRadius: 8, fontSize: 13,
              background: 'transparent', color: 'var(--ink3)', border: '1px solid transparent',
              cursor: ocupado ? 'not-allowed' : 'pointer',
            }}
          >
            Remover
          </button>
        </div>
      </div>

      <div style={{ textAlign: 'right', flexShrink: 0 }}>
        <Money value={subtotal} size={16} />
      </div>
    </Card>
  );
}

/* ------------------------------------------------------------------ item compacto */

/** A conferência do checkout: o que vai no pedido, sem controles. */
export function MiniItem({ linha }: { linha: LinhaDetalhada }) {
  const nome = linha.product?.name ?? 'Produto indisponível';
  return (
    <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
      <Foto url={fotoCapa(linha.product)} alt={nome} size={44} />
      <div style={{ flexGrow: 1, minWidth: 0 }}>
        <p style={{ margin: 0, fontSize: 14, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {nome}
        </p>
        <span className="mono" style={{ fontSize: 12, color: 'var(--ink3)' }}>
          {linha.quantity} × {brl(linha.unitPrice)}
        </span>
      </div>
      <span className="mono" style={{ fontSize: 14 }}>{brl(linha.unitPrice * linha.quantity)}</span>
    </div>
  );
}

/* ------------------------------------------------------------------ ação de rodapé */

export function Rodape({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>{children}</div>
  );
}

export function VoltarAoCatalogo() {
  return (
    <Link href="/">
      <Button variant="ghost">Ver o catálogo</Button>
    </Link>
  );
}
