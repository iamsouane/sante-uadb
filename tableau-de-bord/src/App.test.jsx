import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Instance, Pastille } from './App.jsx';
import { creerApi } from './api.js';
import { decrire, duree, resumer } from './statuts.js';

const instance = (plus) => ({
  id: 'catalogue-service:abc:8081', statutEureka: 'UP', sante: 'UP', divergence: false, latenceMs: 4,
  composants: { db: 'UP', panneSimulee: 'UP' }, detail: '', depuis: null, ...plus,
});
const afficher = (i, props) => render(<table><tbody><Instance instance={i} {...props} /></tbody></table>);

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('statuts', () => {
  it('décrit un état inconnu sans planter', () => expect(decrire('XYZ').libelle).toBe('Inconnu'));
  it('formate les durées', () => {
    const t = Date.parse('2026-11-25T10:00:00Z');
    expect(duree('2026-11-25T09:59:30Z', t)).toBe('30 s');
    expect(duree('2026-11-25T09:57:55Z', t)).toBe('2 min 5 s');
    expect(duree(null)).toBe('–');
  });
  it('compte les instances disponibles et les écarts', () => {
    const r = resumer({ services: [{ etat: 'DEGRADE', instances: [instance(), instance({ sante: 'DOWN', divergence: true })] }] });
    expect(r).toEqual({ servicesDisponibles: 1, services: 1, instancesDisponibles: 1, instances: 2, divergences: 1 });
  });
});

describe('affichage', () => {
  it('donne toujours un texte en plus de la couleur', () => {
    render(<Pastille etat="DOWN" />);
    expect(screen.getByText('DOWN')).toBeTruthy();
  });
  it('montre l’écart et le composant fautif', () => {
    afficher(instance({ sante: 'DOWN', divergence: true, composants: { db: 'DOWN' }, detail: 'db=DOWN' }));
    expect(screen.getByText('écart')).toBeTruthy();
    expect(screen.getByText('db : DOWN')).toBeTruthy();
  });
  it('cache l’injection de panne hors admin', () => {
    afficher(instance(), { admin: false });
    expect(screen.queryByRole('combobox')).toBeNull();
  });
  it('injecte puis rétablit', () => {
    const api = { injecter: vi.fn(), retablir: vi.fn() };
    const { rerender } = afficher(instance(), { admin: true, api, agir: (f) => f() });
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'DOWN' } });
    expect(api.injecter).toHaveBeenCalledWith('catalogue-service:abc:8081', { statut: 'DOWN', raison: 'démonstration' });
    rerender(<table><tbody><Instance instance={instance({ composants: { panneSimulee: 'DOWN' } })} admin api={api} agir={(f) => f()} /></tbody></table>);
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'UP' } });
    expect(api.retablir).toHaveBeenCalled();
  });
});

describe('api', () => {
  it('encode l’identifiant et transmet l’authentification', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, status: 204 });
    vi.stubGlobal('fetch', fetch);
    await creerApi('admin', 'é').retablir('a:b/c');
    expect(fetch.mock.calls[0][0]).toBe('/api/supervision/instances/a%3Ab%2Fc/panne');
    expect(fetch.mock.calls[0][1].headers.Authorization).toMatch(/^Basic /);
  });
  it('signale les erreurs HTTP', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }));
    await expect(creerApi('a', 'b').etat()).rejects.toMatchObject({ statut: 401 });
  });
});
