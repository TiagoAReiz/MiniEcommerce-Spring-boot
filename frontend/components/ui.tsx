'use client';

/**
 * Primitivas compartilhadas por todas as telas.
 *
 * Regra do design que estas peças carregam: `--accent` (âmbar) significa preço e urgência,
 * `--accent2` (ciano) significa medido/confirmado, `--danger` significa bloqueio. Se um
 * componente novo precisar de cor, escolha por esse significado, não por gosto.
 */

import { useEffect, useMemo, useState, useSyncExternalStore } from 'react';
import { brl, hhmmss, km, mmss } from '@/lib/format';
import type { ProductHighlight, ProductSpec } from '@/lib/types';

/* ---------------------------------------------------------------- botão */

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'ghost' | 'danger';
  full?: boolean;
};

export function Button({ variant = 'primary', full, style, ...rest }: ButtonProps) {
  const base: React.CSSProperties = {
    height: 46,
    padding: '0 22px',
    borderRadius: 'var(--radius)',
    border: '1px solid transparent',
    fontSize: 15,
    fontWeight: 500,
    cursor: rest.disabled ? 'not-allowed' : 'pointer',
    opacity: rest.disabled ? 0.5 : 1,
    width: full ? '100%' : undefined,
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  };
  const skin: Record<string, React.CSSProperties> = {
    primary: { background: 'var(--ink)', color: 'var(--bg)' },
    ghost: { background: 'transparent', color: 'var(--ink)', borderColor: 'var(--line)' },
    danger: { background: 'var(--danger)', color: 'var(--surface)' },
  };
  return <button {...rest} style={{ ...base, ...skin[variant], ...style }} />;
}

/* ---------------------------------------------------------------- caixas */

export function Card({ children, style }: { children: React.ReactNode; style?: React.CSSProperties }) {
  return (
    <div
      style={{
        background: 'var(--surface)',
        border: '1px solid var(--line)',
        borderRadius: 'var(--radius)',
        ...style,
      }}
    >
      {children}
    </div>
  );
}

export function Chip({
  label, active, onClick,
}: { label: string; active?: boolean; onClick?: () => void }) {
  return (
    <button
      onClick={onClick}
      style={{
        height: 34,
        padding: '0 14px',
        borderRadius: 999,
        cursor: 'pointer',
        fontSize: 13,
        background: active ? 'var(--ink)' : 'transparent',
        color: active ? 'var(--bg)' : 'var(--ink2)',
        border: `1px solid ${active ? 'var(--ink)' : 'var(--line)'}`,
      }}
    >
      {label}
    </button>
  );
}

export function Tag({
  children, tone = 'neutral',
}: { children: React.ReactNode; tone?: 'neutral' | 'ok' | 'accent' | 'danger' }) {
  const fg = {
    neutral: 'var(--ink3)', ok: 'var(--ok)', accent: 'var(--accent)', danger: 'var(--danger)',
  }[tone];
  return (
    <span
      className="mono"
      style={{
        display: 'inline-flex', alignItems: 'center', height: 24, padding: '0 9px',
        borderRadius: 5, fontSize: 11, letterSpacing: '.04em', textTransform: 'uppercase',
        color: fg, border: `1px solid ${fg}`,
      }}
    >
      {children}
    </span>
  );
}

/* ---------------------------------------------------------------- dinheiro e ficha */

export function Money({ value, size = 18 }: { value: number; size?: number }) {
  return <span className="mono" style={{ fontSize: size, fontWeight: 500 }}>{brl(value)}</span>;
}

/** Os números grandes do card e do topo do produto. Valor e unidade em tamanhos diferentes. */
export function Highlights({ items }: { items: ProductHighlight[] }) {
  if (!items.length) return null;
  return (
    <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
      {items.map((h, i) => (
        <div key={i} style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <span className="mono" style={{ fontSize: 24, fontWeight: 600, lineHeight: 1.1 }}>
            {h.value}
          </span>
          {h.unit ? (
            <span style={{ fontSize: 12, color: 'var(--ink3)' }}>{h.unit}</span>
          ) : null}
        </div>
      ))}
    </div>
  );
}

