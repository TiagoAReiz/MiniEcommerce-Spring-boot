import type { Metadata } from 'next';
import { Archivo, IBM_Plex_Mono } from 'next/font/google';
import { AuthProvider } from '@/lib/auth';
import { ThemeScript } from '@/components/ThemeScript';
import './globals.css';

const archivo = Archivo({
  subsets: ['latin'],
  weight: ['400', '500', '600', '700'],
  variable: '--font-archivo',
  display: 'swap',
});

const plexMono = IBM_Plex_Mono({
  subsets: ['latin'],
  weight: ['400', '500', '600'],
  variable: '--font-plex-mono',
  display: 'swap',
});

export const metadata: Metadata = {
  title: 'VOLT — Eletrônicos',
  description:
    'Monitores, notebooks, armazenamento e áudio, com ficha técnica completa e frete calculado por distância.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    /*
      `suppressHydrationWarning` só aqui, e só por causa do <ThemeScript />: ele escreve
      `data-theme` no <html> antes da hidratação, de propósito, para não haver lampejo de tema
      claro em quem escolheu escuro. O servidor não tem como saber essa escolha — ela vive no
      localStorage —, então o atributo diverge por desenho, não por defeito.

      O escopo é estreito: a supressão vale para os atributos DESTE elemento, não para os
      filhos. Nada mais dentro da árvore deixa de ser conferido.
    */
    <html
      lang="pt-BR"
      className={`${archivo.variable} ${plexMono.variable}`}
      suppressHydrationWarning
    >
      <head>
        <ThemeScript />
      </head>
      <body>
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
