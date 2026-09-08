'use client';

/**
 * CPF e telefone — os dois campos que decidem o `canCheckout`.
 *
 * Mora aqui, e não dentro da tela de conta, porque o checkout precisa exatamente do mesmo
 * formulário: sem esses dados o backend recusa com 403 `CHECKOUT_BLOCKED`, e a saída é pedir
 * antes de tentar, não depois de falhar.
 */

import { useState } from 'react';
import { ApiError } from '@/lib/api';
import { ERROR_CODES } from '@/lib/errors';
import { onlyDigits } from '@/lib/format';
import { useApi, useAuth } from '@/lib/auth';
import { Button, Card, Field, Input, Spinner } from '@/components/ui';
import {
  cpfValido, mascaraCpf, mascaraTelefone, telefoneE164, telefoneLegivel,
} from './validacao';

export function PerfilForm({
  titulo = 'Seus dados',
  descricao,
  rotuloBotao = 'Salvar',
  onSalvo,
}: {
  titulo?: string;
  descricao?: string;
  rotuloBotao?: string;
  onSalvo?: () => void;
}) {
  const { user, refresh } = useAuth();
  const call = useApi();

  const [cpf, setCpf] = useState(mascaraCpf(user?.cpf ?? ''));
  const [telefone, setTelefone] = useState(telefoneLegivel(user?.phone ?? null));
  const [erros, setErros] = useState<{ cpf?: string; phone?: string }>({});
  const [falha, setFalha] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);
  const [salvo, setSalvo] = useState(false);

  const salvar = async () => {
    const digitosCpf = onlyDigits(cpf);
    const e164 = telefoneE164(telefone);

    // Validação local com as mesmas regras do servidor: 11 dígitos com DV correto, e um
    // telefone que caiba no E.164. Mandar sabendo que vai voltar 400 é desperdiçar a viagem.
    const locais: { cpf?: string; phone?: string } = {};
    if (!cpfValido(digitosCpf)) locais.cpf = 'CPF inválido. Confira os 11 dígitos.';
    if (!e164) locais.phone = 'Telefone inválido. Use DDD + número.';
    setErros(locais);
    if (locais.cpf || locais.phone) return;

    setSalvando(true);
    setFalha(null);
    try {
      await call('/users/me', { method: 'PATCH', body: { cpf: digitosCpf, phone: e164 } });
      await refresh(); // é o `refresh` que faz o `canCheckout` virar verdadeiro aqui na tela
      setSalvo(true);
      onSalvo?.();
    } catch (e) {
      const api = e instanceof ApiError ? e : null;
      if (api?.is(ERROR_CODES.CPF_ALREADY_USED)) {
        setErros({ cpf: 'Este CPF já está em uso por outra conta.' });
      } else if (api?.fieldErrors.length) {
        // O backend responde `errors` por campo em 400: aproveitamos a mensagem dele.
        const mapa: { cpf?: string; phone?: string } = {};
        for (const f of api.fieldErrors) {
          if (f.field === 'cpf') mapa.cpf = f.message;
          if (f.field === 'phone') mapa.phone = f.message;
        }
        setErros(mapa);
        if (!mapa.cpf && !mapa.phone) setFalha('Revise os dados e tente de novo.');
      } else {
        setFalha('Não conseguimos salvar agora. Tente de novo.');
      }
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Card style={{ padding: 22, display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div>
        <h2 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>{titulo}</h2>
        {descricao ? (
          <p style={{ margin: '6px 0 0', fontSize: 14, color: 'var(--ink2)', lineHeight: 1.6 }}>
            {descricao}
          </p>
        ) : null}
      </div>

      <div style={{ display: 'grid', gap: 14, gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
        <Field label="CPF" error={erros.cpf} hint="Só números. Vai na nota fiscal do pedido.">
          <Input
            className="mono"
            inputMode="numeric"
            autoComplete="off"
            placeholder="000.000.000-00"
            value={cpf}
            onChange={(e) => { setCpf(mascaraCpf(e.target.value)); setSalvo(false); }}
          />
        </Field>

        <Field label="Telefone" error={erros.phone} hint="DDD + número, para falarmos sobre a entrega.">
          <Input
            className="mono"
            inputMode="tel"
            autoComplete="tel-national"
            placeholder="(11) 99999-9999"
            value={telefone}
            onChange={(e) => { setTelefone(mascaraTelefone(e.target.value)); setSalvo(false); }}
          />
        </Field>
      </div>

      {falha ? <p style={{ margin: 0, fontSize: 13, color: 'var(--danger)' }}>{falha}</p> : null}

      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <Button onClick={salvar} disabled={salvando}>
          {salvando ? <Spinner size={14} /> : null}
          {rotuloBotao}
        </Button>
        {salvo && !salvando ? (
          <span style={{ fontSize: 13, color: 'var(--ok)' }}>Dados salvos.</span>
        ) : null}
      </div>
    </Card>
  );
}
