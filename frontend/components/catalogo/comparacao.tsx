'use client';

/**
 * Comparação lado a lado, até 4 produtos.
 *
 * Vive só no cliente: não há endpoint de comparação, e nem deveria haver — é uma leitura de
 * dados que a página já carregou.
 *
 * As linhas de ficha saem da união dos `label` de `specs`, na ordem em que aparecem. Alinhar
 * por rótulo é o que torna a tabela útil; o que um produto não declara vira travessão, e não
 * uma linha faltando, senão as colunas deslizam.
 */

import Link from 'next/link';
import { Button, Card, Money } from '@/components/ui';
import type { Product } from '@/lib/types';
import { Estoque } from './estoque';

const CELULA: React.CSSProperties = {
  padding: '11px 14px',
  borderBottom: '1px solid var(--line)',
  verticalAlign: 'top',
  minWidth: 190,
};

const ROTULO: React.CSSProperties = {
  ...CELULA,
  fontSize: 13,
  color: 'var(--ink2)',
  fontWeight: 400,
  textAlign: 'left',
  whiteSpace: 'nowrap',
  position: 'sticky',
  left: 0,
  background: 'var(--surface)',
  minWidth: 150,
};

function rotulosDeSpec(produtos: Product[]): string[] {
  const vistos: string[] = [];
  for (const p of produtos) {
    for (const s of p.specs) if (!vistos.includes(s.label)) vistos.push(s.label);
  }
  return vistos;
}

export function TabelaComparacao({
  produtos, onRemover, onLimpar,
}: {
  produtos: Product[];
  onRemover: (id: string) => void;
  onLimpar: () => void;
}) {
  if (!produtos.length) return null;

  const rotulos = rotulosDeSpec(produtos);

  return (
    <Card style={{ overflow: 'hidden' }}>
      <div
        style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16,
          padding: '14px 16px', borderBottom: '1px solid var(--line)',
        }}
      >
        <h2 style={{ margin: 0, fontSize: 15, fontWeight: 600 }}>
          Comparando {produtos.length} de 4
        </h2>
        <Button variant="ghost" onClick={onLimpar} style={{ height: 36, padding: '0 14px', fontSize: 13 }}>
          Limpar
        </Button>
      </div>

      {produtos.length < 2 ? (
        <p style={{ margin: 0, padding: '18px 16px', fontSize: 14, color: 'var(--ink3)' }}>
          Marque ao menos mais um produto para ver a comparação.
        </p>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table style={{ borderCollapse: 'collapse', width: '100%' }}>
            <thead>
              <tr>
                <th style={{ ...ROTULO, borderBottom: '1px solid var(--line)' }} />
                {produtos.map((p) => (
                  <th key={p.id} style={{ ...CELULA, textAlign: 'left' }}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                      <Link
                        href={`/produtos/${p.id}`}
                        style={{ fontSize: 14, fontWeight: 500, color: 'var(--ink)', lineHeight: 1.35 }}
                      >
                        {p.name}
                      </Link>
                      <button
                        onClick={() => onRemover(p.id)}
                        aria-label={`Remover ${p.name} da comparação`}
                        style={{
                          alignSelf: 'flex-start', height: 28, padding: '0 10px', borderRadius: 6,
                          fontSize: 12, cursor: 'pointer', background: 'transparent',
                          color: 'var(--ink3)', border: '1px solid var(--line)',
                        }}
                      >
                        remover
                      </button>
                    </div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              <tr>
                <th scope="row" style={ROTULO}>Preço</th>
                {produtos.map((p) => (
                  <td key={p.id} style={CELULA}><Money value={p.price} size={16} /></td>
                ))}
              </tr>
              <tr>
                <th scope="row" style={ROTULO}>Categoria</th>
                {produtos.map((p) => (
                  <td key={p.id} style={{ ...CELULA, fontSize: 13 }}>
                    {p.category ?? '—'}
                  </td>
                ))}
              </tr>
              <tr>
                <th scope="row" style={ROTULO}>Estoque</th>
                {produtos.map((p) => (
                  <td key={p.id} style={CELULA}><Estoque produto={p} size={13} /></td>
                ))}
              </tr>
              <tr>
                <th scope="row" style={ROTULO}>Destaques</th>
                {produtos.map((p) => (
                  <td key={p.id} style={CELULA}>
                    {p.highlights.length ? (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                        {p.highlights.map((h, i) => (
                          <span key={i} className="mono" style={{ fontSize: 13 }}>
                            {h.value}
                            {h.unit ? (
                              <span style={{ color: 'var(--ink3)' }}> {h.unit}</span>
                            ) : null}
                          </span>
                        ))}
                      </div>
                    ) : (
                      <span style={{ color: 'var(--ink3)' }}>—</span>
                    )}
                  </td>
                ))}
              </tr>
              {rotulos.map((rotulo) => (
                <tr key={rotulo}>
                  <th scope="row" style={ROTULO}>{rotulo}</th>
                  {produtos.map((p) => {
                    const spec = p.specs.find((s) => s.label === rotulo);
                    return (
                      <td key={p.id} className="mono" style={{ ...CELULA, fontSize: 13 }}>
                        {spec ? spec.value : <span style={{ color: 'var(--ink3)' }}>—</span>}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}
