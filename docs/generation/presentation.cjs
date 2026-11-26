// Génère docs/presentation.pptx (soutenance du sujet A, binôme 3).
//   cd docs/generation && npm install && npm run slides
// Les chiffres viennent des mesures réelles (docs/mesures, rapport section 6) : ne pas les inventer.
const pptxgen = require("pptxgenjs");
const React = require("react");
const ReactDOMServer = require("react-dom/server");
const sharp = require("sharp");
const fa = require("react-icons/fa");
const fs = require("fs");
const path = require("path");
const JSZip = require("jszip");

const PROJET = path.resolve(__dirname, "../..") + "/";
const IMG = path.join(__dirname, "images") + "/";
const SORTIE = PROJET + "docs/presentation.pptx";

// Pétrole profond + couleurs d'état du tableau de bord (vert UP, rouge DOWN, ambre DEGRADE)
const THEME = {
  name: "Supervision sante",
  headFontFace: "Arial",
  bodyFontFace: "Arial",
  colors: {
    dk1: "14232A", lt1: "FFFFFF", dk2: "0F3B3F", lt2: "EEF4F3",
    accent1: "0E8C6B", accent2: "D9483B", accent3: "E8A317",
    accent4: "5B6B73", accent5: "2F6F8F", accent6: "7A5BA6",
    hlink: "0E8C6B", folHlink: "5B6B73",
  },
};
const H = THEME.colors;

// pptxgenjs n'écrit pas les couleurs du thème : sans cette étape, les couleurs de schéma
// (C.text2, C.accent1…) prendraient la palette Office par défaut
async function appliquerTheme(fichier, theme) {
  const zip = await JSZip.loadAsync(fs.readFileSync(fichier));
  const partie = "ppt/theme/theme1.xml";
  const xml = await zip.file(partie).async("string");
  const cles = ["dk1", "lt1", "dk2", "lt2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink"];
  const schema = `<a:clrScheme name="${theme.name}">` + cles.map((k) => `<a:${k}><a:srgbClr val="${theme.colors[k]}"/></a:${k}>`).join("") + "</a:clrScheme>";
  const nouveau = xml.replace(/<a:clrScheme\b[\s\S]*?<\/a:clrScheme>/, schema);
  if (!nouveau.includes(schema)) throw new Error("Thème introuvable dans " + partie);
  zip.file(partie, nouveau);
  fs.writeFileSync(fichier, await zip.generateAsync({ type: "nodebuffer", compression: "DEFLATE" }));
}

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13,333 x 7,5 pouces
pres.title = "Supervision et endpoint /health";
pres.subject = "Eureka, Spring Boot Actuator, états UP/DOWN";
pres.author = "Serigne Ismaila SOUANE, Moussa NIANG";
pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
const C = pres.SchemeColor;

const W = 13.333, M = 0.6;

// ---------- Layouts
const piedDePage = (couleur) => [
  { text: { text: "Binôme 3 · Supervision et endpoint /health", options: { x: M, y: 7.0, w: 7, h: 0.3, fontSize: 10, color: couleur, margin: 0 } } },
];
pres.defineSlideMaster({
  title: "TITRE",
  background: { color: H.dk2 },
  objects: [
    { placeholder: { options: { name: "title", type: "title", x: M, y: 2.2, w: 11, h: 1.6, fontSize: 48, bold: true, color: C.background1, valign: "bottom", align: "left", margin: 0 }, text: "" } },
    { placeholder: { options: { name: "body", type: "body", x: M, y: 3.95, w: 11, h: 0.8, fontSize: 24, color: "9FD8C8", valign: "top", align: "left", margin: 0 }, text: "" } },
  ],
});
pres.defineSlideMaster({
  title: "SECTION",
  background: { color: H.dk2 },
  objects: [
    { placeholder: { options: { name: "title", type: "title", x: M, y: 0.6, w: 12, h: 1.0, fontSize: 40, bold: true, color: C.background1, align: "left", margin: 0 }, text: "" } },
    ...piedDePage("9DB4B0"),
  ],
  slideNumber: { x: 12.2, y: 7.0, w: 0.5, h: 0.3, fontSize: 10, color: "9DB4B0", align: "right" },
});
pres.defineSlideMaster({
  title: "CONTENU",
  background: { color: H.lt1 },
  objects: [
    { placeholder: { options: { name: "title", type: "title", x: M, y: 0.4, w: 12.1, h: 0.9, fontSize: 34, bold: true, color: C.text2, valign: "middle", align: "left", margin: 0 }, text: "" } },
    ...piedDePage("7D8A90"),
  ],
  slideNumber: { x: 12.2, y: 7.0, w: 0.5, h: 0.3, fontSize: 10, color: "7D8A90", align: "right" },
});

// ---------- Outils
async function icone(Comp, couleur = "#FFFFFF") {
  const svg = ReactDOMServer.renderToStaticMarkup(React.createElement(Comp, { color: couleur, size: 256 }));
  const png = await sharp(Buffer.from(svg)).png().toBuffer();
  return "image/png;base64," + png.toString("base64");
}
let n = 0;
const nom = (p) => `${p}-${++n}`;

