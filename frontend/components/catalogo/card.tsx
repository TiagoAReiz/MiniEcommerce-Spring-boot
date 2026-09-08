'use client';

/** O card da grade do catálogo. Só apresentação — a seleção é estado da vitrine. */

import Link from 'next/link';
import { Card, Highlights, Money } from '@/components/ui';
import type { Product } from '@/lib/types';
import { Estoque } from './estoque';
import { SemFoto, ordenarFotos } from './foto';

export function ProdutoCard({
  produto, marcado, podeMarcar, onComparar,
}: {
  produto: Product;
  marcado: boolean;
  /** Falso quando o limite de 4 já foi atingido e este não é um dos marcados. */
  podeMarcar: boolean;
  onComparar: () => void;
}) {
  const capa = ordenarFotos(produto.photos)[0];

  return (
    <Card style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Link
        href={`/produtos/${produto.id}`}
        style={{ display: 'block', aspectRatio: '4 / 3', color: 'inherit' }}
      >
        {capa ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={capa.url}
            alt={produto.name}
            loading="lazy"
            style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
          />
        ) : (
          <SemFoto label={produto.name} />
        )}
      </Link>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: 16, flexGrow: 1 }}>
        <span
          className="mono"
          style={{
            fontSize: 11, letterSpacing: '.06em', textTransform: 'uppercase', color: 'var(--ink3)',
          }}
        >
          {produto.category ?? 'Sem categoria'}
        </span>

        <Link
          href={`/produtos/${produto.id}`}
          style={{ fontSize: 16, fontWeight: 500, lineHeight: 1.35, color: 'var(--ink)' }}
        >
          {produto.name}
        </Link>

        <Highlights items={produto.highlights} />

        <div
          style={{
            marginTop: 'auto', paddingTop: 12, display: 'flex', alignItems: 'baseline',
            justifyContent: 'space-between', gap: 12,
          }}
        >
          <Money value={produto.price} size={19} />
          <Estoque produto={produto} />
        </div>

        <label
          style={{
            display: 'flex', alignItems: 'center', gap: 9, minHeight: 44, fontSize: 13,
            color: podeMarcar ? 'var(--ink2)' : 'var(--ink3)',
            cursor: podeMarcar ? 'pointer' : 'not-allowed',
            borderTop: '1px solid var(--line)', marginTop: 4,
          }}
        >
          <input
            type="checkbox"
            checked={marcado}
            disabled={!podeMarcar}
            onChange={onComparar}
            style={{ width: 16, height: 16, accentColor: 'var(--ink)', cursor: 'inherit' }}
          />
          Comparar
        </label>
      </div>
    </Card>
  );
}
