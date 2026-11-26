import { useCallback, useEffect, useState } from 'react';
import { creerApi } from './api.js';
import { decrire, duree, resumer } from './statuts.js';

const RAFRAICHISSEMENT_MS = 2000;

export default function App() {
  const [session, setSession] = useState(null);
  return session ? (
    <TableauDeBord api={session.api} admin={session.admin} quitter={() => setSession(null)} />
  ) : (
    <Connexion ouvrir={setSession} />
  );
}

function Connexion({ ouvrir }) {
  const [erreur, setErreur] = useState('');

  async function soumettre(e) {
    e.preventDefault();
    const donnees = new FormData(e.currentTarget);
    const utilisateur = donnees.get('utilisateur');
    const api = creerApi(utilisateur, donnees.get('motDePasse'));
    try {
      await api.etat();
      // Simple confort d'affichage : c'est le serveur qui contrôle le rôle ADMIN
      ouvrir({ api, admin: utilisateur === 'admin' });
    } catch (err) {
      setErreur(err.statut === 401 ? 'Identifiants refusés.' : 'Service de supervision injoignable.');
    }
  }

  return (
    <main className="connexion">
      <form onSubmit={soumettre}>
        <h1>Supervision des services</h1>
        <label>
          Compte
          <input name="utilisateur" defaultValue="supervision" autoComplete="username" required />
        </label>
        <label>
          Mot de passe
          <input name="motDePasse" type="password" autoComplete="current-password" required />
        </label>
        {erreur && <p role="alert" className="erreur">{erreur}</p>}
        <button className="principal">Se connecter</button>
      </form>
    </main>
  );
}

function TableauDeBord({ api, admin, quitter }) {
  const [etat, setEtat] = useState(null);
  const [evenements, setEvenements] = useState([]);
  const [erreur, setErreur] = useState('');

  const charger = useCallback(async () => {
    try {
      const [e, h] = await Promise.all([api.etat(), api.evenements()]);
      setEtat(e);
      setEvenements(h);
      setErreur('');
    } catch (err) {
      if (err.statut === 401) quitter();
      setErreur('Supervision injoignable : les états affichés ne sont plus à jour.');
    }
  }, [api, quitter]);

  useEffect(() => {
    charger();
    const minuterie = setInterval(charger, RAFRAICHISSEMENT_MS);
    return () => clearInterval(minuterie);
    // quitter change à chaque rendu du parent : seul api doit relancer la minuterie
  }, [api]);

  async function agir(action) {
    try {
      await action();
    } catch (err) {
      setErreur(err.statut === 403 ? 'Action réservée au compte admin.' : 'Commande refusée par l’instance.');
      return;
    }
    charger();
  }

  if (!etat) return <p className="attente">{erreur || 'Chargement…'}</p>;
  const r = resumer(etat);

  return (
    <div className="page">
      <header>
        <div>
          <p className="surtitre">État global</p>
          <p className={`heros ${decrire(etat.global).ton}`}>
            <Pastille etat={etat.global} grande />
          </p>
        </div>
        <button onClick={quitter}>Se déconnecter</button>
      </header>

      {erreur && <p role="alert" className="bandeau">{erreur}</p>}

      <section className="tuiles" aria-label="Synthèse">
        <Tuile libelle="Services rendus" valeur={`${r.servicesDisponibles} / ${r.services}`} />
        <Tuile libelle="Instances disponibles" valeur={`${r.instancesDisponibles} / ${r.instances}`} />
        <Tuile libelle="Écarts Eureka / Actuator" valeur={r.divergences} />
        <Tuile libelle="Registre Eureka" valeur={decrire(etat.eureka).libelle} />
      </section>

      {etat.services.map((s) => (
        <Service key={s.nom} service={s} admin={admin} api={api} agir={agir} />
      ))}

      <Journal evenements={evenements} />
    </div>
  );
}

