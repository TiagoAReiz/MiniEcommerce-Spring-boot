'use client';

/**
 * Quantidade e "Adicionar ao carrinho".
 *
 * Precisa ser cliente por duas razões independentes: `/cart/items` é autenticado, e a API só
 * aceita `Authorization: Bearer` — token que mora no navegador, nunca no servidor de render.
 *
 * O `quantity` daqui é intenção, não garantia: quem confere estoque é o backend, que responde
 * 409 `INSUFFICIENT_STOCK` quando o número mudou desde que esta página foi gerada.
 */

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui';
import { useApi, useAuth } from '@/lib/auth';
import { ApiError } from '@/lib/api';
import { ERROR_MESSAGES } from '@/lib/errors';
import type { Product } from '@/lib/types';

type Estado = 'parado' | 'enviando' | 'pronto';

export function Comprar({ produto }: { produto: Product }) {
  const { user, loading } = useAuth();
  const call = useApi();
  const router = useRouter();

  const [quantidade, setQuantidade] = useState(1);
  const [estado, setEstado] = useState<Estado>('parado');
  const [erro, setErro] = useState<string | null>(null);

  const maximo = Math.max(1, produto.stock);
  const bloqueado = !produto.sellable;

  const ajustar = (delta: number) => {
    setEstado('parado');
    setQuantidade((q) => Math.min(maximo, Math.max(1, q + delta)));
  };

  const adicionar = async () => {
    if (bloqueado || loading) return;
    // Sem sessão não há carrinho: o backend guarda o carrinho no Redis atrelado ao usuário.
    if (!user) {
      router.push('/conta');
      return;
    }

    setEstado('enviando');
    setErro(null);
    try {
      await call('/cart/items', {
        method: 'POST',
        body: { productId: produto.id, quantity: quantidade },
      });
      setEstado('pronto');
    } catch (e) {
      setEstado('parado');
      setErro(
        e instanceof ApiError
          ? (e.code && ERROR_MESSAGES[e.code]) || e.detail || 'Não foi possível adicionar ao carrinho.'
          : 'Não foi possível adicionar ao carrinho.',
      );
    }
  };

  const passo: React.CSSProperties = {
    width: 44, height: 44, borderRadius: 8, cursor: 'pointer', fontSize: 18,
    background: 'transparent', color: 'var(--ink)', border: '1px solid var(--line)',
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <button
            onClick={() => ajustar(-1)}
            disabled={bloqueado || quantidade <= 1}
            aria-label="Diminuir quantidade"
            style={{ ...passo, opacity: bloqueado || quantidade <= 1 ? 0.4 : 1 }}
          >
            −
          </button>
          <span
            className="mono"
            aria-live="polite"
            style={{ minWidth: 40, textAlign: 'center', fontSize: 16 }}
          >
            {quantidade}
          </span>
          <button
            onClick={() => ajustar(1)}
            disabled={bloqueado || quantidade >= maximo}
            aria-label="Aumentar quantidade"
            style={{ ...passo, opacity: bloqueado || quantidade >= maximo ? 0.4 : 1 }}
          >
            +
          </button>
        </div>

        <Button
          onClick={adicionar}
          disabled={bloqueado || estado === 'enviando'}
          style={{ flexGrow: 1, minWidth: 220 }}
        >
          {bloqueado
            ? 'Sem estoque'
            : estado === 'enviando'
              ? 'Adicionando…'
              : 'Adicionar ao carrinho'}
        </Button>
      </div>

      {estado === 'pronto' ? (
        <p style={{ margin: 0, fontSize: 14, color: 'var(--ok)' }}>
          Adicionado ao carrinho. <Link href="/carrinho">Ver carrinho</Link>
        </p>
      ) : null}

      {erro ? (
        <p role="alert" style={{ margin: 0, fontSize: 14, color: 'var(--danger)' }}>{erro}</p>
      ) : null}

      {bloqueado ? (
        <p style={{ margin: 0, fontSize: 14, color: 'var(--ink2)' }}>
          Este produto está indisponível no momento. Não é possível reservá-lo.
        </p>
      ) : null}
    </div>
  );
}
