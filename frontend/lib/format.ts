/** Formatação pt-BR. Um lugar só, para preço não sair de dois jeitos em telas diferentes. */

export const brl = (n: number) =>
  'R$ ' + n.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Distância do frete medido: "17,3 km". */
export const km = (n: number) =>
  n.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + ' km';

export const dateTime = (iso: string) =>
  new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit',
  });

export const date = (iso: string) =>
  new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' });

/** "28:14" para a reserva de 30 min. */
export const mmss = (totalSeconds: number) => {
  const s = Math.max(0, Math.floor(totalSeconds));
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
};

/** "23:47:02" para a janela de 24 h que a cobrança aberta compra. */
export const hhmmss = (totalSeconds: number) => {
  const s = Math.max(0, Math.floor(totalSeconds));
  return [Math.floor(s / 3600), Math.floor((s % 3600) / 60), s % 60]
    .map((v) => String(v).padStart(2, '0'))
    .join(':');
};

/**
 * Segundos até `expiresAt`, ou null quando o pedido não tem reserva correndo.
 *
 * A varredura do backend mantém a data depois de expirar — é assim que ela distingue pedido
 * abandonado de cancelado à mão. Então data no passado significa reserva vencida, não ausente.
 */
export const secondsUntil = (iso: string | null): number | null => {
  if (!iso) return null;
  return Math.max(0, Math.floor((new Date(iso).getTime() - Date.now()) / 1000));
};

/** CEP como se digita, a partir dos 8 dígitos que o backend guarda. */
export const cep = (digits: string) =>
  digits.length === 8 ? `${digits.slice(0, 5)}-${digits.slice(5)}` : digits;

export const onlyDigits = (s: string) => s.replace(/\D/g, '');
