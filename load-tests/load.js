// Prueba de carga (spec 015, SC-001): navegación con rampa de 2 min hasta 200 usuarios, 5 min sostenidos y 1 min de
// bajada (cada usuario inicia sesión una vez y reutiliza su token), más logins a ritmo fijo (LOGIN_RATE, 5/s por
// defecto) durante toda la corrida. Falla (código distinto de cero) si algún flujo supera p95 de 2 s o 1 % de
// errores.
import { flowThresholds, loginIteration, loginScenario, navigationIteration } from './lib/flows.js';
import { setup, teardown, writeSummary } from './lib/report.js';

export { setup, teardown };

const TARGET = Number(__ENV.TARGET_VUS || 200);

export const options = {
  scenarios: {
    navegacion: {
      executor: 'ramping-vus',
      exec: 'navigation',
      startVUs: 0,
      stages: [
        { duration: '2m', target: TARGET },
        { duration: '5m', target: TARGET },
        { duration: '1m', target: 0 },
      ],
    },
    login: loginScenario('8m'),
  },
  thresholds: flowThresholds(),
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function navigation() {
  navigationIteration();
}

export function loginFlow() {
  loginIteration();
}

export function handleSummary(data) {
  return writeSummary('load', data);
}
