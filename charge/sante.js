// Test de charge k6 : le coût de /actuator/health et l'impact sur l'API métier.
// Lancement : voir README (image Docker grafana/k6, rien à installer).
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://api-gateway:8080';
const CATALOGUE = __ENV.CATALOGUE || 'http://catalogue-service:8081';

const palier = (exec, startTime) => ({
  executor: 'constant-arrival-rate', exec, startTime,
  rate: Number(__ENV.DEBIT || 100), timeUnit: '1s', duration: __ENV.DUREE || '30s',
  preAllocatedVUs: 50, maxVUs: 200,
});

export const options = {
  scenarios: {
    // Chauffe la JVM (compilation JIT) ; non soumise aux seuils de durée
    chauffe: { executor: 'constant-vus', exec: 'chauffe', vus: 5, duration: '30s' },
    vivacite: palier('vivacite', '35s'),
    preparation: palier('preparation', '70s'),
    sante: palier('sante', '105s'),
    metier: palier('metier', '140s'),
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{scenario:vivacite}': ['p(95)<200'],
    'http_req_duration{scenario:preparation}': ['p(95)<300'],
    'http_req_duration{scenario:sante}': ['p(95)<300'],
    'http_req_duration{scenario:metier}': ['p(95)<500'],
    // Déclarés pour que k6 affiche le détail par scénario
    'http_reqs{scenario:vivacite}': ['count>0'],
    'http_reqs{scenario:preparation}': ['count>0'],
    'http_reqs{scenario:sante}': ['count>0'],
    'http_reqs{scenario:metier}': ['count>0'],
    'dropped_iterations': ['count<100'],
  },
};

const ok = (r) => check(r, { 'statut 200': (x) => x.status === 200 });

export function chauffe() { vivacite(); preparation(); sante(); metier(); }
export function vivacite() { ok(http.get(`${CATALOGUE}/actuator/health/liveness`)); }
export function preparation() { ok(http.get(`${CATALOGUE}/actuator/health/readiness`)); }
export function sante() { ok(http.get(`${CATALOGUE}/actuator/health`)); }
export function metier() { ok(http.get(`${BASE}/api/produits`)); }
