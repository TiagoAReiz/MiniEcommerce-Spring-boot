'use client';

/**
 * A volta do Mercado Pago.
 *
 * A rota não decide nada: ela lê o desfecho carimbado na query e entrega para
 * `<TelaPagamento>`, que pergunta ao backend. O gateway devolve o cliente para cá em
 * qualquer desfecho, e nenhum deles prova pagamento — quem confirma é o webhook, e a
 * espera por ele vive no `usePedidoWatch`. Por isso `desfecho` só escolhe texto.
 *
 * `use(searchParams)` suspende, então o desempacotamento fica num filho sob `<Suspense>`:
 * é o que a documentação do Next pede quando a página cliente lê a query.
 */

import { Suspense, use } from 'react';
import { Spinner } from '@/components/ui';
import { Tela } from '@/components/pagamento/pecas';
import { TelaPagamento } from '@/components/pagamento/TelaPagamento';
import { lerDesfecho } from '@/components/pagamento/contrato';

type Props = {
  params: Promise<{ orderId: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

export default function PagamentoPage(props: Props) {
  return (
    <Suspense fallback={<Carregando />}>
      <Retorno {...props} />
    </Suspense>
  );
}

function Retorno({ params, searchParams }: Props) {
  const { orderId } = use(params);
  // `status` / `collection_status` do Mercado Pago viram 'sucesso' | 'falha' | 'pendente'.
  const desfecho = lerDesfecho(use(searchParams));

  return <TelaPagamento orderId={orderId} desfecho={desfecho} />;
}

function Carregando() {
  return (
    <Tela>
      <div style={{ display: 'flex', justifyContent: 'center', padding: 56 }}>
        <Spinner size={24} />
      </div>
    </Tela>
  );
}
