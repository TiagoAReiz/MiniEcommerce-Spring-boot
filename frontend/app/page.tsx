/**
 * Catálogo.
 *
 * Server Component de propósito: `/products` é público, então dá para buscar sem token, e esta
 * é uma das duas telas onde SEO importa — o HTML precisa sair do servidor com os produtos
 * dentro. `revalidate: 60` deixa a página estática entre revalidações, e um minuto de atraso no
 * estoque é aceitável porque quem decide a venda é o backend no checkout, não este HTML.
 */

import { ApiError, api } from '@/lib/api';
import type { Page, Product } from '@/lib/types';
import { StoreHeader } from '@/components/chrome';
import { Banner, Empty } from '@/components/ui';
import { Vitrine } from '@/components/catalogo/vitrine';

const TAMANHO_PAGINA = 24;

async function buscarProdutos(q: string): Promise<Page<Product> | null> {
  const params = new URLSearchParams({ page: '0', size: String(TAMANHO_PAGINA) });
  if (q) params.set('q', q);
  try {
    return await api<Page<Product>>(`/products?${params}`, { revalidate: 60 });
  } catch (e) {
    // A API fora do ar não pode virar tela de erro do Next: a loja mostra o aviso e segue de pé.
    if (e instanceof ApiError || e instanceof TypeError) return null;
    throw e;
  }
}

export default async function CatalogoPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string | string[] }>;
}) {
  const { q } = await searchParams;
  const busca = (Array.isArray(q) ? q[0] : q)?.trim() ?? '';
  const pagina = await buscarProdutos(busca);

  return (
    <>
      <StoreHeader />
      <main className="container" style={{ padding: '36px 20px 64px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>
          <header style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <h1 style={{ margin: 0, fontSize: 30, fontWeight: 700, letterSpacing: '-.02em' }}>
              {busca ? `Resultados para "${busca}"` : 'Eletrônicos com ficha técnica completa'}
            </h1>
            <p style={{ margin: 0, fontSize: 15, color: 'var(--ink2)', maxWidth: 620, lineHeight: 1.6 }}>
              Compare até quatro produtos lado a lado. O frete é calculado no checkout pela
              distância até o seu CEP.
            </p>

            {/* Form GET puro: a busca é do servidor, e assim funciona antes de qualquer JavaScript. */}
            <form
              action="/"
              method="get"
              style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 4 }}
            >
              <input
                type="search"
                name="q"
                defaultValue={busca}
                placeholder="Buscar por nome"
                aria-label="Buscar produtos"
                style={{
                  height: 44, padding: '0 14px', borderRadius: 8, minWidth: 240, flexGrow: 1,
                  maxWidth: 380, border: '1px solid var(--line)', background: 'var(--surface)',
                  color: 'var(--ink)',
                }}
              />
              <button
                type="submit"
                style={{
                  height: 44, padding: '0 22px', borderRadius: 'var(--radius)', cursor: 'pointer',
                  border: '1px solid transparent', background: 'var(--ink)', color: 'var(--bg)',
                  fontSize: 15, fontWeight: 500,
                }}
              >
                Buscar
              </button>
            </form>
          </header>

          {pagina === null ? (
            <Banner tone="danger" title="Não foi possível carregar o catálogo">
              A loja está temporariamente indisponível. Atualize a página em alguns instantes.
            </Banner>
          ) : pagina.content.length === 0 ? (
            <Empty
              title={busca ? 'Nenhum produto encontrado' : 'Catálogo vazio'}
              hint={busca ? 'Tente outro termo de busca.' : 'Nenhum produto publicado ainda.'}
            />
          ) : (
            <>
              <Vitrine produtos={pagina.content} />
              <p className="mono" style={{ margin: 0, fontSize: 12, color: 'var(--ink3)' }}>
                {pagina.content.length} de {pagina.totalElements} produtos
              </p>
            </>
          )}
        </div>
      </main>
    </>
  );
}
