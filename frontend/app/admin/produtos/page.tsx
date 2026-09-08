'use client';

/**
 * Produtos: a lista e o formulário de criar/editar.
 *
 * A regra que desenha esta tela inteira: **o `PUT` substitui `highlights` e `specs` por
 * inteiro**. Um `PUT` que as omite não "deixa como estava" — limpa as duas. Então corrigir o
 * preço com um formulário que não carregou a ficha apagaria a ficha, sem erro nenhum na tela.
 *
 * A garantia daqui é uma só, e está em `carregarFicha`: **nenhuma escrita sai sem um
 * `GET /products/{id}` imediatamente antes**. O formulário de edição não é aberto a partir da
 * linha da lista, que pode estar velha; ele é aberto a partir do produto recém-lido. Se essa
 * leitura falhar, o formulário não abre — é melhor não editar do que editar às cegas.
 *
 * `DELETE` não apaga: desativa, e responde 204. `order_items` referencia produto com
 * `RESTRICT`, então apagar destruiria histórico de pedido. O botão diz o que a rota faz.
 */

import { useCallback, useEffect, useState } from 'react';
import { useApi } from '@/lib/auth';
import type { Page, Product, ProductHighlight, ProductSpec } from '@/lib/types';
import { Button, Card, Empty, Field, Input, Money, Spinner, Tag } from '@/components/ui';
import { OriginBanner } from '@/components/admin/origin';
import { LIMITS, type ProductPayload } from '@/components/admin/contract';
import { HighlightsEditor, SpecsEditor } from '@/components/admin/sheet-editor';
import {
  ErrorNote, OkNote, Pager, PageHead, Section, Textarea,
} from '@/components/admin/primitives';

const TAMANHO = 20;

/* ---------------------------------------------------------------- formulário */

interface Rascunho {
  /** Null é produto novo: `POST`. Preenchido é `PUT`, e a ficha abaixo veio do servidor. */
  id: string | null;
  nome: string;
  descricao: string;
  preco: string;
  estoque: string;
  categoria: string;
  highlights: ProductHighlight[];
  specs: ProductSpec[];
  ativo: boolean;
}

const VAZIO: Rascunho = {
  id: null, nome: '', descricao: '', preco: '', estoque: '', categoria: '',
  highlights: [], specs: [], ativo: true,
};

/** O produto lido do servidor vira rascunho — com a ficha inteira, que é o ponto. */
function rascunhoDe(p: Product): Rascunho {
  return {
    id: p.id,
    nome: p.name,
    descricao: p.description ?? '',
    preco: String(p.price),
    estoque: String(p.stock),
    categoria: p.category ?? '',
    highlights: p.highlights.map((h) => ({ value: h.value, unit: h.unit ?? '' })),
    specs: p.specs.map((s) => ({ label: s.label, value: s.value })),
    ativo: p.active,
  };
}

/**
 * Rascunho → corpo da requisição, ou a primeira coisa errada.
 *
 * Linha inteiramente em branco é descartada (é o "adicionar" que o operador não preencheu);
 * linha pela metade vira erro, porque o backend recusaria com 400 e a mensagem dele fala de
 * `highlights[2].value`, não de "o segundo destaque está sem número".
 */
