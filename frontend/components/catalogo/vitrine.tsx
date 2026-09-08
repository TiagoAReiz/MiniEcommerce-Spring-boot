'use client';

/**
 * A parte interativa do catálogo: filtro por categoria e seleção para comparar.
 *
 * Recebe a página de produtos pronta do servidor. A página é Server Component porque catálogo
 * é a tela onde SEO importa — então tudo que precisa de estado desce para cá, por props, em
 * vez de virar um `useEffect` que buscaria de novo o que o HTML já traz.
 */

import { useMemo, useState } from 'react';
import { Chip, Empty } from '@/components/ui';
import type { Product } from '@/lib/types';
import { ProdutoCard } from './card';
import { TabelaComparacao } from './comparacao';

const MAX_COMPARAR = 4;
const SEM_CATEGORIA = 'Sem categoria';

export function Vitrine({ produtos }: { produtos: Product[] }) {
  const [categoria, setCategoria] = useState<string | null>(null);
  const [marcados, setMarcados] = useState<string[]>([]);

  const categorias = useMemo(() => {
    const nomeadas = [...new Set(produtos.map((p) => p.category).filter((c): c is string => !!c))]
      .sort((a, b) => a.localeCompare(b, 'pt-BR'));
    // Produto sem categoria só apareceria em "Todos" — a chip existe para ele não sumir.
    return produtos.some((p) => !p.category) ? [...nomeadas, SEM_CATEGORIA] : nomeadas;
  }, [produtos]);

  const visiveis = useMemo(() => {
    if (categoria === null) return produtos;
    if (categoria === SEM_CATEGORIA) return produtos.filter((p) => !p.category);
    return produtos.filter((p) => p.category === categoria);
  }, [produtos, categoria]);

  // Busca na lista inteira, não na filtrada: trocar de categoria não pode desfazer a comparação.
  const comparados = useMemo(
    () => marcados
      .map((id) => produtos.find((p) => p.id === id))
      .filter((p): p is Product => !!p),
    [marcados, produtos],
  );

  const alternar = (id: string) =>
    setMarcados((atual) =>
      atual.includes(id)
        ? atual.filter((x) => x !== id)
        : atual.length >= MAX_COMPARAR ? atual : [...atual, id],
    );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {categorias.length > 1 ? (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Chip label="Todos" active={categoria === null} onClick={() => setCategoria(null)} />
          {categorias.map((c) => (
            <Chip
              key={c}
              label={c}
              active={categoria === c}
              onClick={() => setCategoria(c)}
            />
          ))}
        </div>
      ) : null}

      {comparados.length ? (
        <TabelaComparacao
          produtos={comparados}
          onRemover={(id) => setMarcados((atual) => atual.filter((x) => x !== id))}
          onLimpar={() => setMarcados([])}
        />
      ) : null}

      {visiveis.length ? (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
            gap: 18,
          }}
        >
          {visiveis.map((p) => (
            <ProdutoCard
              key={p.id}
              produto={p}
              marcado={marcados.includes(p.id)}
              podeMarcar={marcados.includes(p.id) || marcados.length < MAX_COMPARAR}
              onComparar={() => alternar(p.id)}
            />
          ))}
        </div>
      ) : (
        <Empty
          title="Nada nesta categoria"
          hint="Escolha outra categoria ou volte para Todos."
        />
      )}
    </div>
  );
}
