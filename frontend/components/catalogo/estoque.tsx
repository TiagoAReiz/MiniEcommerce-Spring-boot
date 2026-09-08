/**
 * O estado de estoque, em um lugar só.
 *
 * Sem diretiva de propósito: a página de produto (servidor) e a vitrine (cliente) mostram a
 * mesma frase, e duas cópias divergiriam na primeira mudança de regra.
 *
 * A cor carrega significado, não decoração: `--danger` é bloqueio (não dá para comprar),
 * `--accent` é urgência (vai acabar), `--ink2` é informação neutra.
 */

const URGENTE = 6;

export interface EstoqueInfo {
  texto: string;
  cor: string;
}

export function estoqueInfo(p: { sellable: boolean; stock: number }): EstoqueInfo {
  // `sellable` já é `active && stock > 0` no backend — não recalcular a regra aqui.
  if (!p.sellable) return { texto: 'sem estoque', cor: 'var(--danger)' };
  if (p.stock < URGENTE) return { texto: `${p.stock} em estoque`, cor: 'var(--accent)' };
  return { texto: `${p.stock} em estoque`, cor: 'var(--ink2)' };
}

export function Estoque({
  produto, size = 12,
}: { produto: { sellable: boolean; stock: number }; size?: number }) {
  const { texto, cor } = estoqueInfo(produto);
  return (
    <span className="mono" style={{ fontSize: size, color: cor }}>
      {texto}
    </span>
  );
}
