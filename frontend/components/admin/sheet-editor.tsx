'use client';

/**
 * Os editores das duas listas da ficha do produto: destaques e ficha técnica.
 *
 * As duas são **substituídas por inteiro** no `PUT`. Isso muda o desenho: não existe "editar
 * a linha 3", existe editar a ficha e reenviá-la. Daí estes editores trabalharem sempre sobre
 * o array completo, e o formulário nunca mandar um `PUT` com a ficha que ele não carregou.
 *
 * Reordenar é função de primeira classe, não enfeite: a comparação lado a lado alinha os
 * produtos pela ordem digitada das specs, então mover uma linha é mudar o produto.
 */

import type { ProductHighlight, ProductSpec } from '@/lib/types';
import { Input } from '@/components/ui';
import { IconBtn, LIMITS_HINT, Section } from './primitives';
import { LIMITS, MAX_HIGHLIGHTS, MAX_SPECS } from './contract';

/* ---------------------------------------------------------------- moldura */

const ROW: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 8,
};

const ADD: React.CSSProperties = {
  height: 44, padding: '0 16px', borderRadius: 8, cursor: 'pointer', fontSize: 14,
  alignSelf: 'flex-start', background: 'transparent', color: 'var(--ink2)',
  border: '1px dashed var(--line)',
};

function Counter({ used, max }: { used: number; max: number }) {
  const full = used >= max;
  return (
    <span className="mono" style={{ fontSize: 12, color: full ? 'var(--accent)' : 'var(--ink3)' }}>
      {used}/{max}
    </span>
  );
}

/** Move um item da lista sem mutar o array recebido. */
function moved<T>(list: T[], from: number, to: number): T[] {
  if (to < 0 || to >= list.length) return list;
  const next = list.slice();
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}

/* ---------------------------------------------------------------- destaques */

/**
 * Os números grandes do card. Valor e unidade separados porque são tipografados em tamanhos
 * diferentes — juntá-los faria o front adivinhar onde o número termina, e ele erra em "2 TB".
 *
 * O limite de três é do backend: um quarto vira 400, não é descartado em silêncio.
 */
export function HighlightsEditor({
  value, onChange, disabled,
}: {
  value: ProductHighlight[];
  onChange: (next: ProductHighlight[]) => void;
  disabled?: boolean;
}) {
  const set = (i: number, patch: Partial<ProductHighlight>) =>
    onChange(value.map((h, k) => (k === i ? { ...h, ...patch } : h)));

  return (
    <Section
      title="Destaques"
      hint="Até três números grandes do card. O valor vai grande, a unidade vai pequena ao lado."
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {value.map((h, i) => (
          <div key={i} style={ROW}>
            <Input
              value={h.value}
              onChange={(e) => set(i, { value: e.target.value })}
              disabled={disabled}
              maxLength={LIMITS.highlightValue}
              placeholder="144"
              aria-label={`Valor do destaque ${i + 1}`}
              className="mono"
              style={{ width: 130, flexShrink: 0 }}
            />
            <Input
              value={h.unit ?? ''}
              onChange={(e) => set(i, { unit: e.target.value })}
              disabled={disabled}
              maxLength={LIMITS.highlightUnit}
              placeholder="Hz"
              aria-label={`Unidade do destaque ${i + 1}`}
              style={{ flexGrow: 1, minWidth: 0 }}
            />
            <IconBtn
              glyph="↑" label="Subir" disabled={disabled || i === 0}
              onClick={() => onChange(moved(value, i, i - 1))}
            />
            <IconBtn
              glyph="↓" label="Descer" disabled={disabled || i === value.length - 1}
              onClick={() => onChange(moved(value, i, i + 1))}
            />
            <IconBtn
              glyph="×" label="Remover destaque" tone="danger" disabled={disabled}
              onClick={() => onChange(value.filter((_, k) => k !== i))}
            />
          </div>
        ))}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <button
          type="button"
          style={ADD}
          disabled={disabled || value.length >= MAX_HIGHLIGHTS}
          onClick={() => onChange([...value, { value: '', unit: '' }])}
        >
          + Adicionar destaque
        </button>
        <Counter used={value.length} max={MAX_HIGHLIGHTS} />
      </div>
    </Section>
  );
}

/* ---------------------------------------------------------------- ficha técnica */

/**
 * A ficha técnica, **na ordem digitada** — é dessa ordem que a comparação lado a lado alinha
 * os produtos. Por isso as setas existem: mover "Tela" para cima em um produto e não no outro
 * desalinha a comparação dos dois.
 */
export function SpecsEditor({
  value, onChange, disabled,
}: {
  value: ProductSpec[];
  onChange: (next: ProductSpec[]) => void;
  disabled?: boolean;
}) {
  const set = (i: number, patch: Partial<ProductSpec>) =>
    onChange(value.map((s, k) => (k === i ? { ...s, ...patch } : s)));

  return (
    <Section
      title="Ficha técnica"
      hint="Até 30 linhas, na ordem em que aparecem aqui. É essa ordem que alinha a comparação entre produtos."
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {value.map((s, i) => (
          <div key={i} style={ROW}>
            <span
              className="mono"
              aria-hidden
              style={{ width: 22, flexShrink: 0, fontSize: 12, color: 'var(--ink3)', textAlign: 'right' }}
            >
              {i + 1}
            </span>
            <Input
              value={s.label}
              onChange={(e) => set(i, { label: e.target.value })}
              disabled={disabled}
              maxLength={LIMITS.specLabel}
              placeholder="Tela"
              aria-label={`Rótulo da linha ${i + 1}`}
              style={{ width: 200, flexShrink: 0 }}
            />
            <Input
              value={s.value}
              onChange={(e) => set(i, { value: e.target.value })}
              disabled={disabled}
              maxLength={LIMITS.specValue}
              placeholder='27" IPS · 2560×1440'
              aria-label={`Valor da linha ${i + 1}`}
              className="mono"
              style={{ flexGrow: 1, minWidth: 0 }}
            />
            <IconBtn
              glyph="↑" label="Subir" disabled={disabled || i === 0}
              onClick={() => onChange(moved(value, i, i - 1))}
            />
            <IconBtn
              glyph="↓" label="Descer" disabled={disabled || i === value.length - 1}
              onClick={() => onChange(moved(value, i, i + 1))}
            />
            <IconBtn
              glyph="×" label="Remover linha" tone="danger" disabled={disabled}
              onClick={() => onChange(value.filter((_, k) => k !== i))}
            />
          </div>
        ))}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <button
          type="button"
          style={ADD}
          disabled={disabled || value.length >= MAX_SPECS}
          onClick={() => onChange([...value, { label: '', value: '' }])}
        >
          + Adicionar linha
        </button>
        <Counter used={value.length} max={MAX_SPECS} />
        <span style={{ fontSize: 12, color: 'var(--ink3)' }}>{LIMITS_HINT}</span>
      </div>
    </Section>
  );
}
