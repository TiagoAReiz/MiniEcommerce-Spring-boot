'use client';
import { MAX_HIGHLIGHTS, MAX_SPECS } from './contract';

/**
 * Peças que só o painel usa, e as que faltam em `components/ui.tsx`.
 *
 * `ui.tsx` é da fundação e tem `Input`, mas não `Select` nem `Textarea` — e o formulário de
 * produto precisa dos dois. Ficam aqui em vez de lá para não disputar o arquivo com os
 * outros agentes; se subirem para `ui.tsx`, este arquivo encolhe.
 */

import { ApiError } from '@/lib/api';
import { ERROR_MESSAGES } from '@/lib/errors';

/* ---------------------------------------------------------------- cabeçalho */

export function PageHead({
  title, hint, action,
}: { title: string; hint?: string; action?: React.ReactNode }) {
  return (
    <div
      style={{
        display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between',
        gap: 20, flexWrap: 'wrap', marginBottom: 22,
      }}
    >
      <div>
        <h1 style={{ margin: 0, fontSize: 26, fontWeight: 600, letterSpacing: '-.01em' }}>
          {title}
        </h1>
        {hint ? (
          <p style={{ margin: '6px 0 0', fontSize: 14, color: 'var(--ink3)', maxWidth: 640, lineHeight: 1.6 }}>
            {hint}
          </p>
        ) : null}
      </div>
      {action}
    </div>
  );
}

export function Section({
  title, hint, children, style,
}: { title: string; hint?: string; children: React.ReactNode; style?: React.CSSProperties }) {
  return (
    <section style={{ display: 'flex', flexDirection: 'column', gap: 12, ...style }}>
      <div>
        <h2 style={{ margin: 0, fontSize: 15, fontWeight: 600 }}>{title}</h2>
        {hint ? (
          <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--ink3)', lineHeight: 1.6 }}>
            {hint}
          </p>
        ) : null}
      </div>
      {children}
    </section>
  );
}

/* ---------------------------------------------------------------- formulário */

export function Select(props: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      {...props}
      style={{
        height: 44, padding: '0 10px', borderRadius: 8,
        border: '1px solid var(--line)', background: 'var(--surface)', color: 'var(--ink)',
        cursor: 'pointer',
        ...props.style,
      }}
    />
  );
}

export function Textarea(props: React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      {...props}
      style={{
        minHeight: 92, padding: '10px 12px', borderRadius: 8, lineHeight: 1.6, resize: 'vertical',
        border: '1px solid var(--line)', background: 'var(--surface)', color: 'var(--ink)',
        ...props.style,
      }}
    />
  );
}

/**
 * Botão quadrado das linhas da ficha (mover, remover).
 *
 * 44px mesmo sendo só um símbolo: são os alvos mais clicados do formulário, e reordenar
 * ficha técnica com alvo de 28px acerta a linha vizinha.
 */
export function IconBtn({
  glyph, label, onClick, disabled, tone = 'neutral',
}: {
  glyph: string;
  label: string;
  onClick: () => void;
  disabled?: boolean;
  tone?: 'neutral' | 'danger';
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      title={label}
      style={{
        width: 44, height: 44, flexShrink: 0, borderRadius: 8,
        cursor: disabled ? 'not-allowed' : 'pointer',
        background: 'transparent', border: '1px solid var(--line)',
        color: disabled ? 'var(--ink3)' : tone === 'danger' ? 'var(--danger)' : 'var(--ink2)',
        opacity: disabled ? 0.45 : 1, fontSize: 15, lineHeight: 1,
      }}
    >
      {glyph}
    </button>
  );
}

/* ---------------------------------------------------------------- estados */

/**
 * O erro da API em português, escolhido pelo `code` e não pelo `detail`.
 *
 * `detail` é texto para humano e muda sem aviso; ainda assim é melhor que "HTTP 409" quando
 * o `code` não tem tradução, então entra como último recurso antes do genérico.
 */
