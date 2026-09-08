'use client';

/** Galeria do produto. Cliente porque trocar a foto em foco é estado, e só isso. */

import { useState } from 'react';
import type { ProductPhoto } from '@/lib/types';
import { SemFoto, ordenarFotos } from './foto';

export function Galeria({ photos, nome }: { photos: ProductPhoto[]; nome: string }) {
  const fotos = ordenarFotos(photos);
  const [ativa, setAtiva] = useState(0);

  const moldura: React.CSSProperties = {
    aspectRatio: '4 / 3', borderRadius: 'var(--radius)', overflow: 'hidden',
    border: '1px solid var(--line)', background: 'var(--surface)',
  };

  if (!fotos.length) {
    return <div style={moldura}><SemFoto label={nome} /></div>;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div style={moldura}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={fotos[ativa].url}
          alt={nome}
          style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
        />
      </div>

      {fotos.length > 1 ? (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {fotos.map((f, i) => (
            <button
              key={f.id}
              onClick={() => setAtiva(i)}
              aria-label={`Foto ${i + 1} de ${fotos.length}`}
              aria-current={i === ativa}
              style={{
                width: 66, height: 66, padding: 0, borderRadius: 8, overflow: 'hidden',
                cursor: 'pointer', background: 'var(--surface)',
                border: `1px solid ${i === ativa ? 'var(--ink)' : 'var(--line)'}`,
                opacity: i === ativa ? 1 : 0.7,
              }}
            >
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={f.url}
                alt=""
                loading="lazy"
                style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
              />
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
