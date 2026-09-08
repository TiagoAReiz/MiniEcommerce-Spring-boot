/**
 * Cliente do backend Spring.
 *
 * A API autentica por header `Authorization: Bearer`, nunca por cookie — é por isso que o
 * CSRF está desligado lá. Consequência aqui: toda chamada autenticada roda no navegador, em
 * Client Component. Server Components só podem buscar o que é público (catálogo e produto),
 * que por sorte é exatamente onde SEO importa.
 */

import type { ApiErrorBody } from './errors';

export const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE ?? 'http://localhost:8080';

/**
 * Erro carregando o que o backend devolveu em `application/problem+json`.
 *
 * `code` é o campo estável do contrato (`EMPTY_CART`, `PRICE_CHANGED`,
 * `SHIPPING_ORIGIN_NOT_CONFIGURED`…). É nele que a interface reage, nunca no `detail`, que é
 * texto para humano e pode mudar sem aviso.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string | null;
  readonly detail: string | null;
  readonly fieldErrors: { field: string; message: string }[];

  constructor(status: number, body: ApiErrorBody | null) {
    super(body?.detail || body?.title || `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = body?.code ?? null;
    this.detail = body?.detail ?? null;
    this.fieldErrors = body?.errors ?? [];
  }

  is(code: string) {
    return this.code === code;
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  token?: string | null;
  /** Só para Server Components: repassado ao fetch do Next. */
  revalidate?: number;
  signal?: AbortSignal;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, token, revalidate, signal } = options;

  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
    ...(revalidate === undefined ? {} : { next: { revalidate } }),
  });

  if (!res.ok) {
    throw new ApiError(res.status, await problemBody(res));
  }

  // 204, e também o 201 de rotas que só devolvem Location.
  if (res.status === 204 || res.headers.get('content-length') === '0') {
    return undefined as T;
  }

  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/**
 * O corpo de erro pode não vir, ou não ser JSON — um 502 do proxy é HTML. Engolir a falha de
 * parse aqui é de propósito: o status já basta para a interface reagir, e estourar aqui
 * trocaria "502 do gateway" por "SyntaxError", que é bem pior de depurar.
 */
async function problemBody(res: Response): Promise<ApiErrorBody | null> {
  try {
    return (await res.json()) as ApiErrorBody;
  } catch {
    return null;
  }
}

/**
 * O `Location` das rotas de criação. O backend expõe esse header no CORS de propósito —
 * sem ele o front não consegue ler o id do que acabou de criar.
 */
export function idFromLocation(location: string | null): string | null {
  if (!location) return null;
  const parts = location.split('/').filter(Boolean);
  return parts.length ? parts[parts.length - 1] : null;
}
