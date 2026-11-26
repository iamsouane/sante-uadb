// Génère docs/rapport.pdf depuis docs/rapport.md.
//   cd docs/generation && npm install && npx playwright install chromium-headless-shell && npm run pdf
import { readFileSync, writeFileSync, rmSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { marked } from 'marked';
import { chromium } from 'playwright';

const docs = fileURLToPath(new URL('..', import.meta.url));
const md = readFileSync(docs + 'rapport.md', 'utf8');
const css = readFileSync(new URL('rapport.css', import.meta.url), 'utf8');

const titres = [...md.matchAll(/^(##|###) (.+)$/gm)];
const ancre = (t) => t.toLowerCase().replace(/[^\p{L}\d]+/gu, '-');
const sommaire = titres.map(([, n, t]) => `<li class="n${n.length}"><a href="#${ancre(t)}">${t.replace(/`/g, '')}</a></li>`).join('');
marked.use({
  renderer: {
    heading({ tokens, depth, text }) {
      return `<h${depth} id="${ancre(text)}">${this.parser.parseInline(tokens)}</h${depth}>`;
    },
  },
});
const [garde, ...reste] = md.split('</section>');
const html = `<!doctype html><html lang="fr"><meta charset="utf-8"><title>Rapport technique</title><style>${css}</style>
${garde}</section><nav><h2>Sommaire</h2><ol>${sommaire}</ol></nav>${marked.parse(reste.join('</section>'))}</html>`;

// Fichier temporaire dans docs/ : les images relatives restent valides
const tmp = docs + '.rapport.html';
writeFileSync(tmp, html);
const navigateur = await chromium.launch();
try {
  const page = await navigateur.newPage();
  await page.goto(pathToFileURL(tmp).href, { waitUntil: 'networkidle' });
  await page.pdf({
    path: docs + 'rapport.pdf', format: 'A4', printBackground: true, displayHeaderFooter: true, outline: true, tagged: true,
    headerTemplate: '<span></span>',
    footerTemplate: '<div style="font:9px Helvetica;width:100%;text-align:center;color:#666">Supervision et endpoint /health, binôme 3 - <span class="pageNumber"></span> / <span class="totalPages"></span></div>',
    margin: { top: '20mm', bottom: '20mm', left: '20mm', right: '20mm' },
  });
} finally {
  await navigateur.close();
  rmSync(tmp);
}
console.log('docs/rapport.pdf généré');
