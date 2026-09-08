'use client';

/**
 * Conta.
 *
 * A porta de entrada da área autenticada: é para cá que o catálogo manda quem tenta comprar
 * sem sessão. Sem token, a tela é o botão do Google; com token, é o mesmo formulário de perfil
 * e a mesma lista de endereços que o checkout usa — de propósito, para o cliente não descobrir
 * dois jeitos diferentes de cadastrar a mesma coisa.
 */

import Link from 'next/link';
import { useAuth } from '@/lib/auth';
import { date } from '@/lib/format';
import { StoreHeader } from '@/components/chrome';
import { Banner, Button, Card, Tag } from '@/components/ui';
import { ParedeDeEntrada } from '@/components/compra/entrar';
import { PerfilForm } from '@/components/compra/perfil-form';
import { Enderecos } from '@/components/compra/enderecos';

export default function ContaPage() {
  return (
    <>
      <StoreHeader />
      <main className="container" style={{ maxWidth: 780, padding: '36px 20px 64px' }}>
        <ParedeDeEntrada motivo="Sua conta guarda o carrinho, os endereços e os pedidos.">
          <Conteudo />
        </ParedeDeEntrada>
      </main>
    </>
  );
}

function Conteudo() {
  const { user, signOut } = useAuth();
  if (!user) return null; // a parede já cuidou disso; aqui é só o TypeScript

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 30 }}>
      <h1 style={{ margin: 0, fontSize: 28, fontWeight: 700, letterSpacing: '-.02em' }}>
        Sua conta
      </h1>

      <Card style={{ padding: 22, display: 'flex', gap: 16, alignItems: 'center', flexWrap: 'wrap' }}>
        {user.photoUrl ? (
          // A foto vem do Google, domínio que o next/image exigiria configurar no next.config.
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={user.photoUrl}
            alt=""
            style={{ width: 52, height: 52, borderRadius: '50%', objectFit: 'cover' }}
          />
        ) : null}
        <div style={{ flexGrow: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 4 }}>
          <span style={{ fontSize: 16, fontWeight: 600 }}>{user.name}</span>
          <span className="mono" style={{ fontSize: 12, color: 'var(--ink3)' }}>{user.email}</span>
          <span style={{ fontSize: 12, color: 'var(--ink3)' }}>
            Cliente desde {date(user.createdAt)}
          </span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          {user.canCheckout ? <Tag tone="ok">pronto para comprar</Tag> : null}
          <Button variant="ghost" onClick={signOut}>Sair</Button>
        </div>
      </Card>

      {!user.canCheckout ? (
        <Banner title="Falta pouco para você poder fechar pedidos">
          Complete o CPF e o telefone abaixo. Sem os dois o checkout é recusado — e é melhor
          resolver agora do que no meio da compra.
        </Banner>
      ) : null}

      <PerfilForm
        titulo="Seus dados"
        descricao="O CPF vai na nota fiscal do pedido; o telefone serve para falarmos sobre a entrega."
      />

      <Enderecos
        titulo="Endereços"
        descricao="O endereço escolhido no checkout define o frete, cotado pela distância até a loja."
      />

      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <Link href="/pedidos"><Button variant="ghost">Meus pedidos</Button></Link>
        <Link href="/carrinho"><Button variant="ghost">Meu carrinho</Button></Link>
      </div>
    </div>
  );
}