export function errorText(e: unknown): string {
  if (!(e instanceof ApiError)) {
    return 'Não foi possível falar com o servidor. Verifique a conexão e tente de novo.';
  }
  if (e.fieldErrors.length) {
    return e.fieldErrors.map((f) => `${f.field}: ${f.message}`).join(' · ');
  }
  if (e.code && ERROR_MESSAGES[e.code]) return ERROR_MESSAGES[e.code];
  if (e.code === 'INVALID_STATUS_TRANSITION') {
    return 'O pedido já saiu desse estado. Recarregue a lista para ver a situação atual.';
  }
  if (e.code === 'ORDER_NOT_PAID') {
    return 'Só dá para criar o envio depois que o pagamento for confirmado.';
  }
  if (e.code === 'SHIPMENT_ALREADY_EXISTS') return 'Este pedido já tem um envio criado.';
  if (e.status === 403) return 'Sua conta não tem permissão de dono para esta ação.';
  if (e.status === 404) return 'Não encontrado. Pode ter sido alterado em outra aba.';
  if (e.status === 500) {
    return 'O servidor falhou. Se for a origem do frete, a linha da loja pode não ter sido semeada.';
  }
  return e.detail || `Falhou com HTTP ${e.status}.`;
}

export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null;
  return (
    <p
      role="alert"
      style={{
        margin: 0, fontSize: 13, lineHeight: 1.6, color: 'var(--danger)',
        background: 'var(--danger-soft)', border: '1px solid var(--danger)',
        borderRadius: 8, padding: '10px 12px',
      }}
    >
      {errorText(error)}
    </p>
  );
}

export function OkNote({ children }: { children: React.ReactNode }) {
  return (
    <p
      role="status"
      style={{
        margin: 0, fontSize: 13, lineHeight: 1.6, color: 'var(--ok)',
        border: '1px solid var(--ok)', borderRadius: 8, padding: '10px 12px',
      }}
    >
      {children}
    </p>
  );
}

/* ---------------------------------------------------------------- paginação */

export function Pager({
  page, totalPages, onChange,
}: { page: number; totalPages: number; onChange: (p: number) => void }) {
  if (totalPages <= 1) return null;
  const btn: React.CSSProperties = {
    height: 44, padding: '0 16px', borderRadius: 8, cursor: 'pointer', fontSize: 14,
    background: 'transparent', color: 'var(--ink2)', border: '1px solid var(--line)',
  };
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, justifyContent: 'center', padding: '18px 0' }}>
      <button type="button" style={btn} disabled={page <= 0} onClick={() => onChange(page - 1)}>
        Anterior
      </button>
      <span className="mono" style={{ fontSize: 13, color: 'var(--ink3)' }}>
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        style={btn}
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
      >
        Próxima
      </button>
    </div>
  );
}

/* ---------------------------------------------------------------- números */

export function Stat({
  label, value, tone = 'neutral', hint,
}: { label: string; value: string; tone?: 'neutral' | 'ok' | 'danger' | 'accent'; hint?: string }) {
  const fg = {
    neutral: 'var(--ink)', ok: 'var(--ok)', danger: 'var(--danger)', accent: 'var(--accent)',
  }[tone];
  return (
    <div
      style={{
        background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 'var(--radius)',
        padding: '16px 18px', display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0,
      }}
    >
      <span style={{ fontSize: 12, color: 'var(--ink3)' }}>{label}</span>
      <span className="mono" style={{ fontSize: 24, fontWeight: 600, color: fg, lineHeight: 1.2 }}>
        {value}
      </span>
      {hint ? <span style={{ fontSize: 12, color: 'var(--ink3)' }}>{hint}</span> : null}
    </div>
  );
}

/**
 * O texto de limite mostrado sob o editor de ficha técnica.
 *
 * Fica aqui, e não no próprio editor, porque os dois números são os que o backend recusa com
 * 400 — quem mexer neles tem que mexer no `contract.ts` junto, e o vizinho é o lembrete.
 */
export const LIMITS_HINT =
  `Até ${MAX_HIGHLIGHTS} destaques e ${MAX_SPECS} linhas de ficha. A ordem é a que o cliente vê.`;