function montar(r: Rascunho): { payload: ProductPayload } | { erro: string } {
  const nome = r.nome.trim();
  if (!nome) return { erro: 'O nome é obrigatório.' };
  if (nome.length > LIMITS.name) return { erro: `O nome passa de ${LIMITS.name} caracteres.` };

  const bruto = r.preco.trim().replace(',', '.');
  const preco = Number(bruto);
  if (!bruto || !Number.isFinite(preco) || preco < 0) {
    return { erro: 'O preço não pode ficar vazio nem negativo.' };
  }
  // Duas casas é o `@Digits(fraction = 2)` do backend; contar no texto digitado evita o
  // arredondamento binário que faria 24,99 parecer ter quinze casas.
  if ((bruto.split('.')[1]?.length ?? 0) > 2) {
    return { erro: 'O preço aceita no máximo duas casas decimais.' };
  }

  // `Number('')` é zero: sem esta guarda, campo vazio viraria estoque zerado em silêncio.
  const estoque = r.estoque.trim() === '' ? NaN : Number(r.estoque);
  if (!Number.isInteger(estoque) || estoque < 0) {
    return { erro: 'O estoque tem que ser um inteiro igual ou maior que zero.' };
  }

  const highlights: ProductHighlight[] = [];
  for (const h of r.highlights) {
    const value = h.value.trim();
    const unit = (h.unit ?? '').trim();
    if (!value && !unit) continue;
    if (!value) return { erro: 'Um destaque ficou com unidade e sem número. Preencha ou remova a linha.' };
    highlights.push({ value, unit: unit || null });
  }

  const specs: ProductSpec[] = [];
  for (const s of r.specs) {
    const label = s.label.trim();
    const value = s.value.trim();
    if (!label && !value) continue;
    if (!label || !value) {
      return { erro: 'Uma linha da ficha ficou pela metade. Rótulo e valor são obrigatórios.' };
    }
    specs.push({ label, value });
  }

  const categoria = r.categoria.trim();

  return {
    payload: {
      name: nome,
      description: r.descricao.trim() || null,
      price: preco,
      stock: estoque,
      category: categoria || null,
      // Sempre as duas listas inteiras, mesmo vazias: é assim que o `PUT` é honesto.
      highlights,
      specs,
      ...(r.id ? { active: r.ativo } : {}),
    },
  };
}

/* ---------------------------------------------------------------- notas */

/**
 * Erro nosso, não do servidor.
 *
 * `ErrorNote` só sabe traduzir `ApiError` — qualquer outra coisa vira "não foi possível falar
 * com o servidor", que seria mentira sobre um campo mal preenchido.
 */
function NotaLocal({ texto }: { texto: string | null }) {
  if (!texto) return null;
  return (
    <p
      role="alert"
      style={{
        margin: 0, fontSize: 13, lineHeight: 1.6, color: 'var(--danger)',
        background: 'var(--danger-soft)', border: '1px solid var(--danger)',
        borderRadius: 8, padding: '10px 12px',
      }}
    >
      {texto}
    </p>
  );
}

/* ---------------------------------------------------------------- situação */

function Situacao({ p }: { p: Product }) {
  if (!p.active) return <Tag tone="danger">desativado</Tag>;
  if (p.stock === 0) return <Tag tone="accent">sem estoque</Tag>;
  return <Tag tone="ok">à venda</Tag>;
}

/* ---------------------------------------------------------------- tela */

