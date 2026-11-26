/** Libellé, icône et gravité de chaque état : la couleur n'est jamais seule à porter l'information. */
const STATUTS = {
  UP: { libelle: 'UP', icone: '✓', ton: 'bon' },
  DEGRADE: { libelle: 'Dégradé', icone: '!', ton: 'alerte' },
  OUT_OF_SERVICE: { libelle: 'Hors service', icone: '⏸', ton: 'serieux' },
  DOWN: { libelle: 'DOWN', icone: '✕', ton: 'critique' },
  INJOIGNABLE: { libelle: 'Injoignable', icone: '✕', ton: 'critique' },
  STARTING: { libelle: 'Démarrage', icone: '…', ton: 'neutre' },
  ABSENTE: { libelle: 'Absente', icone: '–', ton: 'neutre' },
};

export const decrire = (etat) => STATUTS[etat] ?? { libelle: 'Inconnu', icone: '?', ton: 'neutre' };

export function duree(depuis, maintenant = Date.now()) {
  if (!depuis) return '–';
  const s = Math.max(0, Math.round((maintenant - new Date(depuis).getTime()) / 1000));
  if (s < 60) return `${s} s`;
  if (s < 3600) return `${Math.floor(s / 60)} min ${s % 60} s`;
  return `${Math.floor(s / 3600)} h ${Math.floor((s % 3600) / 60)} min`;
}

export function resumer(etat) {
  const instances = etat.services.flatMap((s) => s.instances);
  const disponible = (e) => e === 'UP' || e === 'DEGRADE';
  return {
    servicesDisponibles: etat.services.filter((s) => disponible(s.etat)).length,
    services: etat.services.length,
    instancesDisponibles: instances.filter((i) => disponible(i.sante)).length,
    instances: instances.length,
    divergences: instances.filter((i) => i.divergence).length,
  };
}
