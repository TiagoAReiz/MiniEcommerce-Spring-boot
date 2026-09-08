'use client';

/**
 * A espera pela confirmação do pagamento.
 *
 * O pedido só vira `PAID` quando o webhook do Mercado Pago chega ao nosso servidor e o
 * pagamento é relido no gateway. O cliente voltar para esta tela não é evidência de nada: o
 * webhook responde 200 na hora e processa depois, então há uma janela real — de segundos,
 * às vezes de minutos — em que o cliente já pagou e o pedido ainda está `PENDING`.
 *
 * Daí a forma deste hook: ele **pergunta**, não conclui. E para de perguntar depois de dois
 * minutos, porque um spinner eterno mente tão bem quanto um "pago" precipitado.
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '@/lib/api';
import { useApi } from '@/lib/auth';
import type { Order, Payment } from '@/lib/types';

/** Ritmo do polling. Mais curto que isto só empilha requisição sem adiantar o webhook. */
export const POLL_MS = 3000;

/** Teto da espera automática. Depois disto a tela oferece "atualizar" e o cliente decide. */
export const TETO_MS = 120_000;

/** Quantas falhas de rede seguidas antes de desistir. Uma sozinha não significa nada. */
const FALHAS_ATE_DESISTIR = 4;

export type Fase =
  /** Primeira leitura, antes de saber qualquer coisa. */
  | 'lendo'
  /** `PENDING`: a cobrança está aberta e o webhook ainda não chegou. */
  | 'processando'
  /** `PAID`, `SHIPPED` ou `DELIVERED`: o webhook chegou e liquidou. */
  | 'confirmado'
  /** `CANCELLED`. Veja `pagamento.paid` — pode ser a divergência descrita abaixo. */
  | 'cancelado'
  /** Estourou o teto (ou a rede caiu) sem desfecho. Nada foi perdido; só paramos de perguntar. */
  | 'parado'
  /** 404. Pedido de outra pessoa responde igual, de propósito. */
  | 'ausente';

export interface PedidoWatch {
  pedido: Order | null;
  /** Só é lido quando o pedido é `CANCELLED`, para separar abandono de divergência. */
  pagamento: Payment | null;
  fase: Fase;
  /** Mensagem de rede/sessão. Não é o mesmo que "deu errado no pagamento". */
  erro: string | null;
  conferindo: boolean;
  /** Uma leitura sob demanda. Não religa o polling — o teto existe para ser respeitado. */
  conferirAgora: () => Promise<void>;
  /** Recomeça a espera do zero. Use depois de abrir uma cobrança nova. */
  reiniciar: () => void;
}

const ERRO_REDE = 'Não conseguimos falar com o servidor agora.';
const ERRO_SESSAO = 'Sua sessão expirou. Entre de novo para acompanhar este pedido.';

export function usePedidoWatch(orderId: string): PedidoWatch {
  const call = useApi();

  const [pedido, setPedido] = useState<Order | null>(null);
  const [pagamento, setPagamento] = useState<Payment | null>(null);
  const [fase, setFase] = useState<Fase>('lendo');
  const [erro, setErro] = useState<string | null>(null);
  const [conferindo, setConferindo] = useState(false);
  const [ciclo, setCiclo] = useState(0);

  // Espelho da fase para quem lê fora do render (o refresh manual, abaixo).
  const faseRef = useRef<Fase>('lendo');
  useEffect(() => {
    faseRef.current = fase;
  }, [fase]);

  /**
   * Uma leitura e sua classificação. Devolve `true` enquanto ainda vale perguntar de novo.
   *
   * Lê o pedido, e não o pagamento: uma requisição só, e ela traz de brinde o `expiresAt`
   * esticado para 24 h e o `paymentId` que a resposta de `POST /payments` não devolve.
   */
  const classificar = useCallback(async (): Promise<boolean> => {
    const atual = await call<Order>(`/orders/${orderId}`);
    setPedido(atual);

    if (atual.status === 'PENDING') {
      setFase('processando');
      return true;
    }

    if (atual.status === 'CANCELLED') {
      // Divergência possível: a varredura devolveu o estoque e o pagamento foi aprovado
      // depois. O cliente pagou por um pedido que não existe mais, e isso tem que aparecer.
      if (atual.paymentId) {
        try {
          setPagamento(await call<Payment>(`/payments/${atual.paymentId}`));
        } catch {
          // Sem o pagamento a tela mostra o caso comum (abandono), que é o mais provável.
        }
      }
      setFase('cancelado');
      return false;
    }

    setFase('confirmado');
    return false;
  }, [call, orderId]);

  useEffect(() => {
    let vivo = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const prazo = Date.now() + TETO_MS;
    let falhas = 0;

    const tick = async () => {
      if (!vivo) return;

      try {
        const seguir = await classificar();
        if (!vivo) return;
        falhas = 0;
        setErro(null);
        if (!seguir) return; // desfecho conhecido: não há mais o que perguntar
      } catch (e) {
        if (!vivo) return;

        if (e instanceof ApiError && e.status === 404) {
          setFase('ausente');
          return;
        }
        if (e instanceof ApiError && e.status === 401) {
          setErro(ERRO_SESSAO);
          setFase('parado');
          return;
        }
        // Rede instável não pode derrubar a espera: o webhook pode chegar no próximo ciclo.
        falhas += 1;
        if (falhas >= FALHAS_ATE_DESISTIR) {
          setErro(ERRO_REDE);
          setFase('parado');
          return;
        }
      }

      if (!vivo) return;
      if (Date.now() >= prazo) {
        setFase('parado');
        return;
      }
      timer = setTimeout(tick, POLL_MS);
    };

    void tick();

    return () => {
      vivo = false;
      if (timer) clearTimeout(timer);
    };
  }, [classificar, ciclo]);

  const conferirAgora = useCallback(async () => {
    const antes = faseRef.current;
    setConferindo(true);
    setErro(null);
    try {
      const seguir = await classificar();
      // Continua pendente e a espera automática já tinha acabado: fica parado, sem voltar a
      // girar sozinho. Quem clicou pediu uma leitura, não um novo relógio.
      if (seguir && antes === 'parado') setFase('parado');
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) setFase('ausente');
      else if (e instanceof ApiError && e.status === 401) setErro(ERRO_SESSAO);
      else setErro(ERRO_REDE);
    } finally {
      setConferindo(false);
    }
  }, [classificar]);

  const reiniciar = useCallback(() => {
    setErro(null);
    setFase('lendo');
    setCiclo((c) => c + 1);
  }, []);

  return { pedido, pagamento, fase, erro, conferindo, conferirAgora, reiniciar };
}