const Tuile = ({ libelle, valeur }) => (
  <div className="tuile">
    <p>{libelle}</p>
    <strong>{valeur}</strong>
  </div>
);

export function Pastille({ etat, grande }) {
  const d = decrire(etat);
  return (
    <span className={`pastille ${d.ton} ${grande ? 'grande' : ''}`}>
      <span aria-hidden="true" className="icone">{d.icone}</span>
      {d.libelle}
    </span>
  );
}

function Service({ service, admin, api, agir }) {
  return (
    <section className="service">
      <div className="titre">
        <h2>{service.nom}</h2>
        <Pastille etat={service.etat} />
        <span className="secondaire">
          {service.instancesDisponibles} instance(s) sur {service.minimum} attendue(s), disponibilité {service.disponibilite} %
        </span>
      </div>
      {service.instances.length === 0 ? (
        <p className="secondaire">Aucune instance connue : le service attendu n’a jamais été enregistré.</p>
      ) : (
        <div className="defilement">
          <table>
            <thead>
              <tr>
                <th>Instance</th><th>Eureka</th><th>Actuator</th><th>Composants</th>
                <th className="nombre">Sonde</th><th className="nombre">Depuis</th>{admin && <th>Panne simulée</th>}
              </tr>
            </thead>
            <tbody>
              {service.instances.map((i) => (
                <Instance key={i.id} instance={i} admin={admin} api={api} agir={agir} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

export function Instance({ instance: i, admin, api, agir }) {
  return (
    <tr>
      <td className="identifiant">{i.id.split(':').slice(1).join(':')}</td>
      <td><Pastille etat={i.statutEureka} /></td>
      <td>
        <Pastille etat={i.sante} />
        {i.divergence && <span className="ecart" title="Eureka et Actuator ne disent pas la même chose">écart</span>}
      </td>
      <td className="composants">
        {Object.entries(i.composants).filter(([, s]) => s !== 'UP' && s !== 'UNKNOWN')
          .map(([nom, s]) => <span key={nom}>{nom} : {decrire(s).libelle}</span>)}
        {i.detail && Object.keys(i.composants).length === 0 && <span>{i.detail}</span>}
        {Object.keys(i.composants).length > 0 && !i.detail && (
          <span className="secondaire">{Object.keys(i.composants).length} vérifiés</span>
        )}
      </td>
      <td className="nombre">{i.latenceMs} ms</td>
      <td className="nombre">{duree(i.depuis)}</td>
      {admin && (
        <td>
          {i.composants.panneSimulee ? (
            <select
              aria-label={`Panne simulée sur ${i.id}`}
              value={i.composants.panneSimulee}
              onChange={(e) => {
                const statut = e.target.value;
                agir(() => (statut === 'UP' ? api.retablir(i.id) : api.injecter(i.id, { statut, raison: 'démonstration' })));
              }}
            >
              <option value="UP">Aucune</option>
              <option value="DEGRADE">Dégradé</option>
              <option value="OUT_OF_SERVICE">Hors service</option>
              <option value="DOWN">DOWN</option>
            </select>
          ) : <span className="secondaire">–</span>}
        </td>
      )}
    </tr>
  );
}

function Journal({ evenements }) {
  return (
    <section className="service">
      <h2>Journal des changements d’état</h2>
      {evenements.length === 0 ? <p className="secondaire">Aucun changement.</p> : (
        <div className="defilement">
          <table>
            <thead><tr><th>Heure</th><th>Instance</th><th>De</th><th>Vers</th><th>Détail</th></tr></thead>
            <tbody>
              {evenements.map((e) => (
                <tr key={`${e.horodatage}${e.instance}`}>
                  <td className="nombre gauche">{new Date(e.horodatage).toLocaleTimeString('fr-FR')}</td>
                  <td className="identifiant">{e.instance}</td>
                  <td><Pastille etat={e.de} /></td>
                  <td><Pastille etat={e.vers} /></td>
                  <td className="secondaire">{e.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