// Le motif : une pastille d'état, comme dans le tableau de bord
async function pastille(slide, x, y, d, couleur, Comp, alt = "") {
  slide.addShape(pres.shapes.OVAL, { x, y, w: d, h: d, fill: { color: couleur }, line: { color: couleur }, objectName: nom("pastille") });
  const m = d * 0.26;
  slide.addImage({ data: await icone(Comp), x: x + m, y: y + m, w: d - 2 * m, h: d - 2 * m, objectName: nom("icone"), altText: alt || "Icône décorative" });
}
const texte = (slide, t, o) => slide.addText(t, { isTextBox: true, margin: 0, fontSize: 16, color: C.text1, valign: "top", objectName: nom("texte"), ...o });
const carte = (slide, x, y, w, h, fond = C.background2) =>
  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, rectRadius: 0.12, fill: { color: fond }, line: { color: fond }, objectName: nom("carte") });
const code = (slide, t, o) => slide.addText(t, {
  isTextBox: true, fontFace: "Courier New", fontSize: 13, color: C.text1, valign: "top",
  fill: { color: H.lt2 }, margin: [10, 12, 10, 12], objectName: nom("code"), ...o,
});
const contenu = (titre, section) => {
  const s = pres.addSlide({ masterName: "CONTENU", sectionTitle: section });
  s.addText(titre, { placeholder: "title" });
  return s;
};

