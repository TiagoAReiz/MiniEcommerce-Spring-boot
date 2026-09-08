/**
 * Aplica o tema salvo antes da primeira pintura.
 *
 * Precisa ser script inline e síncrono no <head>: se esperar a hidratação, quem escolheu o
 * tema escuro vê um lampejo branco em toda navegação. É o único caso em que vale um
 * dangerouslySetInnerHTML aqui — o conteúdo é literal, não vem de dado nenhum.
 */
export function ThemeScript() {
  const js = `
try {
  var t = localStorage.getItem('volt.theme');
  if (t === 'dark' || t === 'light') document.documentElement.dataset.theme = t;
} catch (e) {}
`.trim();

  return <script dangerouslySetInnerHTML={{ __html: js }} />;
}
