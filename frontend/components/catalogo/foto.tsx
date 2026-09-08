/**
 * Fotos do produto: ordenação e ausência.
 *
 * O backend não elege capa — devolve o array e o `isCover`. Quem escolhe é o cliente, então a
 * ordenação vive aqui e não em cada tela.
 */

import type { ProductPhoto } from '@/lib/types';

/** Capa primeiro, o resto por `position`. */
export function ordenarFotos(photos: ProductPhoto[]): ProductPhoto[] {
  return [...photos].sort((a, b) => {
    if (a.isCover !== b.isCover) return a.isCover ? -1 : 1;
    return a.position - b.position;
  });
}

/**
 * O que aparece quando o produto não tem foto.
 *
 * Desenho, não emoji: emoji muda de forma e de cor a cada sistema, e aqui isto ocupa o lugar
 * de uma imagem de produto — precisa parecer parte da interface.
 */
export function SemFoto({ label = 'Sem foto' }: { label?: string }) {
  return (
    <div
      role="img"
      aria-label={label}
      style={{
        width: '100%', height: '100%', display: 'flex', alignItems: 'center',
        justifyContent: 'center', background: 'var(--surface2)',
      }}
    >
      <svg width="64" height="64" viewBox="0 0 64 64" fill="none" aria-hidden="true">
        <rect
          x="8.5" y="14.5" width="47" height="30" rx="3.5"
          stroke="var(--ink3)" strokeWidth="1.5"
        />
        <path d="M26 44v6m12-6v6M20 50.5h24" stroke="var(--ink3)" strokeWidth="1.5" strokeLinecap="round" />
        <path
          d="M14 38.5l9.5-9.5 6.5 6.5 8.5-8.5 11.5 11.5"
          stroke="var(--ink3)" strokeWidth="1.5" strokeLinejoin="round"
        />
        <circle cx="23" cy="22" r="2.5" stroke="var(--ink3)" strokeWidth="1.5" />
      </svg>
    </div>
  );
}
