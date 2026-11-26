const BASE = '/api/supervision';

export class ErreurApi extends Error {
  constructor(statut) {
    super(`HTTP ${statut}`);
    this.statut = statut;
  }
}

/** Client de l'API. Les identifiants restent en mémoire : ni localStorage, ni cookie. */
export function creerApi(utilisateur, motDePasse) {
  const octets = new TextEncoder().encode(`${utilisateur}:${motDePasse}`);
  const jeton = `Basic ${btoa(String.fromCharCode(...octets))}`;

  async function appeler(chemin, options = {}) {
    const reponse = await fetch(BASE + chemin, {
      ...options,
      headers: { Authorization: jeton, ...(options.body && { 'Content-Type': 'application/json' }) },
      signal: AbortSignal.timeout(5000),
    });
    if (!reponse.ok) throw new ErreurApi(reponse.status);
    return reponse.status === 204 ? null : reponse.json();
  }

  const panne = (id) => `/instances/${encodeURIComponent(id)}/panne`;
  return {
    etat: () => appeler('/etat'),
    evenements: () => appeler('/evenements?limite=40'),
    injecter: (id, demande) => appeler(panne(id), { method: 'PUT', body: JSON.stringify(demande) }),
    retablir: (id) => appeler(panne(id), { method: 'DELETE' }),
  };
}