export default function ProdutosPage() {
  const call = useApi();

  const [pagina, setPagina] = useState<Page<Product> | null>(null);
  const [indice, setIndice] = useState(0);
  const [busca, setBusca] = useState('');
  const [termo, setTermo] = useState('');
  const [carregando, setCarregando] = useState(true);
  const [erroLista, setErroLista] = useState<unknown>(null);
  const [recarga, setRecarga] = useState(0);

  const [rascunho, setRascunho] = useState<Rascunho | null>(null);
  const [abrindo, setAbrindo] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);
  const [erroForm, setErroForm] = useState<unknown>(null);
  /** Erro de ação de linha. Separado do erro do formulário porque o formulário pode estar fechado. */
  const [erroAcao, setErroAcao] = useState<unknown>(null);
  const [invalidoForm, setInvalidoForm] = useState<string | null>(null);
  const [invalidoAcao, setInvalidoAcao] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [confirmando, setConfirmando] = useState<string | null>(null);
  const [agindo, setAgindo] = useState<string | null>(null);

  // Quem dispara a busca também liga o "carregando": ligar aqui dentro seria um setState
  // dentro do efeito, e um render em cascata a cada troca de página.
  useEffect(() => {
    let vivo = true;
    const params = new URLSearchParams({ page: String(indice), size: String(TAMANHO) });
    if (busca) params.set('q', busca);

    call<Page<Product>>(`/products?${params}`)
      .then((p) => { if (vivo) { setPagina(p); setErroLista(null); } })
      .catch((e: unknown) => { if (vivo) setErroLista(e); })
      .finally(() => { if (vivo) setCarregando(false); });

    return () => { vivo = false; };
  }, [call, indice, busca, recarga]);

  const recarregar = useCallback(() => {
    setCarregando(true);
    setRecarga((n) => n + 1);
  }, []);

  const irPara = useCallback((p: number) => {
    setCarregando(true);
    setIndice(p);
  }, []);

  const buscarPor = useCallback((q: string) => {
    setCarregando(true);
    setIndice(0);
    setBusca(q);
  }, []);

  /**
   * A leitura que protege a ficha. Toda escrita sobre produto existente passa por aqui —
   * inclusive reativar, que também é um `PUT` e também levaria a ficha junto.
   */
  const carregarFicha = useCallback(
    async (id: string): Promise<Product | null> => {
      setAgindo(id);
      setErroAcao(null);
      try {
        return await call<Product>(`/products/${id}`);
      } catch (e) {
        setErroAcao(e);
        return null;
      } finally {
        setAgindo(null);
      }
    },
    [call],
  );

  async function abrirEdicao(id: string) {
    setAviso(null);
    setInvalidoForm(null);
    setInvalidoAcao(null);
    setErroForm(null);
    setAbrindo(id);
    const atual = await carregarFicha(id);
    setAbrindo(null);
    if (atual) setRascunho(rascunhoDe(atual));
  }

  async function salvar(e: React.FormEvent) {
    e.preventDefault();
    if (!rascunho) return;

    const montado = montar(rascunho);
    if ('erro' in montado) {
      setInvalidoForm(montado.erro);
      return;
    }
    setInvalidoForm(null);

    setSalvando(true);
    setErroForm(null);
    try {
      if (rascunho.id) {
        await call<Product>(`/products/${rascunho.id}`, { method: 'PUT', body: montado.payload });
        setAviso('Produto atualizado, ficha técnica inclusive.');
      } else {
        await call<Product>('/products', { method: 'POST', body: montado.payload });
        setAviso('Produto criado e já ativo.');
      }
      setRascunho(null);
      recarregar();
    } catch (err) {
      setErroForm(err);
    } finally {
      setSalvando(false);
    }
  }

  async function desativar(id: string) {
    setAgindo(id);
    setErroAcao(null);
    try {
      await call<void>(`/products/${id}`, { method: 'DELETE' });
      setConfirmando(null);
      setAviso('Produto desativado. Ele some da venda e continua nos pedidos antigos.');
      recarregar();
    } catch (err) {
      setErroAcao(err);
    } finally {
      setAgindo(null);
    }
  }

  /** Reativar é um `PUT`, então carrega a ficha antes — pelo mesmo motivo de sempre. */
  async function reativar(id: string) {
    const atual = await carregarFicha(id);
    if (!atual) return;

    const montado = montar({ ...rascunhoDe(atual), ativo: true });
    if ('erro' in montado) {
      setInvalidoAcao(montado.erro);
      return;
    }
    setInvalidoAcao(null);

    setAgindo(id);
    try {
      await call<Product>(`/products/${id}`, { method: 'PUT', body: montado.payload });
      setAviso('Produto reativado.');
      recarregar();
    } catch (err) {
      setErroAcao(err);
    } finally {
      setAgindo(null);
    }
  }

  const editando = rascunho?.id != null;

  return (
    <>
      <OriginBanner />

      <PageHead
        title="Produtos"
        hint="Catálogo, preço, estoque e ficha técnica. A lista mostra também os desativados."
        action={
          rascunho ? null : (
            <Button
              onClick={() => {
                setRascunho(VAZIO);
                setErroForm(null);
                setInvalidoForm(null);
                setAviso(null);
              }}
            >
              Novo produto
            </Button>
          )
        }
      />

      <div style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>
        {aviso ? <OkNote>{aviso}</OkNote> : null}

        {rascunho ? (
          <Card style={{ padding: '22px 24px' }}>
            <form onSubmit={salvar} style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
                <h2 style={{ margin: 0, fontSize: 18, fontWeight: 600 }}>
                  {editando ? 'Editar produto' : 'Novo produto'}
                </h2>
                {editando ? (
                  <span className="mono" style={{ fontSize: 12, color: 'var(--ink3)' }}>
                    {rascunho.id}
                  </span>
                ) : null}
              </div>

              {editando ? (
                <p style={{ margin: 0, fontSize: 13, color: 'var(--ink3)', lineHeight: 1.7 }}>
                  Destaques e ficha técnica abaixo foram lidos do servidor agora e serão
                  reenviados por inteiro. O <span className="mono">PUT</span> substitui as duas
                  listas — salvar com elas vazias é apagar a ficha, e é assim que se apaga.
                </p>
              ) : null}

              <div
                style={{
                  display: 'grid', gap: 14,
                  gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                }}
              >
                <Field label="Nome">
                  <Input
                    value={rascunho.nome}
                    onChange={(e) => setRascunho({ ...rascunho, nome: e.target.value })}
                    maxLength={LIMITS.name}
                    placeholder='Monitor 27" QHD 144 Hz'
                    disabled={salvando}
                  />
                </Field>

                <Field label="Categoria" hint="Texto livre. Vazia tira o produto da barra de filtros.">
                  <Input
                    value={rascunho.categoria}
                    onChange={(e) => setRascunho({ ...rascunho, categoria: e.target.value })}
                    maxLength={LIMITS.category}
                    placeholder="Monitores"
                    disabled={salvando}
                  />
                </Field>

                <Field label="Preço" hint="Em reais, até duas casas.">
                  <Input
                    value={rascunho.preco}
                    onChange={(e) => setRascunho({ ...rascunho, preco: e.target.value })}
                    type="number"
                    min={0}
                    step={0.01}
                    inputMode="decimal"
                    placeholder="2449.00"
                    className="mono"
                    disabled={salvando}
                  />
                </Field>

                <Field label="Estoque" hint="Zerar não desativa; só tira da venda até repor.">
                  <Input
                    value={rascunho.estoque}
                    onChange={(e) => setRascunho({ ...rascunho, estoque: e.target.value })}
                    type="number"
                    min={0}
                    step={1}
                    inputMode="numeric"
                    placeholder="12"
                    className="mono"
                    disabled={salvando}
                  />
                </Field>
              </div>

              <Field label="Descrição" hint="Texto corrido. A ficha técnica abaixo é o que vira tabela.">
                <Textarea
                  value={rascunho.descricao}
                  onChange={(e) => setRascunho({ ...rascunho, descricao: e.target.value })}
                  placeholder="Painel IPS de 27 polegadas em 2560×1440."
                  disabled={salvando}
                />
              </Field>

              <HighlightsEditor
                value={rascunho.highlights}
                onChange={(highlights) => setRascunho({ ...rascunho, highlights })}
                disabled={salvando}
              />

              <SpecsEditor
                value={rascunho.specs}
                onChange={(specs) => setRascunho({ ...rascunho, specs })}
                disabled={salvando}
              />

              {editando ? (
                <label style={{ display: 'flex', alignItems: 'center', gap: 10, minHeight: 44 }}>
                  <input
                    type="checkbox"
                    checked={rascunho.ativo}
                    onChange={(e) => setRascunho({ ...rascunho, ativo: e.target.checked })}
                    disabled={salvando}
                    style={{ width: 18, height: 18, accentColor: 'var(--ink)' }}
                  />
                  <span style={{ fontSize: 14, color: 'var(--ink2)' }}>
                    Produto ativo — desmarcar tira da venda sem apagar nada.
                  </span>
                </label>
              ) : null}

              <NotaLocal texto={invalidoForm} />
              <ErrorNote error={erroForm} />

              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                <Button type="submit" disabled={salvando}>
                  {salvando ? 'Salvando…' : editando ? 'Salvar alterações' : 'Criar produto'}
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  disabled={salvando}
                  onClick={() => { setRascunho(null); setErroForm(null); setInvalidoForm(null); }}
                >
                  Cancelar
                </Button>
              </div>
            </form>
          </Card>
        ) : null}

        <Section title="Catálogo">
          <form
            onSubmit={(e) => { e.preventDefault(); buscarPor(termo.trim()); }}
            style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}
          >
            <Input
              type="search"
              value={termo}
              onChange={(e) => setTermo(e.target.value)}
              placeholder="Buscar por nome"
              aria-label="Buscar produtos por nome"
              style={{ flexGrow: 1, minWidth: 200, maxWidth: 360 }}
            />
            <Button type="submit" variant="ghost">Buscar</Button>
            {busca ? (
              <Button
                type="button"
                variant="ghost"
                onClick={() => { setTermo(''); buscarPor(''); }}
              >
                Limpar
              </Button>
            ) : null}
          </form>

          <ErrorNote error={erroLista} />
          <ErrorNote error={erroAcao} />
          <NotaLocal texto={invalidoAcao} />

          {carregando && !pagina ? (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '40px 0' }}>
              <Spinner size={22} />
            </div>
          ) : null}

          {pagina && pagina.content.length === 0 ? (
            <Empty
              title={busca ? 'Nenhum produto com esse nome' : 'Nenhum produto cadastrado'}
              hint={busca ? 'A busca é por nome, não por categoria nem por ficha.' : 'Comece por “Novo produto”.'}
            />
          ) : null}

          {pagina && pagina.content.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {pagina.content.map((p) => {
                const ocupado = agindo === p.id || abrindo === p.id;
                return (
                  <Card key={p.id} style={{ padding: '14px 16px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
                      <div style={{ flexGrow: 1, minWidth: 220 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                          <span style={{ fontSize: 15, fontWeight: 500 }}>{p.name}</span>
                          <Situacao p={p} />
                        </div>
                        <div style={{ display: 'flex', gap: 12, marginTop: 4, flexWrap: 'wrap' }}>
                          <span style={{ fontSize: 12, color: 'var(--ink3)' }}>
                            {p.category ?? 'sem categoria'}
                          </span>
                          <span className="mono" style={{ fontSize: 12, color: 'var(--ink3)' }}>
                            {p.specs.length} specs · {p.highlights.length} destaques
                          </span>
                          <span className="mono" style={{ fontSize: 11, color: 'var(--ink3)' }}>
                            {p.id}
                          </span>
                        </div>
                      </div>

                      <Money value={p.price} size={15} />

                      <span
                        className="mono"
                        style={{
                          fontSize: 13, minWidth: 74, textAlign: 'right',
                          color: p.stock === 0 ? 'var(--accent)' : 'var(--ink2)',
                        }}
                      >
                        {p.stock} un
                      </span>

                      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                        <Button
                          variant="ghost"
                          style={{ height: 44 }}
                          disabled={ocupado || salvando}
                          onClick={() => abrirEdicao(p.id)}
                        >
                          {abrindo === p.id ? 'Abrindo…' : 'Editar'}
                        </Button>

                        {p.active ? (
                          confirmando === p.id ? (
                            <>
                              <Button
                                variant="danger"
                                style={{ height: 44 }}
                                disabled={ocupado}
                                onClick={() => desativar(p.id)}
                              >
                                {agindo === p.id ? 'Desativando…' : 'Confirmar'}
                              </Button>
                              <Button
                                variant="ghost"
                                style={{ height: 44 }}
                                onClick={() => setConfirmando(null)}
                              >
                                Não
                              </Button>
                            </>
                          ) : (
                            <Button
                              variant="ghost"
                              style={{ height: 44, color: 'var(--danger)', borderColor: 'var(--danger)' }}
                              disabled={ocupado}
                              onClick={() => { setConfirmando(p.id); setAviso(null); }}
                            >
                              Desativar
                            </Button>
                          )
                        ) : (
                          <Button
                            variant="ghost"
                            style={{ height: 44 }}
                            disabled={ocupado}
                            onClick={() => reativar(p.id)}
                          >
                            {agindo === p.id ? 'Reativando…' : 'Reativar'}
                          </Button>
                        )}
                      </div>
                    </div>

                    {confirmando === p.id ? (
                      <p style={{ margin: '10px 0 0', fontSize: 13, color: 'var(--ink2)', lineHeight: 1.6 }}>
                        Desativar tira o produto da venda e o mantém legível nos pedidos que já o
                        referenciam. Nada é apagado, e dá para reativar depois.
                      </p>
                    ) : null}
                  </Card>
                );
              })}
            </div>
          ) : null}

          {pagina ? (
            <Pager page={pagina.page} totalPages={pagina.totalPages} onChange={irPara} />
          ) : null}
        </Section>
      </div>
    </>
  );
}
