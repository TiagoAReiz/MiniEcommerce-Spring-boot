/**
 * Produto.
 *
 * A outra tela pública, e o outro motivo de existir Server Component neste projeto: o título e
 * a descrição do produto precisam estar no HTML que o buscador lê, e é isso que
 * `generateMetadata` resolve. Nada disso funcionaria se a página buscasse no cliente.
 *
 * `revalidate: 60` combina com o cache de 10 min do backend nesta rota — a página nunca é mais
 * velha que a soma dos dois, e nenhum dos dois decide venda: quem confere estoque é o checkout.
 */

import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { ApiError, api } from '@/lib/api';
import type { Product } from '@/lib/types';
import { StoreHeader } from '@/components/chrome';
import { Card, Highlights, Money, SpecTable } from '@/components/ui';
import { Estoque } from '@/components/catalogo/estoque';
import { Galeria } from '@/components/catalogo/galeria';
import { Comprar } from '@/components/catalogo/comprar';

/**
 * Devolve null no 404 em vez de estourar — a página chama `notFound()`, e `generateMetadata`
 * precisa do mesmo dado sem derrubar o render. O fetch é o mesmo, então o Next reaproveita a
 * resposta entre as duas chamadas.
 */
async function buscarProduto(id: string): Promise<Product | null> {
  try {
    return await api<Product>(`/products/${id}`, { revalidate: 60 });
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

export async function generateMetadata(
  { params }: { params: Promise<{ id: string }> },
): Promise<Metadata> {
  const { id } = await params;
  const produto = await buscarProduto(id);
  if (!produto) return { title: 'Produto não encontrado — VOLT' };

  const ficha = produto.highlights
    .map((h) => [h.value, h.unit].filter(Boolean).join(' '))
    .join(' · ');

  return {
    title: `${produto.name} — VOLT`,
    description:
      (produto.description ?? [produto.category, ficha].filter(Boolean).join(' · '))
        .slice(0, 160) || produto.name,
  };
}

export default async function ProdutoPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const produto = await buscarProduto(id);
  if (!produto) notFound();

  return (
    <>
      <StoreHeader />
      <main className="container" style={{ padding: '28px 20px 64px' }}>
        <Link href="/" style={{ fontSize: 14, color: 'var(--ink3)' }}>← Catálogo</Link>

        <div
          style={{
            display: 'grid',
            // auto-fit colapsa para uma coluna sozinho: sem media query em inline style.
            gridTemplateColumns: 'repeat(auto-fit, minmax(330px, 1fr))',
            gap: 40,
            marginTop: 20,
            alignItems: 'start',
          }}
        >
          <Galeria photos={produto.photos} nome={produto.name} />

          <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <span
                className="mono"
                style={{
                  fontSize: 11, letterSpacing: '.06em', textTransform: 'uppercase',
                  color: 'var(--ink3)',
                }}
              >
                {produto.category ?? 'Sem categoria'}
              </span>
              <h1 style={{ margin: 0, fontSize: 28, fontWeight: 700, letterSpacing: '-.02em', lineHeight: 1.25 }}>
                {produto.name}
              </h1>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 14, flexWrap: 'wrap' }}>
                <Money value={produto.price} size={28} />
                <Estoque produto={produto} size={13} />
              </div>
            </div>

            <Highlights items={produto.highlights} />

            <Comprar produto={produto} />

            <Card style={{ padding: '14px 16px' }}>
              <ul
                style={{
                  margin: 0, padding: 0, listStyle: 'none',
                  display: 'flex', flexDirection: 'column', gap: 8,
                  fontSize: 13, color: 'var(--ink2)', lineHeight: 1.5,
                }}
              >
                <li>
                  O frete é calculado no checkout, pela distância entre o CEP da loja e o seu.
                </li>
                <li>
                  Ao fechar o pedido, o estoque fica reservado por <strong className="mono">30 minutos</strong>.
                </li>
              </ul>
            </Card>

            {produto.description ? (
              <p style={{ margin: 0, fontSize: 15, lineHeight: 1.7, color: 'var(--ink2)' }}>
                {produto.description}
              </p>
            ) : null}
          </div>
        </div>

        <section style={{ marginTop: 48, maxWidth: 620 }}>
          <h2 style={{ margin: '0 0 8px', fontSize: 17, fontWeight: 600 }}>Ficha técnica</h2>
          <SpecTable specs={produto.specs} />
        </section>
      </main>
    </>
  );
}