(async () => {
  // ===== 1. Titre
  pres.addSection({ title: "Ouverture" });
  let s = pres.addSlide({ masterName: "TITRE", sectionTitle: "Ouverture" });
  s.addText("Supervision et endpoint /health", { placeholder: "title" });
  s.addText("Eureka, Spring Boot Actuator, états UP/DOWN", { placeholder: "body" });
  await pastille(s, M, 1.0, 0.9, H.accent1, fa.FaHeartbeat);
  texte(s, [
    { text: "Serigne Ismaila SOUANE · Moussa NIANG", options: { bold: true, breakLine: true } },
    { text: "SID Bambey, Développement Web Avancé 2026-2027 · Sujet A, binôme 3 · Enseignant : Maodo DIOP" },
  ], { x: M, y: 5.6, w: 12, h: 0.8, fontSize: 16, color: "CFE3DE" });
  s.addNotes("Présente : Ismaila. 30 s. Se présenter, annoncer le sujet et le déroulé : le problème, la solution, une démonstration en direct, puis ce que nos mesures ont révélé.");

  // ===== 2. Le problème
  s = contenu("Lancé ne veut pas dire en état de servir", "Ouverture");
  texte(s, "Le processus tourne, Docker le dit vivant… et pourtant le service ne rend plus rien. Trois cas fréquents :", { x: M, y: 1.45, w: 12, h: 0.5, fontSize: 18, color: C.text2 });
  const cas = [
    [fa.FaDatabase, H.accent2, "La base est coupée", "Le processus répond, mais chaque requête métier échoue."],
    [fa.FaBolt, H.accent2, "L'instance est tuée net", "Personne ne prévient le registre : il continue d'envoyer du trafic."],
    [fa.FaHourglassHalf, H.accent3, "Dépendance absente", "Le service répond à moitié : faut-il le couper ou non ?"],
  ];
  for (let i = 0; i < 3; i++) {
    const x = M + i * 4.1;
    carte(s, x, 2.35, 3.8, 3.2);
    await pastille(s, x + 0.35, 2.65, 0.8, cas[i][1], cas[i][0]);
    texte(s, cas[i][2], { x: x + 0.35, y: 3.65, w: 3.2, h: 0.5, fontSize: 20, bold: true, color: C.text2 });
    texte(s, cas[i][3], { x: x + 0.35, y: 4.2, w: 3.15, h: 1.1, fontSize: 16 });
  }
  texte(s, [{ text: "Question du projet : ", options: { bold: true } }, { text: "qui le sait, et en combien de temps le trafic s'en détourne-t-il ?" }],
    { x: M, y: 5.95, w: 12, h: 0.6, fontSize: 18, color: C.text2 });
  s.addNotes("Présente : Ismaila. 1 min. Insister sur la différence entre « lancé » et « en état de servir » : c'est le fil rouge de toute la présentation.");

  // ===== 3. Trois mécanismes
  s = contenu("Trois mécanismes qui se complètent", "Ouverture");
  const meca = [
    [fa.FaHeartbeat, "Spring Boot Actuator", "/actuator/health", "Chaque instance décrit son propre état : base, disque, stock, dépendances."],
    [fa.FaAddressBook, "Netflix Eureka", "registre de services", "Sait quelles instances existent et lesquelles reçoivent du trafic."],
    [fa.FaEye, "Notre supervision", "+ Prometheus, Grafana", "Croise les deux sources, garde l'historique, alerte."],
  ];
  for (let i = 0; i < 3; i++) {
    const y = 1.65 + i * 1.65;
    await pastille(s, M, y, 1.0, [H.accent1, H.accent5, H.dk2][i], meca[i][0]);
    texte(s, [{ text: meca[i][1], options: { bold: true, breakLine: true } }, { text: meca[i][2], options: { fontSize: 15, color: C.accent4 } }],
      { x: M + 1.35, y: y + 0.05, w: 3.9, h: 0.95, fontSize: 22, color: C.text2 });
    texte(s, meca[i][3], { x: 6.1, y: y + 0.15, w: 6.6, h: 0.8, fontSize: 18, valign: "middle" });
  }
  s.addNotes("Présente : Ismaila. 1 min. Un service dit son état, un registre le diffuse, un superviseur vérifie. Aucun des trois ne suffit seul : c'est ce que la démonstration va montrer.");

  // ===== 4. Architecture
  s = contenu("Le terrain d'essai : une boutique en microservices", "Ouverture");
  s.addImage({ path: IMG + "schema-architecture.png", x: 0.55, y: 1.4, w: 9.3, h: 5.17, objectName: "architecture", altText: "Architecture : client, passerelle, deux instances du catalogue, commande, PostgreSQL, Eureka, supervision, tableau de bord, Prometheus et Grafana" });
  texte(s, [
    { text: "6 modules Spring Boot 4", options: { bold: true, breakLine: true } },
    { text: "Catalogue en 2 instances, pour voir la bascule", options: { breakLine: true } },
    { text: " ", options: { breakLine: true, fontSize: 8 } },
    { text: "Un seul lancement", options: { bold: true, breakLine: true } },
    { text: "docker compose up : 10 conteneurs", options: { breakLine: true } },
    { text: " ", options: { breakLine: true, fontSize: 8 } },
    { text: "Prometheus sans adresse en dur", options: { bold: true, breakLine: true } },
    { text: "il trouve ses cibles dans Eureka" },
  ], { x: 10.1, y: 1.7, w: 2.65, h: 4.8, fontSize: 15, color: C.text1 });
  s.addNotes("Présente : Ismaila. 1 min 30. Suivre le trait plein (trafic) puis les tirets (supervision). Le tableau de bord parle directement à la supervision, sans passer par la passerelle : si la passerelle tombe, l'écran doit pouvoir l'afficher.");

  // ===== 5. /actuator/health
  pres.addSection({ title: "Comment ça marche" });
  s = contenu("Ce que répond /actuator/health", "Comment ça marche");
  code(s, '{\n  "status": "DOWN",\n  "components": {\n    "db":            { "status": "DOWN" },\n    "diskSpace":     { "status": "UP" },\n    "livenessState": { "status": "UP" },\n    "stock":         { "status": "DOWN" }\n  }\n}',
    { x: M, y: 1.55, w: 5.6, h: 2.75 });
  texte(s, "Le pire composant l'emporte. Anonyme : le statut seul ; rôle SUPERVISION : le détail.", { x: M, y: 4.5, w: 5.6, h: 0.9, fontSize: 16, color: C.text2 });
  const etats = [
    [H.accent1, fa.FaCheck, "UP", "200", "routée"],
    [H.accent3, fa.FaExclamation, "DEGRADE (créé par nous)", "200", "routée"],
    [H.accent4, fa.FaPause, "OUT_OF_SERVICE", "503", "coupée"],
    [H.accent2, fa.FaTimes, "DOWN", "503", "coupée"],
  ];
  texte(s, [{ text: "Statut", options: {} }], { x: 7.35, y: 1.55, w: 3, h: 0.35, fontSize: 13, color: C.accent4, bold: true });
  texte(s, "HTTP", { x: 10.85, y: 1.55, w: 0.8, h: 0.35, fontSize: 13, color: C.accent4, bold: true });
  texte(s, "Trafic", { x: 11.75, y: 1.55, w: 1, h: 0.35, fontSize: 13, color: C.accent4, bold: true });
  for (let i = 0; i < 4; i++) {
    const y = 2.05 + i * 0.95;
    await pastille(s, 6.75, y, 0.5, etats[i][0], etats[i][1]);
    texte(s, etats[i][2], { x: 7.35, y: y + 0.05, w: 3.4, h: 0.4, fontSize: 17, bold: true, color: C.text2, valign: "middle" });
    texte(s, etats[i][3], { x: 10.85, y: y + 0.05, w: 0.8, h: 0.4, fontSize: 17, valign: "middle" });
    texte(s, etats[i][4], { x: 11.75, y: y + 0.05, w: 1, h: 0.4, fontSize: 17, valign: "middle" });
  }
  s.addNotes("Présente : Moussa. 1 min 30. Le code HTTP permet à un équilibreur de décider sans lire le JSON. DEGRADE est un statut que nous avons créé : le service répond, mais une fonction secondaire est atteinte (rupture de stock). Les détails ne sont visibles qu'avec le rôle SUPERVISION : un attaquant ne doit pas apprendre notre architecture.");

  // ===== 6. Liveness / readiness
  s = contenu("Vivacité et préparation : deux questions différentes", "Comment ça marche");
  const col = [
    [fa.FaRedo, H.accent5, "Vivacité (liveness)", "Faut-il redémarrer le processus ?", "Ne dépend PAS de la base", "→ l'orchestrateur redémarre"],
    [fa.FaTrafficLight, H.accent1, "Préparation (readiness)", "Peut-on lui envoyer du trafic ?", "Dépend de la base", "→ le trafic va ailleurs"],
  ];
  for (let i = 0; i < 2; i++) {
    const x = M + i * 6.15;
    carte(s, x, 1.55, 5.85, 3.15);
    await pastille(s, x + 0.35, 1.85, 0.75, col[i][1], col[i][0]);
    texte(s, col[i][2], { x: x + 1.3, y: 1.95, w: 4.3, h: 0.55, fontSize: 22, bold: true, color: C.text2 });
    texte(s, [
      { text: col[i][3], options: { italic: true, breakLine: true } },
      { text: col[i][4], options: { bold: true, breakLine: true } },
      { text: col[i][5] },
    ], { x: x + 0.35, y: 2.85, w: 5.2, h: 1.6, fontSize: 18, paraSpaceAfter: 6 });
  }
  carte(s, M, 5.0, 12.1, 1.5, "FBEDEA");
  await pastille(s, M + 0.3, 5.35, 0.75, H.accent2, fa.FaDatabase);
  texte(s, [
    { text: "Base coupée, constaté dans notre projet : ", options: { bold: true } },
    { text: "/health répond 503, mais le conteneur reste « healthy ». Si la vivacité dépendait de PostgreSQL, tous les services redémarreraient en boucle sans rien réparer." },
  ], { x: M + 1.35, y: 5.2, w: 10.5, h: 1.1, fontSize: 17, valign: "middle" });
  s.addNotes("Présente : Moussa. 1 min. Le HEALTHCHECK Docker utilise /health/liveness, la supervision et Eureka utilisent /health complet. On le verra pendant la démonstration en coupant la base.");

  // ===== 7. Eureka
  s = contenu("Eureka par défaut ignore la santé", "Comment ça marche");
  const cycle = [["1", "Enregistrement", "au démarrage"], ["2", "Battement", "toutes les 30 s"], ["3", "Copie du registre", "chaque client, 30 s"], ["4", "Expulsion", "bail de 90 s expiré"]];
  for (let i = 0; i < 4; i++) {
    const x = M + i * 3.1;
    s.addShape(pres.shapes.OVAL, { x, y: 1.6, w: 0.6, h: 0.6, fill: { color: H.dk2 }, line: { color: H.dk2 }, objectName: nom("etape") });
    texte(s, cycle[i][0], { x, y: 1.6, w: 0.6, h: 0.6, fontSize: 18, bold: true, color: C.background1, align: "center", valign: "middle" });
    texte(s, [{ text: cycle[i][1], options: { bold: true, breakLine: true } }, { text: cycle[i][2], options: { color: C.accent4 } }],
      { x: x + 0.75, y: 1.58, w: 2.25, h: 0.8, fontSize: 16, color: C.text2 });
  }
  code(s, "eureka:\n  client:\n    healthcheck:\n      enabled: true   # absent = false", { x: M, y: 2.85, w: 5.4, h: 1.65, fontSize: 15 });
  texte(s, [
    { text: "Sans cette ligne", options: { bold: true, breakLine: true } },
    { text: "Eureka ne connaît que les battements de cœur. Base coupée : il annonce UP pour les deux instances du catalogue." },
  ], { x: M, y: 4.75, w: 5.4, h: 1.6, fontSize: 17 });
  carte(s, 6.4, 2.85, 6.3, 3.5);
  texte(s, "Base coupée, DOWN dans Eureka après…", { x: 6.75, y: 3.1, w: 5.7, h: 0.4, fontSize: 16, color: C.text2, bold: true });
  texte(s, "7,7 s", { x: 6.75, y: 3.65, w: 2.8, h: 1.1, fontSize: 54, bold: true, color: C.accent1 });
  texte(s, "avec healthcheck", { x: 6.75, y: 4.75, w: 2.8, h: 0.4, fontSize: 15, color: C.accent4 });
  texte(s, "jamais", { x: 9.75, y: 3.65, w: 2.8, h: 1.1, fontSize: 54, bold: true, color: C.accent2 });
  texte(s, "sans (60 s d'observation)", { x: 9.75, y: 4.75, w: 2.8, h: 0.4, fontSize: 15, color: C.accent4 });
  texte(s, "Mesuré avec scripts/mesurer.py", { x: 6.75, y: 5.6, w: 5.7, h: 0.4, fontSize: 13, color: C.accent4, italic: true });
  s.addNotes("Présente : Moussa. 1 min 30. Point le plus important du sujet : la propriété eureka.client.healthcheck.enabled est à false par défaut. Nous l'avons vu dans le code de Spring Cloud (matchIfMissing = false), puis mesuré.");

  // ===== 8. DEGRADE
  s = contenu("Un statut maison peut couper le trafic", "Comment ça marche");
  texte(s, "Eureka ne connaît que 5 statuts. Le nôtre, DEGRADE, devenait UNKNOWN, et une instance UNKNOWN n'est plus routée : une simple rupture de stock aurait coupé le catalogue.",
    { x: M, y: 1.5, w: 12.1, h: 0.95, fontSize: 18 });
  code(s, "@Override\nprotected InstanceStatus mapToInstanceStatus(Status statut) {\n  return Statuts.DEGRADE.equals(statut)\n      ? InstanceStatus.UP\n      : super.mapToInstanceStatus(statut);\n}",
    { x: M, y: 2.7, w: 7.2, h: 1.95, fontSize: 13 });
  texte(s, "DegradeHealthCheckHandler.java", { x: M, y: 4.85, w: 7.2, h: 0.35, fontSize: 13, color: C.accent4, italic: true });
  const avap = [[H.accent2, fa.FaTimes, "Sans la classe", "DEGRADE → UNKNOWN → plus de trafic"], [H.accent1, fa.FaCheck, "Avec la classe", "DEGRADE → UP → toujours routée"]];
  for (let i = 0; i < 2; i++) {
    const y = 2.7 + i * 1.3;
    await pastille(s, 8.2, y + 0.1, 0.6, avap[i][0], avap[i][1]);
    texte(s, [{ text: avap[i][2], options: { bold: true, breakLine: true } }, { text: avap[i][3] }], { x: 9.0, y, w: 3.75, h: 1.0, fontSize: 16, color: C.text2 });
  }
  texte(s, "Deux tests fixent ce comportement : l'un prouve le défaut, l'autre la correction.", { x: 8.2, y: 5.25, w: 4.55, h: 0.7, fontSize: 14, color: C.accent4 });
  s.addNotes("Présente : Moussa. 1 min. Autre surprise trouvée par un test : Spring traduit OUT_OF_SERVICE en DOWN. Côté serveur, la règle LeaseExistsRule garderait sinon l'instance bloquée en OUT_OF_SERVICE.");

  // ===== 9. Supervision
  s = contenu("La supervision croise Eureka et Actuator", "Comment ça marche");
  s.addImage({ path: PROJET + "docs/captures/03-kill-ecart.png", x: M, y: 1.45, w: 7.6, h: 5.15, objectName: "capture-ecart", altText: "Tableau de bord : une instance du catalogue tuée, Eureka la croit UP, Actuator injoignable, écart signalé, service dégradé" });
  const points = [
    [fa.FaSyncAlt, "Toutes les 5 s", "lit le registre, sonde chaque /health en parallèle"],
    [fa.FaExclamationTriangle, "Signale l'écart", "Eureka UP mais instance morte = trafic perdu"],
    [fa.FaFilter, "Évite les fausses alertes", "INJOIGNABLE après 2 échecs de suite"],
    [fa.FaDatabase, "Sans base de données", "ne tombe pas avec ce qu'elle surveille"],
  ];
  for (let i = 0; i < 4; i++) {
    const y = 1.5 + i * 1.28;
    await pastille(s, 8.55, y, 0.55, [H.accent5, H.accent2, H.accent3, H.dk2][i], points[i][0]);
    texte(s, [{ text: points[i][1], options: { bold: true, breakLine: true } }, { text: points[i][2], options: { fontSize: 15 } }],
      { x: 9.3, y: y - 0.02, w: 3.45, h: 1.15, fontSize: 17, color: C.text2 });
  }
  s.addNotes("Présente : Moussa. 1 min 30. Sur la capture, une instance vient d'être tuée : Eureka la croit UP, Actuator ne répond plus, l'écart est signalé. Le service est DEGRADE et non DOWN, car l'autre instance tient.");

  // ===== 10. Démonstration
  pres.addSection({ title: "Démonstration" });
  s = pres.addSlide({ masterName: "SECTION", sectionTitle: "Démonstration" });
  s.addText("Démonstration en direct", { placeholder: "title" });
  const demo = [
    ["L'endpoint", "anonyme puis authentifié"],
    ["Répartition", "deux instances alternent"],
    ["DOWN injecté", "zéro erreur côté client"],
    ["Arrêt brutal", "l'écart Eureka / Actuator"],
    ["Base coupée", "DOWN, conteneur « healthy »"],
    ["Rupture de stock", "DEGRADE, toujours routé"],
  ];
  for (let i = 0; i < 6; i++) {
    const x = M + (i % 3) * 4.1, y = 2.0 + Math.floor(i / 3) * 2.3;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: 3.8, h: 1.95, rectRadius: 0.12, fill: { color: "174B50" }, line: { color: "174B50" }, objectName: nom("etape-demo") });
    texte(s, String(i + 1), { x: x + 0.3, y: y + 0.25, w: 0.6, h: 0.6, fontSize: 30, bold: true, color: "9FD8C8" });
    texte(s, [{ text: demo[i][0], options: { bold: true, breakLine: true } }, { text: demo[i][1], options: { fontSize: 16, color: "CFE3DE" } }],
      { x: x + 0.3, y: y + 0.85, w: 3.3, h: 1.0, fontSize: 20, color: C.background1 });
  }
  s.addNotes("Présente : Ismaila (manipulation), Moussa (explications). 7 min. Les 6 cartes correspondent aux étapes 2:00, 5:30, 7:00, 9:30, 12:00 et 14:30 de docs/demonstration.md ; la console Eureka (4:00) et les alertes (16:00) ne sont montrées que s'il reste du temps. AVANT : verifier.py doit afficher « Tout est conforme », puis attendre 1 minute. Plan de secours : docker compose up -d --force-recreate --wait, ou montrer les captures du rapport.");

  // ===== 11. Mesures
  pres.addSection({ title: "Mesures" });
  s = contenu("Réglages d'usine contre réglages du projet", "Mesures");
  s.addChart(pres.charts.BAR, [
    { name: "Réglages du projet", labels: ["DOWN vu dans Eureka", "Fin du trafic vers l'instance DOWN", "Arrêt propre : dernière erreur", "Base coupée : DOWN dans Eureka"], values: [4.6, 8.0, 7.0, 7.7] },
    { name: "Réglages d'usine", labels: ["DOWN vu dans Eureka", "Fin du trafic vers l'instance DOWN", "Arrêt propre : dernière erreur", "Base coupée : DOWN dans Eureka"], values: [28.6, 57.6, 57.7, 33.9] },
  ], {
    x: M, y: 1.45, w: 8.3, h: 5.2, barDir: "bar", barGrouping: "clustered", barGapWidthPct: 60,
    chartColors: [H.accent1, H.accent4],
    showValue: true, dataLabelPosition: "outEnd", dataLabelFormatCode: '0.0" s"', dataLabelFontSize: 12, dataLabelColor: H.dk1, dataLabelFontFace: "+mn-lt",
    catAxisLabelColor: H.dk1, catAxisLabelFontSize: 13, catAxisLabelFontFace: "+mn-lt", catAxisOrientation: "maxMin",
    valAxisHidden: true, valGridLine: { style: "none" }, catGridLine: { style: "none" },
    showLegend: true, legendPos: "t", legendFontSize: 13, legendFontFace: "+mn-lt", legendColor: H.dk1,
    showTitle: true, title: "Secondes, une exécution par scénario", titleFontSize: 13, titleColor: H.accent4, titleFontFace: "+mn-lt",
    objectName: "graphique-mesures",
  });
  carte(s, 9.25, 1.55, 3.5, 2.35);
  texte(s, "×7", { x: 9.55, y: 1.75, w: 2.9, h: 1.0, fontSize: 54, bold: true, color: C.accent1 });
  texte(s, "plus lent avec les réglages d'usine, pour arrêter le trafic vers une instance DOWN", { x: 9.55, y: 2.75, w: 2.95, h: 1.0, fontSize: 14 });
  carte(s, 9.25, 4.15, 3.5, 2.4);
  texte(s, "0", { x: 9.55, y: 4.35, w: 2.9, h: 1.0, fontSize: 54, bold: true, color: C.accent1 });
  texte(s, "erreur client quand l'instance se déclare DOWN elle-même : le registre a le temps de prévenir", { x: 9.55, y: 5.35, w: 2.95, h: 1.05, fontSize: 14 });
  s.addNotes("Présente : Ismaila. 1 min. Chaque scénario joué une fois, sur ce Mac : ce sont des ordres de grandeur. Usine = 30 s de battement, 90 s de bail, 35 s de cache du répartiteur. Projet = 5 s, 15 s, 5 s. 57,6 / 8,0 ≈ 7.");

  // ===== 12. Quatre étages
  s = contenu("Quatre caches entre un DOWN et l'arrêt du trafic", "Mesures");
  const etages = [["Le client pousse son statut", "30 s", "5 s"], ["Cache du serveur Eureka", "30 s", "3 s"], ["Copie locale de la passerelle", "30 s", "5 s"], ["Cache du répartiteur", "35 s", "5 s"]];
  await pastille(s, M, 2.35, 0.9, H.accent2, fa.FaTimes);
  texte(s, "/health DOWN", { x: M - 0.2, y: 3.35, w: 1.3, h: 0.5, fontSize: 14, bold: true, align: "center", color: C.text2 });
  for (let i = 0; i < 4; i++) {
    const x = 2.05 + i * 2.72;
    s.addShape(pres.shapes.RIGHT_ARROW, { x: x - 0.48, y: 2.65, w: 0.38, h: 0.35, fill: { color: H.accent4 }, line: { color: H.accent4 }, objectName: nom("fleche") });
    carte(s, x, 1.85, 2.4, 2.0);
    texte(s, etages[i][0], { x: x + 0.2, y: 2.0, w: 2.05, h: 0.75, fontSize: 16, bold: true, color: C.text2 });
    texte(s, [{ text: "usine ", options: { color: C.accent4 } }, { text: etages[i][1], options: { bold: true } }], { x: x + 0.2, y: 2.85, w: 2.05, h: 0.4, fontSize: 16 });
    texte(s, [{ text: "projet ", options: { color: C.accent4 } }, { text: etages[i][2], options: { bold: true, color: C.accent1 } }], { x: x + 0.2, y: 3.25, w: 2.05, h: 0.4, fontSize: 16 });
  }
  carte(s, M, 4.4, 5.8, 2.1);
  texte(s, "125 s", { x: M + 0.35, y: 4.55, w: 2.6, h: 1.0, fontSize: 48, bold: true, color: C.accent2 });
  texte(s, "pire cas, réglages d'usine", { x: M + 0.35, y: 5.55, w: 2.7, h: 0.6, fontSize: 15 });
  texte(s, "réglages du projet", { x: 3.85, y: 5.55, w: 2.3, h: 0.6, fontSize: 15 });
  texte(s, "18 s", { x: 3.85, y: 4.55, w: 2.4, h: 1.0, fontSize: 48, bold: true, color: C.accent1 });
  texte(s, [{ text: "Accélérer un seul étage ne suffit pas.", options: { bold: true, breakLine: true } },
    { text: "Le cache du répartiteur (35 s) est le plus souvent oublié. Mais tout réduire augmente le trafic vers Eureka et expulse à tort les instances lentes." }],
    { x: 6.85, y: 4.45, w: 5.85, h: 2.0, fontSize: 17 });
  s.addNotes("Présente : Ismaila. 1 min. Observé : 57,6 s en usine, cohérent avec le pire cas de 125 s puisque chaque étage est traversé en moyenne à mi-période.");

  // ===== 13. Deux surprises
  s = contenu("Deux surprises révélées par les mesures", "Mesures");
  carte(s, M, 1.55, 5.9, 4.95);
  texte(s, "Le bail compte double", { x: M + 0.4, y: 1.8, w: 5.1, h: 0.5, fontSize: 22, bold: true, color: C.text2 });
  texte(s, "31,9 s", { x: M + 0.4, y: 2.45, w: 5.1, h: 1.1, fontSize: 60, bold: true, color: C.accent2 });
  texte(s, "pour expulser une instance tuée, avec un bail configuré à 15 s", { x: M + 0.4, y: 3.6, w: 5.1, h: 0.7, fontSize: 16 });
  code(s, "renew()    : fin = maintenant + durée\nisExpired(): maintenant > fin + durée", { x: M + 0.4, y: 4.5, w: 5.1, h: 0.85, fontSize: 13 });
  texte(s, "Lu dans le code compilé d'Eureka (classe Lease)", { x: M + 0.4, y: 5.55, w: 5.1, h: 0.6, fontSize: 13, color: C.accent4, italic: true });
  carte(s, 6.85, 1.55, 5.85, 4.95);
  texte(s, "Jamais expulsée", { x: 7.25, y: 1.8, w: 5.1, h: 0.5, fontSize: 22, bold: true, color: C.text2 });
  texte(s, "49 %", { x: 7.25, y: 2.45, w: 5.1, h: 1.1, fontSize: 60, bold: true, color: C.accent2 });
  texte(s, "des requêtes en échec pendant 412\u00A0s,\naprès un kill\u00A0-9 (réglages d'usine)", { x: 7.25, y: 3.6, w: 5.1, h: 0.75, fontSize: 16 });
  texte(s, [{ text: "Explication probable : l'auto-préservation. ", options: { bold: true } },
    { text: "Moins de 85 % des battements reçus → Eureka croit à une coupure réseau et n'expulse plus personne. Avec nos réglages : dernière erreur à 36 s." }],
    { x: 7.25, y: 4.45, w: 5.1, h: 1.8, fontSize: 15 });
  s.addNotes("Présente : Moussa. 1 min 30. Nous pensions que 15 s de bail donneraient 15 s : la mesure a contredit l'hypothèse, le code a expliqué pourquoi. Pour l'auto-préservation, dire clairement que c'est une interprétation.");

  // ===== 14. Difficultés
  pres.addSection({ title: "Bilan" });
  s = contenu("Six défauts trouvés en faisant tourner la pile", "Bilan");
  const pb = [
    [fa.FaNetworkWired, "Mauvaise adresse", "Une instance annonçait l'IP du réseau de la base. Notre supervision l'a vu."],
    [fa.FaHourglassEnd, "Passerelle sans délai", "Bloquée plusieurs minutes face à une adresse muette."],
    [fa.FaSync, "Boucle Eureka / Actuator", "/health relisait son propre statut dans Eureka."],
    [fa.FaStopwatch, "Délais additionnés", "2 indicateurs × 2 s > 3 s de sonde : la cause disparaissait."],
    [fa.FaKey, "401 sur une page publique", "Un mauvais compte est refusé, même là où l'anonyme passe."],
    [fa.FaWindows, "Test instable sous Windows", "Trouvé par la CI, corrigé après preuve, pas à l'intuition."],
  ];
  for (let i = 0; i < 6; i++) {
    const x = M + (i % 3) * 4.1, y = 1.55 + Math.floor(i / 3) * 2.55;
    carte(s, x, y, 3.8, 2.3);
    await pastille(s, x + 0.3, y + 0.3, 0.6, [H.accent2, H.accent3, H.accent5, H.accent3, H.accent6, H.accent4][i], pb[i][0]);
    texte(s, pb[i][1], { x: x + 1.05, y: y + 0.3, w: 2.55, h: 0.65, fontSize: 17, bold: true, color: C.text2, valign: "middle" });
    texte(s, pb[i][2], { x: x + 0.3, y: y + 1.1, w: 3.25, h: 1.05, fontSize: 15 });
  }
  s.addNotes("Présentent : Moussa (1 à 5), Ismaila (6). 1 min. Tous les tests unitaires passaient : ces défauts ne sont apparus qu'en exécutant la vraie pile. C'est l'argument pour une supervision active et des essais de bout en bout. Si le temps manque, passer cette slide.");

  // ===== 15. Sécurité
  s = contenu("Un endpoint de santé ne doit rien divulguer", "Bilan");
  carte(s, M, 1.55, 3.9, 4.95);
  texte(s, "Vulnérabilités connues", { x: M + 0.35, y: 1.8, w: 3.3, h: 0.45, fontSize: 17, bold: true, color: C.text2 });
  texte(s, "47 → 3", { x: M + 0.35, y: 2.35, w: 3.4, h: 1.0, fontSize: 48, bold: true, color: C.accent1 });
  texte(s, "Java : 5 → 0. Les 3 restantes : expat, non utilisée par notre code. Analyse OSV et Trivy en CI.", { x: M + 0.35, y: 3.45, w: 3.25, h: 1.5, fontSize: 15 });
  texte(s, "docs/securite.md", { x: M + 0.35, y: 5.75, w: 3.3, h: 0.4, fontSize: 13, color: C.accent4, italic: true });
  const secu = [
    [fa.FaUserSecret, "Actuator en liste blanche", "détails réservés, env et heapdump absents"],
    [fa.FaLock, "Registre Eureka protégé", "sinon : fausse instance, trafic détourné"],
    [fa.FaUserShield, "Panne simulée réservée à ADMIN", "et désactivée par défaut"],
    [fa.FaBox, "Conteneurs durcis", "non root, lecture seule, ports sur 127.0.0.1"],
  ];
  for (let i = 0; i < 4; i++) {
    const y = 1.6 + i * 1.23;
    await pastille(s, 4.9, y, 0.6, H.dk2, secu[i][0]);
    texte(s, [{ text: secu[i][1], options: { bold: true, breakLine: true } }, { text: secu[i][2], options: { fontSize: 15, color: C.accent4 } }],
      { x: 5.75, y: y - 0.02, w: 6.95, h: 1.0, fontSize: 18, color: C.text2 });
  }
  s.addNotes("Présente : Moussa. 1 min. Limites assumées : HTTP clair entre services, authentification Basic sans limite d'essais, API métier ouvertes. Acceptable sur un poste local, pas en production.");

  // ===== 16. Qualité
  s = contenu("Testé à chaque push, sur trois systèmes", "Bilan");
  const stats = [["51", "tests Java"], ["9", "tests de l'interface"], ["19", "contrôles de bout en bout"], ["94 063", "requêtes k6, 0 erreur"]];
  for (let i = 0; i < 4; i++) {
    const x = M + i * 3.07;
    carte(s, x, 1.55, 2.82, 2.0);
    texte(s, stats[i][0], { x: x + 0.3, y: 1.7, w: 2.3, h: 0.95, fontSize: 44, bold: true, color: C.accent1 });
    texte(s, stats[i][1], { x: x + 0.3, y: 2.7, w: 2.3, h: 0.7, fontSize: 15 });
  }
  const ci = ["Linux", "macOS", "Windows", "Pile complète", "Trivy"];
  texte(s, "GitHub Actions, dernier passage", { x: M, y: 4.0, w: 6, h: 0.4, fontSize: 17, bold: true, color: C.text2 });
  for (let i = 0; i < 5; i++) {
    const x = M + i * 2.45;
    await pastille(s, x, 4.6, 0.55, H.accent1, fa.FaCheck);
    texte(s, ci[i], { x: x + 0.7, y: 4.6, w: 1.65, h: 0.55, fontSize: 16, valign: "middle" });
  }
  texte(s, [{ text: "/actuator/health : ", options: { bold: true } }, { text: "4,8\u00A0ms au 95e centile à 100 requêtes/s. Sonder toutes les 5\u00A0s ne coûte presque rien." }],
    { x: M, y: 5.6, w: 12.1, h: 0.7, fontSize: 17 });
  s.addNotes("Présente : Ismaila. 1 min. Couverture Java 75 %, 86 % sur la supervision. La CI teste Windows parce que Moussa travaille sous Windows : c'est elle qui a trouvé le test instable.");

  // ===== 17. Ce que nous retenons
  s = contenu("Ce que nous retenons", "Bilan");
  const lecons = [
    [fa.FaHeartbeat, "Demander au service lui-même", "Lancé ne veut pas dire en état de servir."],
    [fa.FaBalanceScale, "Registre et sonde : les deux", "L'un sait ce qu'on lui déclare, l'autre ce qu'elle constate."],
    [fa.FaLayerGroup, "Regarder les caches", "Le délai perçu est la somme de caches que personne ne regarde."],
    [fa.FaRuler, "Mesurer avant de croire", "Deux de nos hypothèses étaient fausses."],
  ];
  for (let i = 0; i < 4; i++) {
    const x = M + (i % 2) * 6.15, y = 1.55 + Math.floor(i / 2) * 1.75;
    await pastille(s, x, y + 0.1, 0.75, H.accent1, lecons[i][0]);
    texte(s, [{ text: lecons[i][1], options: { bold: true, breakLine: true } }, { text: lecons[i][2], options: { fontSize: 16 } }],
      { x: x + 1.0, y, w: 4.85, h: 1.4, fontSize: 20, color: C.text2 });
  }
  carte(s, M, 5.15, 12.1, 1.4);
  texte(s, [{ text: "Et ensuite : ", options: { bold: true } },
    { text: "Eureka en cluster avec auto-préservation, nouvel essai sur une autre instance dans la passerelle, arrêt en deux temps, alertes par courriel, et lien avec les logs (notre sujet B)." }],
    { x: M + 0.35, y: 5.3, w: 11.4, h: 1.1, fontSize: 17, valign: "middle" });
  s.addNotes("Présentent : Ismaila puis Moussa. 1 min. Terminer sur la phrase « mesurer avant de croire ».");

  // ===== 18. Questions
  s = pres.addSlide({ masterName: "TITRE", sectionTitle: "Bilan" });
  s.addText("Merci, place aux questions", { placeholder: "title" });
  s.addText("github.com/iamsouane/sante-uadb", { placeholder: "body" });
  await pastille(s, M, 1.0, 0.9, H.accent1, fa.FaCheck);
  texte(s, "Rapport : docs/rapport.pdf · Démonstration : docs/demonstration.md · Questions probables préparées en fin de démonstration.md",
    { x: M, y: 5.8, w: 12, h: 0.6, fontSize: 15, color: "CFE3DE" });
  s.addNotes("Les deux. Questions probables et réponses : fin de docs/demonstration.md (liveness/readiness, auto-préservation, Eureka en panne, Spring Boot Admin, Kubernetes).");

  await pres.writeFile({ fileName: SORTIE });
  await appliquerTheme(SORTIE, THEME);
  console.log("écrit", SORTIE);
})().catch((e) => { console.error(e); process.exit(1); });