export function SpecTable({ specs }: { specs: ProductSpec[] }) {
  if (!specs.length) {
    return <p style={{ color: 'var(--ink3)', fontSize: 14 }}>Ficha técnica não informada.</p>;
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {specs.map((s, i) => (
        <div
          key={i}
          style={{
            display: 'flex', justifyContent: 'space-between', gap: 24, padding: '11px 0',
            borderBottom: i === specs.length - 1 ? 'none' : '1px solid var(--line)',
          }}
        >
          <span style={{ fontSize: 13, color: 'var(--ink2)' }}>{s.label}</span>
          <span className="mono" style={{ fontSize: 13, textAlign: 'right' }}>{s.value}</span>
        </div>
      ))}
    </div>
  );
}

/* ---------------------------------------------------------------- frete */

/**
 * A linha de frete, e a distinção que o backend grava em `shipping_distance_km`.
 *
 * Distância nula significa que a consulta de CEP falhou e valeu a tarifa fixa. As duas linhas
 * NÃO podem parecer iguais: cobrar por estimativa e cobrar por medição são coisas diferentes
 * para quem contesta o valor.
 */
export function FreightLine({
  cost, distanceKm,
}: { cost: number; distanceKm: number | null }) {
  const estimated = distanceKm === null;
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
        <span style={{ fontSize: 14, color: 'var(--ink2)' }}>Frete</span>
        <span className="mono" style={{ fontSize: 11, color: estimated ? 'var(--accent)' : 'var(--accent2)' }}>
          {estimated ? 'estimado · tarifa fixa' : `medido · ${km(distanceKm)}`}
        </span>
      </div>
      <span className="mono" style={{ fontSize: 15 }}>
        {estimated ? '~ ' : ''}{brl(cost)}
      </span>
    </div>
  );
}

/* ---------------------------------------------------------------- reserva */

/**
 * Contagem regressiva da reserva de estoque.
 *
 * O backend grava 30 min no checkout e estica para 24 h quando a cobrança abre, então o
 * formato muda com a escala: minutos numa, horas na outra. Fica vermelho nos últimos 5 min.
 */
export function Countdown({ until, onEnd }: { until: string | number; onEnd?: () => void }) {
  /*
   * Recebe o INSTANTE do prazo (`orders.expires_at`), não "quantos segundos faltam".
   *
   * É o que mantém o render puro. Para contar a partir de uma duração, o componente teria que
   * ler o relógio durante o render só para descobrir de onde contar — e render que lê relógio
   * não é idempotente: dois renders no mesmo estado dariam prazos diferentes. Com o instante
   * vindo de fora, o único estado é "que horas são agora", e quem lê o relógio é o intervalo.
   */
  const alvo = useMemo(() => new Date(until).getTime(), [until]);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [alvo]);

  const left = Math.max(0, Math.round((alvo - now) / 1000));
  const acabou = left === 0;

  useEffect(() => {
    if (acabou) onEnd?.();
  }, [acabou, onEnd]);

  const urgent = left > 0 && left < 300;
  return (
    <span
      className="mono"
      style={{ fontSize: 20, fontWeight: 500, color: urgent ? 'var(--danger)' : 'var(--ink)' }}
    >
      {left >= 3600 ? hhmmss(left) : mmss(left)}
    </span>
  );
}

/* ---------------------------------------------------------------- estados */

export function Spinner({ size = 18 }: { size?: number }) {
  return (
    <span
      aria-label="carregando"
      style={{
        display: 'inline-block', width: size, height: size, borderRadius: '50%',
        border: '2px solid var(--line)', borderTopColor: 'var(--ink)',
        animation: 'spin .8s linear infinite',
      }}
    />
  );
}

export function Empty({ title, hint }: { title: string; hint?: string }) {
  return (
    <div style={{ padding: '56px 20px', textAlign: 'center' }}>
      <p style={{ fontSize: 16, fontWeight: 500, margin: 0 }}>{title}</p>
      {hint ? <p style={{ fontSize: 14, color: 'var(--ink3)', marginTop: 8 }}>{hint}</p> : null}
    </div>
  );
}

