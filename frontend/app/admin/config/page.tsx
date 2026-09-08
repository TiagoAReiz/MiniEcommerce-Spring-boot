'use client';

/**
 * Configurações — na prática, o CEP de origem do frete.
 *
 * Esta tela é a instalação da loja. Enquanto a origem for nula o backend recusa todo checkout
 * com 409 `SHIPPING_ORIGIN_NOT_CONFIGURED`, e é o único ajuste do painel que decide se existe
 * venda. Por isso ela não repete o `OriginBanner`: o banner manda vir para cá, e aqui o estado
 * é a própria tela, com a explicação inteira em vez de um resumo com botão.
 *
 * O `PUT` devolve a origem já gravada, e `apply` empurra o valor para o contexto — o aviso
 * some das outras telas na hora, sem recarregar nada.
 */

import { useState } from 'react';
import { useApi } from '@/lib/auth';
import { cep, onlyDigits } from '@/lib/format';
import type { ShippingOrigin } from '@/lib/types';
import { Button, Card, Field, Input } from '@/components/ui';
import { useOrigin, useOriginZip } from '@/components/admin/origin';
import { ErrorNote, OkNote, PageHead, Section } from '@/components/admin/primitives';

/** O mesmo padrão que o backend valida: 8 dígitos, com ou sem hífen. */
const PADRAO = /^[0-9]{5}-?[0-9]{3}$/;

export default function ConfigPage() {
  const call = useApi();
  const { apply } = useOrigin();
  const cepAtual = useOriginZip();

  // O campo nasce com o que está gravado. Não precisa de efeito para isso: `AdminGate` só
  // renderiza esta página depois que a origem já foi lida, então o valor existe no 1º render.
  const [valor, setValor] = useState(() => cepAtual ?? '');
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<unknown>(null);
  const [invalido, setInvalido] = useState<string | null>(null);
  const [salvo, setSalvo] = useState(false);

  async function salvar(e: React.FormEvent) {
    e.preventDefault();
    setSalvo(false);
    setErro(null);

    const digitos = onlyDigits(valor);
    if (!PADRAO.test(valor.trim()) || digitos.length !== 8) {
      setInvalido('O CEP tem 8 dígitos. Pode digitar com ou sem hífen.');
      return;
    }
    setInvalido(null);

    setSalvando(true);
    try {
      // Vai normalizado: o backend aceita as duas formas e grava 8 dígitos de qualquer jeito.
      const origem = await call<ShippingOrigin>('/owners/origin', {
        method: 'PUT',
        body: { zipCode: digitos },
      });
      const gravado = origem?.zipCode ?? digitos;
      apply(gravado);
      setValor(cep(gravado));
      setSalvo(true);
    } catch (e) {
      setErro(e);
    } finally {
      setSalvando(false);
    }
  }

  const configurado = cepAtual !== null;

  return (
    <>
      <PageHead
        title="Configurações"
        hint="O endereço de onde as encomendas saem. É o que faz o frete existir."
      />

      <div style={{ display: 'flex', flexDirection: 'column', gap: 32, maxWidth: 680 }}>
        {/* O estado, antes do formulário: quem chega aqui pelo banner quer saber o que mudou. */}
        <Card
          style={{
            padding: '18px 20px',
            borderColor: configurado ? 'var(--ok)' : 'var(--danger)',
          }}
        >
          <p
            style={{
              margin: 0, fontSize: 16, fontWeight: 600,
              color: configurado ? 'var(--ok)' : 'var(--danger)',
            }}
          >
            {configurado ? 'Origem configurada' : 'Origem não configurada'}
          </p>
          <div style={{ marginTop: 6, fontSize: 14, lineHeight: 1.7, color: 'var(--ink2)' }}>
            {configurado ? (
              <>
                As encomendas saem do CEP <span className="mono">{cepAtual}</span>, e o frete de
                cada pedido é a distância daqui até o CEP do cliente.
              </>
            ) : (
              <>
                Enquanto este campo estiver vazio, <strong>a loja não vende</strong>: todo{' '}
                <span className="mono">POST /orders</span> é recusado com{' '}
                <span className="mono">409 SHIPPING_ORIGIN_NOT_CONFIGURED</span>. O catálogo abre,
                o carrinho enche, e o erro aparece só para o cliente, na última tela.
              </>
            )}
          </div>
        </Card>

        <Section
          title="CEP de origem"
          hint="Onde o estoque fica. Mudou de galpão, muda aqui — não é variável de ambiente nem redeploy."
        >
          <form onSubmit={salvar} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <Field
              label="CEP"
              error={invalido ?? undefined}
              hint="Aceita com ou sem hífen: 01310-100 ou 01310100. É gravado em 8 dígitos."
            >
              <Input
                value={valor}
                onChange={(e) => {
                  // Só dígito e hífen entram: o resto é ruído que o backend recusaria com 400.
                  const limpo = e.target.value.replace(/[^0-9-]/g, '').slice(0, 9);
                  setValor(limpo);
                  setInvalido(null);
                  setSalvo(false);
                }}
                onBlur={() => {
                  const d = onlyDigits(valor);
                  if (d.length === 8) setValor(cep(d));
                }}
                inputMode="numeric"
                autoComplete="postal-code"
                placeholder="01310-100"
                className="mono"
                disabled={salvando}
                style={{ maxWidth: 200 }}
              />
            </Field>

            <ErrorNote error={erro} />
            {salvo ? (
              <OkNote>
                Origem gravada. O cálculo do frete já vale para o próximo checkout, e o aviso de
                loja parada sumiu das outras telas.
              </OkNote>
            ) : null}

            <div>
              <Button type="submit" disabled={salvando}>
                {salvando ? 'Salvando…' : configurado ? 'Atualizar origem' : 'Definir origem'}
              </Button>
            </div>
          </form>
        </Section>

        <Section title="O que muda quando você salva">
          <ul
            style={{
              margin: 0, paddingLeft: 20, display: 'flex', flexDirection: 'column', gap: 10,
              fontSize: 14, lineHeight: 1.7, color: 'var(--ink2)',
            }}
          >
            <li>
              <strong>Só os pedidos futuros são reprecificados.</strong> A cotação de um pedido
              já feito está congelada em <span className="mono">shipping_cost</span> e nunca é
              recalculada — o cliente concordou com aquele valor, e mudar de cidade não pode
              fazer o valor devido andar sozinho depois do aceite.
            </li>
            <li>
              <strong>Não existe como desligar o frete depois.</strong> Não há rota que devolva a
              origem a vazio: definida uma vez, ela muda de valor, nunca volta a ser nula. Isso é
              decisão de produto, não limitação — não existe modo &ldquo;loja sem entrega&rdquo;
              neste sistema, e um botão de desligar seria um jeito silencioso de parar de vender.
            </li>
            <li>
              Quando a consulta de CEP falha na hora do checkout, o pedido não é recusado nem sai
              de graça: vale a tarifa fixa de contingência, e o pedido fica com{' '}
              <span className="mono">shippingDistanceKm</span> nulo. Na tela de pedidos essa linha
              aparece como <em>estimado</em>, nunca como <em>medido</em>.
            </li>
          </ul>
        </Section>
      </div>
    </>
  );
}
