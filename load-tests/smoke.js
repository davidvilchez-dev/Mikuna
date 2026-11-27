// Prueba de humo (spec 015): 2 usuarios navegando 30 s (catálogo y pedido con sesión reutilizada) y 1 login por
// segundo. Debe pasar siempre.
import { flowThresholds, loginIteration, navigationIteration } from './lib/flows.js';
import { setup, teardown, writeSummary } from './lib/report.js';

export { setup, teardown };

export const options = {
  scenarios: {
    navegacion: { executor: 'constant-vus', exec: 'navigation', vus: 2, duration: '30s' },
    login: {
      executor: 'constant-arrival-rate', exec: 'loginFlow', rate: 1, timeUnit: '1s', duration: '30s',
      preAllocatedVUs: 2, maxVUs: 5,
    },
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
  return writeSummary('smoke', data);
}