/** Aviso bloqueante — o banner de CEP ausente no painel usa `tone="danger"`. */
export function Banner({
  tone = 'accent', title, children, action,
}: {
  tone?: 'accent' | 'danger';
  title: string;
  children?: React.ReactNode;
  action?: React.ReactNode;
}) {
  const fg = tone === 'danger' ? 'var(--danger)' : 'var(--accent)';
  const bg = tone === 'danger' ? 'var(--danger-soft)' : 'var(--accent-soft)';
  return (
    <div
      style={{
        display: 'flex', gap: 18, alignItems: 'flex-start',
        background: bg, border: `1px solid ${fg}`, borderRadius: 'var(--radius)', padding: '18px 20px',
      }}
    >
      <div style={{ flexGrow: 1 }}>
        <p style={{ margin: 0, fontSize: 16, fontWeight: 600, color: fg }}>{title}</p>
        {children ? (
          <div style={{ marginTop: 6, fontSize: 14, lineHeight: 1.6, color: 'var(--ink2)' }}>
            {children}
          </div>
        ) : null}
      </div>
      {action}
    </div>
  );
}

/* ---------------------------------------------------------------- formulário */

export function Field({
  label, hint, error, children,
}: { label: string; hint?: string; error?: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span style={{ fontSize: 13, color: 'var(--ink2)' }}>{label}</span>
      {children}
      {error ? <span style={{ fontSize: 12, color: 'var(--danger)' }}>{error}</span> : null}
      {!error && hint ? <span style={{ fontSize: 12, color: 'var(--ink3)' }}>{hint}</span> : null}
    </label>
  );
}

export function Input(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...props}
      style={{
        height: 44, padding: '0 12px', borderRadius: 8,
        border: '1px solid var(--line)', background: 'var(--surface)', color: 'var(--ink)',
        ...props.style,
      }}
    />
  );
}

/* ---------------------------------------------------------------- tema */

const TEMA_MUDOU = 'volt:tema';

/**
 * O tema é estado do DOM, não do React: o script inline do `<head>` já o aplicou antes da
 * primeira pintura. Ler por `useSyncExternalStore` em vez de copiar para dentro de um estado
 * no efeito evita o lampejo e a cascata de render — e de brinde faz o botão reagir quando o
 * sistema operacional muda de tema no meio da sessão, que a versão anterior ignorava.
 */
function assinarTema(aviso: () => void) {
  const mq = window.matchMedia('(prefers-color-scheme: dark)');
  mq.addEventListener('change', aviso);
  window.addEventListener(TEMA_MUDOU, aviso);
  return () => {
    mq.removeEventListener('change', aviso);
    window.removeEventListener(TEMA_MUDOU, aviso);
  };
}

const lerTema = () => {
  const escolhido = document.documentElement.dataset.theme;
  return escolhido
    ? escolhido === 'dark'
    : window.matchMedia('(prefers-color-scheme: dark)').matches;
};

/** No servidor não há DOM nem preferência; o claro é o padrão do `:root`. */
const temaDoServidor = () => false;

export function ThemeToggle() {
  const dark = useSyncExternalStore(assinarTema, lerTema, temaDoServidor);

  const toggle = () => {
    const next = dark ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
    try { localStorage.setItem('volt.theme', next); } catch { /* sessão só desta aba */ }
    window.dispatchEvent(new Event(TEMA_MUDOU));
  };

  return (
    <button
      onClick={toggle}
      aria-label={dark ? 'Usar tema claro' : 'Usar tema escuro'}
      style={{
        height: 36, padding: '0 12px', borderRadius: 8, cursor: 'pointer', fontSize: 13,
        background: 'transparent', color: 'var(--ink3)', border: '1px solid var(--line)',
      }}
    >
      {dark ? 'claro' : 'escuro'}
    </button>
  );
}
