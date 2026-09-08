/**
 * Validação de formato do que o backend recusa com 400.
 *
 * A regra é validar aqui exatamente o que o servidor valida lá — nem mais, nem menos.
 * Menos, e o usuário leva 400 depois de preencher tudo. Mais, e a tela recusa dado que o
 * servidor aceitaria.
 */

import { onlyDigits } from '@/lib/format';

/* ------------------------------------------------------------------ CPF */

/**
 * 11 dígitos e dígitos verificadores corretos — o backend confere os dois (`400` quando falha),
 * então conferir só o comprimento aqui deixaria passar erro de digitação.
 */
export function cpfValido(cpf: string): boolean {
  const d = onlyDigits(cpf);
  if (d.length !== 11) return false;
  // Sequências iguais passam no cálculo do DV mas nenhuma é CPF real.
  if (/^(\d)\1{10}$/.test(d)) return false;

  const digito = (ate: number) => {
    let soma = 0;
    for (let i = 0; i < ate; i++) soma += Number(d[i]) * (ate + 1 - i);
    const resto = (soma * 10) % 11;
    return resto === 10 ? 0 : resto;
  };

  return digito(9) === Number(d[9]) && digito(10) === Number(d[10]);
}

export const mascaraCpf = (v: string) => {
  const d = onlyDigits(v).slice(0, 11);
  return d
    .replace(/^(\d{3})(\d)/, '$1.$2')
    .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d{1,2})$/, '.$1-$2');
};

/* ------------------------------------------------------------------ telefone */

/**
 * Normaliza para E.164 (`+5511999999999`), que é a forma do exemplo do contrato.
 *
 * Aceita o número como se digita no Brasil e acrescenta o `+55`, porque exigir que o usuário
 * digite código de país é transformar formato interno em trabalho dele.
 */
export function telefoneE164(bruto: string): string | null {
  const d = onlyDigits(bruto);
  if (d.length === 10 || d.length === 11) return `+55${d}`;
  if ((d.length === 12 || d.length === 13) && d.startsWith('55')) return `+${d}`;
  return null;
}

export const mascaraTelefone = (v: string) => {
  let d = onlyDigits(v);
  if (d.length > 11 && d.startsWith('55')) d = d.slice(2); // colado com código de país
  d = d.slice(0, 11);
  if (d.length <= 2) return d;
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  const corte = d.length === 11 ? 7 : 6;
  return `(${d.slice(0, 2)}) ${d.slice(2, corte)}-${d.slice(corte)}`;
};

/** Como mostrar de volta o `+5511999999999` que o backend guarda. */
export const telefoneLegivel = (e164: string | null) =>
  e164 ? mascaraTelefone(e164) : '';

/* ------------------------------------------------------------------ endereço */

/** O backend recusa CEP fora de 8 dígitos. Guardamos e enviamos só os dígitos. */
export const cepValido = (v: string) => onlyDigits(v).length === 8;

export const mascaraCep = (v: string) => {
  const d = onlyDigits(v).slice(0, 8);
  return d.length > 5 ? `${d.slice(0, 5)}-${d.slice(5)}` : d;
};

/** `state` tem que ter 2 letras maiúsculas — a normalização evita um 400 por "sp". */
export const normalizaUf = (v: string) =>
  v.replace(/[^a-zA-Z]/g, '').slice(0, 2).toUpperCase();

export const ufValida = (v: string) => /^[A-Z]{2}$/.test(v);
