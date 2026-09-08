'use client';

/**
 * Endereços do cliente: listar, escolher e cadastrar.
 *
 * Não havia peça pronta para isto, e duas telas precisam exatamente da mesma coisa — o
 * checkout, para decidir para onde o pedido vai, e a conta, para manter a lista. Um componente
 * só, com um modo a mais quando há escolha a fazer, em vez de duas listas que divergem.
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '@/lib/api';
import { useApi } from '@/lib/auth';
import { cep as cepLegivel, onlyDigits } from '@/lib/format';
import type { Address } from '@/lib/types';
import { Banner, Button, Card, Field, Input, Spinner, Tag } from '@/components/ui';
import { cepValido, mascaraCep, normalizaUf, ufValida } from './validacao';

/* ------------------------------------------------------------------ leitura */

/** "Avenida Paulista, 1578" — o número é opcional no contrato. */
export const linhaDaRua = (a: Address) =>
  a.streetNumber ? `${a.street}, ${a.streetNumber}` : a.street;

/** "Bela Vista · São Paulo/SP · CEP 01310-100" */
export const linhaDaCidade = (a: Address) =>
  `${a.neighborhood} · ${a.city}/${a.state} · CEP ${cepLegivel(a.zipCode)}`;

function useEnderecos() {
  const call = useApi();
  const [lista, setLista] = useState<Address[] | null>(null);
  const [erro, setErro] = useState<ApiError | null>(null);

  const recarregar = useCallback(async () => {
    try {
      // O backend já devolve o principal primeiro; não reordenamos nada aqui.
      setLista(await call<Address[]>('/users/me/addresses'));
      setErro(null);
    } catch (e) {
      setErro(e instanceof ApiError ? e : new ApiError(0, null));
    }
  }, [call]);

  useEffect(() => {
    let vivo = true;
    call<Address[]>('/users/me/addresses')
      .then((r) => vivo && setLista(r))
      .catch((e) => vivo && setErro(e instanceof ApiError ? e : new ApiError(0, null)));
    return () => {
      vivo = false;
    };
  }, [call]);

  return { lista, erro, recarregar };
}

/* ------------------------------------------------------------------ cartão */

function Corpo({ a }: { a: Address }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 5, textAlign: 'left', minWidth: 0 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
        <span style={{ fontSize: 15, fontWeight: 500, color: 'var(--ink)' }}>{linhaDaRua(a)}</span>
        {a.isPrimary ? <Tag tone="accent">principal</Tag> : null}
      </div>
      <span className="mono" style={{ fontSize: 12, color: 'var(--ink3)' }}>{linhaDaCidade(a)}</span>
    </div>
  );
}

/** Opção de um radiogroup: o teclado precisa distinguir escolher de navegar. */
function Opcao({
  a, escolhido, onEscolher,
}: { a: Address; escolhido: boolean; onEscolher: () => void }) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={escolhido}
      onClick={onEscolher}
      style={{
        display: 'flex', alignItems: 'center', gap: 14, width: '100%', minHeight: 68,
        padding: '14px 18px', cursor: 'pointer', textAlign: 'left',
        background: escolhido ? 'var(--surface2)' : 'var(--surface)',
        border: `1px solid ${escolhido ? 'var(--ink)' : 'var(--line)'}`,
        borderRadius: 'var(--radius)', color: 'var(--ink)',
      }}
    >
      <span
        aria-hidden
        style={{
          width: 18, height: 18, flexShrink: 0, borderRadius: '50%',
          border: `2px solid ${escolhido ? 'var(--ink)' : 'var(--line)'}`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}
      >
        {escolhido ? (
          <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--ink)' }} />
        ) : null}
      </span>
      <Corpo a={a} />
    </button>
  );
}

/* ------------------------------------------------------------------ formulário */

const VAZIO = { zipCode: '', street: '', streetNumber: '', neighborhood: '', city: '', state: '' };
type Campos = typeof VAZIO;
type ErrosCampos = Partial<Record<keyof Campos, string>>;

