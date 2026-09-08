/**
 * A moldura do painel do dono.
 *
 * A ordem dos três não é estética. `OriginProvider` fica por fora porque tanto o portão
 * quanto as páginas leem o mesmo estado: uma única chamada a `GET /owners/origin` responde
 * "quem está logado é dono?" e "a loja está vendendo?", e duplicá-la por tela daria quatro
 * respostas que podem discordar entre si.
 *
 * `AdminGate` vem antes de `AdminShell` de propósito: quem não é dono não deve ver o menu
 * lateral do painel. Todo link dele levaria a uma tela que responde 403 — mostrar a navegação
 * seria convidar para uma porta trancada.
 *
 * Não é Client Component: o layout só compõe peças que já são clientes.
 */

import type { Metadata } from 'next';
import { AdminShell } from '@/components/chrome';
import { AdminGate, OriginProvider } from '@/components/admin/origin';

export const metadata: Metadata = {
  title: 'Painel — VOLT',
  robots: { index: false, follow: false },
};

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <OriginProvider>
      <AdminGate>
        <AdminShell>{children}</AdminShell>
      </AdminGate>
    </OriginProvider>
  );
}
