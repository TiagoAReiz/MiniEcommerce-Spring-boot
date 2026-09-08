'use client';

/**
 * A moldura das duas áreas: a loja e o painel do dono.
 *
 * Fica na fundação de propósito. São quatro conjuntos de telas sendo construídos em paralelo,
 * e cabeçalho é exatamente o tipo de coisa que vira quatro versões ligeiramente diferentes se
 * cada tela desenhar a sua.
 */

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAuth } from '@/lib/auth';
import { ThemeToggle } from './ui';

function Mark() {
  return (
    <Link href="/" style={{ display: 'flex', alignItems: 'center', gap: 9, color: 'var(--ink)' }}>
      <span
        style={{
          width: 26, height: 26, borderRadius: 7, background: 'var(--ink)', color: 'var(--bg)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 14,
        }}
      >
        V
      </span>
      <span style={{ fontSize: 18, fontWeight: 700, letterSpacing: '-.01em' }}>VOLT</span>
    </Link>
  );
}

export function StoreHeader() {
  const { user, role, loading } = useAuth();

  return (
    <header
      style={{
        position: 'sticky', top: 0, zIndex: 20, background: 'var(--surface)',
        borderBottom: '1px solid var(--line)',
      }}
    >
      <div
        className="container"
        style={{ display: 'flex', alignItems: 'center', gap: 24, height: 64 }}
      >
        <Mark />
        <nav style={{ display: 'flex', gap: 18, marginLeft: 12, fontSize: 14 }}>
          <Link href="/" style={{ color: 'var(--ink2)' }}>Catálogo</Link>
          <Link href="/pedidos" style={{ color: 'var(--ink2)' }}>Meus pedidos</Link>
        </nav>
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 12 }}>
          <ThemeToggle />
          <Link
            href="/carrinho"
            className="mono"
            style={{
              height: 36, padding: '0 14px', borderRadius: 8, display: 'flex', alignItems: 'center',
              background: 'var(--ink)', color: 'var(--bg)', fontSize: 13,
            }}
          >
            Carrinho
          </Link>
          {/*
            Dono não tem linha em `users`, então `user` é null mesmo com sessão válida —
            olhar só para `user` mostraria "Entrar" a quem já entrou. Quem responde "há
            sessão?" é o papel; o nome é enfeite que só o cliente tem.
          */}
          {loading ? null : role === 'OWNER' ? (
            <Link href="/admin" style={{ fontSize: 14, color: 'var(--ink2)' }}>Painel</Link>
          ) : user ? (
            <Link href="/conta" style={{ fontSize: 14, color: 'var(--ink2)' }}>
              {user.name.split(' ')[0]}
            </Link>
          ) : (
            <Link href="/conta" style={{ fontSize: 14, color: 'var(--ink2)' }}>Entrar</Link>
          )}
        </div>
      </div>
    </header>
  );
}

const ADMIN_NAV = [
  ['/admin', 'Visão geral'],
  ['/admin/produtos', 'Produtos'],
  ['/admin/pedidos', 'Pedidos'],
  ['/admin/config', 'Configurações'],
] as const;

export function AdminShell({ children }: { children: React.ReactNode }) {
  const path = usePathname();

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      <aside
        style={{
          width: 232, flexShrink: 0, background: 'var(--surface)',
          borderRight: '1px solid var(--line)', padding: '22px 14px',
          display: 'flex', flexDirection: 'column', gap: 24,
        }}
      >
        <div style={{ padding: '0 8px' }}>
          <Mark />
          <p className="mono" style={{ margin: '4px 0 0 35px', fontSize: 10, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink3)' }}>
            Painel
          </p>
        </div>
        <nav style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {ADMIN_NAV.map(([href, label]) => {
            const active = path === href;
            return (
              <Link
                key={href}
                href={href}
                style={{
                  height: 40, padding: '0 12px', borderRadius: 8, display: 'flex', alignItems: 'center',
                  fontSize: 14, color: active ? 'var(--ink)' : 'var(--ink3)',
                  background: active ? 'var(--surface2)' : 'transparent',
                  fontWeight: active ? 500 : 400,
                }}
              >
                {label}
              </Link>
            );
          })}
        </nav>
        <div style={{ marginTop: 'auto', display: 'flex', flexDirection: 'column', gap: 10, padding: '0 8px' }}>
          <ThemeToggle />
          <Link href="/" style={{ fontSize: 13, color: 'var(--ink3)' }}>← Ver a loja</Link>
        </div>
      </aside>
      <main style={{ flexGrow: 1, minWidth: 0, padding: '28px 32px 48px' }}>{children}</main>
    </div>
  );
}