function FormEndereco({
  primeiro, onCriado, onCancelar,
}: {
  /** Sem nenhum endereço ainda, o novo vira principal sem perguntar. */
  primeiro: boolean;
  onCriado: (a: Address) => Promise<void> | void;
  onCancelar?: () => void;
}) {
  const call = useApi();
  const [campos, setCampos] = useState<Campos>(VAZIO);
  const [principal, setPrincipal] = useState(primeiro);
  const [erros, setErros] = useState<ErrosCampos>({});
  const [falha, setFalha] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  const editar = (k: keyof Campos, v: string) => {
    setCampos((c) => ({ ...c, [k]: v }));
    setErros((e) => ({ ...e, [k]: undefined }));
  };

  const salvar = async () => {
    // As mesmas regras do `AddressRequest`: 8 dígitos de CEP, UF em duas maiúsculas e nada
    // em branco. Descobrir isso só no 400 é fazer o usuário preencher tudo para depois voltar.
    const locais: ErrosCampos = {};
    if (!cepValido(campos.zipCode)) locais.zipCode = 'CEP inválido. São 8 dígitos.';
    if (!campos.street.trim()) locais.street = 'Informe a rua.';
    if (!campos.neighborhood.trim()) locais.neighborhood = 'Informe o bairro.';
    if (!campos.city.trim()) locais.city = 'Informe a cidade.';
    if (!ufValida(campos.state)) locais.state = 'Use a sigla da UF.';
    setErros(locais);
    if (Object.keys(locais).length) return;

    setSalvando(true);
    setFalha(null);
    try {
      const criado = await call<Address>('/users/me/addresses', {
        method: 'POST',
        body: {
          zipCode: onlyDigits(campos.zipCode),
          street: campos.street.trim(),
          // O contrato aceita nulo; string vazia passaria pelo @Size e viraria lixo no banco.
          streetNumber: campos.streetNumber.trim() || null,
          neighborhood: campos.neighborhood.trim(),
          city: campos.city.trim(),
          state: campos.state,
          country: 'BR',
          isPrimary: principal,
        },
      });
      setCampos(VAZIO);
      await onCriado(criado);
    } catch (e) {
      const api = e instanceof ApiError ? e : null;
      if (api?.fieldErrors.length) {
        const mapa: ErrosCampos = {};
        for (const f of api.fieldErrors) {
          if (f.field in VAZIO) mapa[f.field as keyof Campos] = f.message;
        }
        setErros(mapa);
        if (!Object.keys(mapa).length) setFalha('Revise os dados e tente de novo.');
      } else {
        setFalha('Não conseguimos salvar o endereço agora. Tente de novo.');
      }
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Card style={{ padding: 22, display: 'flex', flexDirection: 'column', gap: 16 }}>
      <h3 style={{ margin: 0, fontSize: 15, fontWeight: 600 }}>Novo endereço</h3>

      <div style={{ display: 'grid', gap: 14, gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))' }}>
        <Field label="CEP" error={erros.zipCode} hint="Só os 8 dígitos.">
          <Input
            className="mono"
            inputMode="numeric"
            autoComplete="postal-code"
            placeholder="00000-000"
            value={campos.zipCode}
            onChange={(e) => editar('zipCode', mascaraCep(e.target.value))}
          />
        </Field>

        <Field label="Rua" error={erros.street}>
          <Input
            autoComplete="address-line1"
            placeholder="Avenida Paulista"
            value={campos.street}
            onChange={(e) => editar('street', e.target.value)}
          />
        </Field>

        <Field label="Número" error={erros.streetNumber} hint="Deixe vazio se não houver.">
          <Input
            className="mono"
            autoComplete="address-line2"
            placeholder="1578"
            maxLength={10}
            value={campos.streetNumber}
            onChange={(e) => editar('streetNumber', e.target.value)}
          />
        </Field>

        <Field label="Bairro" error={erros.neighborhood}>
          <Input
            autoComplete="address-level3"
            placeholder="Bela Vista"
            value={campos.neighborhood}
            onChange={(e) => editar('neighborhood', e.target.value)}
          />
        </Field>

        <Field label="Cidade" error={erros.city}>
          <Input
            autoComplete="address-level2"
            placeholder="São Paulo"
            value={campos.city}
            onChange={(e) => editar('city', e.target.value)}
          />
        </Field>

        <Field label="UF" error={erros.state}>
          <Input
            className="mono"
            autoComplete="address-level1"
            placeholder="SP"
            value={campos.state}
            onChange={(e) => editar('state', normalizaUf(e.target.value))}
          />
        </Field>
      </div>

      {/* Com um endereço só, "principal" não é decisão de ninguém: já é o único. */}
      {!primeiro ? (
        <label style={{ display: 'flex', alignItems: 'center', gap: 10, minHeight: 44, fontSize: 14, cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={principal}
            onChange={(e) => setPrincipal(e.target.checked)}
            style={{ width: 18, height: 18, accentColor: 'var(--ink)' }}
          />
          Usar como endereço principal
        </label>
      ) : null}

      {falha ? <p style={{ margin: 0, fontSize: 13, color: 'var(--danger)' }}>{falha}</p> : null}

      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        <Button onClick={salvar} disabled={salvando}>
          {salvando ? <Spinner size={14} /> : null}
          Salvar endereço
        </Button>
        {onCancelar ? (
          <Button variant="ghost" onClick={onCancelar} disabled={salvando}>Cancelar</Button>
        ) : null}
      </div>
    </Card>
  );
}

/* ------------------------------------------------------------------ bloco */

export function Enderecos({
  onEscolher, escolhidoId, titulo = 'Endereços', descricao,
}: {
  /** Presente: os cartões viram opções de escolha. Ausente: é só a lista da conta. */
  onEscolher?: (a: Address) => void;
  escolhidoId?: string | null;
  titulo?: string;
  descricao?: string;
}) {
  const { lista, erro, recarregar } = useEnderecos();
  const [abrindoForm, setAbrindoForm] = useState(false);

  // O checkout não deve pedir um clique para confirmar o óbvio: o principal já vem escolhido.
  // A ref garante que isso aconteça uma vez, e não a cada volta do servidor.
  const jaEscolheu = useRef(false);
  const escolher = useRef(onEscolher);
  useEffect(() => {
    escolher.current = onEscolher;
  }, [onEscolher]);

  useEffect(() => {
    if (jaEscolheu.current || !escolher.current || escolhidoId) return;
    if (!lista || !lista.length) return;
    jaEscolheu.current = true;
    escolher.current(lista[0]);
  }, [lista, escolhidoId]);

  const recemCriado = async (a: Address) => {
    setAbrindoForm(false);
    // Recarrega porque `isPrimary: true` rebaixa o principal anterior no servidor — a lista
    // que está na tela ficaria com dois principais.
    await recarregar();
    escolher.current?.(a);
  };

  if (erro) {
    return (
      <Banner
        tone="danger"
        title="Não conseguimos carregar seus endereços"
        action={<Button variant="ghost" onClick={recarregar}>Tentar de novo</Button>}
      >
        A conexão com a loja falhou. Nada foi perdido — seus endereços continuam salvos.
      </Banner>
    );
  }

  if (!lista) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '24px 0', color: 'var(--ink3)', fontSize: 13 }}>
        <Spinner size={16} /> Carregando endereços…
      </div>
    );
  }

  const vazio = lista.length === 0;
  const mostrarForm = vazio || abrindoForm;

  return (
    <section style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>{titulo}</h2>
          {descricao ? (
            <p style={{ margin: '6px 0 0', fontSize: 14, color: 'var(--ink2)', lineHeight: 1.6 }}>
              {descricao}
            </p>
          ) : null}
        </div>
        {!mostrarForm ? (
          <div style={{ marginLeft: 'auto' }}>
            <Button variant="ghost" onClick={() => setAbrindoForm(true)}>Novo endereço</Button>
          </div>
        ) : null}
      </div>

      {vazio && !onEscolher ? (
        <p style={{ margin: 0, fontSize: 14, color: 'var(--ink3)' }}>
          Você ainda não cadastrou nenhum endereço.
        </p>
      ) : null}

      {!vazio ? (
        <div
          role={onEscolher ? 'radiogroup' : undefined}
          aria-label={onEscolher ? 'Endereço de entrega' : undefined}
          style={{ display: 'flex', flexDirection: 'column', gap: 10 }}
        >
          {lista.map((a) =>
            onEscolher ? (
              <Opcao
                key={a.id}
                a={a}
                escolhido={a.id === escolhidoId}
                onEscolher={() => onEscolher(a)}
              />
            ) : (
              <Card key={a.id} style={{ padding: '14px 18px' }}>
                <Corpo a={a} />
              </Card>
            ),
          )}
        </div>
      ) : null}

      {mostrarForm ? (
        <FormEndereco
          primeiro={vazio}
          onCriado={recemCriado}
          onCancelar={vazio ? undefined : () => setAbrindoForm(false)}
        />
      ) : null}
    </section>
  );
}
