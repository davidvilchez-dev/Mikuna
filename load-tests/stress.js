// Prueba de estrés (spec 015, SC-001 / RNF-01 parcial): escalones de 100 en 100 usuarios hasta 600, 2 min cada
// uno, de usuarios navegando (sesión reutilizada), más logins a ritmo fijo (LOGIN_RATE). Cada petición lleva la
// etiqueta del escalón; el informe dice desde qué escalón se incumple p95 < 2 s o errores < 1 %. Los umbrales no
// cortan la corrida: el objetivo es encontrar el límite.
import exec from 'k6/execution';
import { loginIteration, loginScenario, navigationIteration } from './lib/flows.js';
import { setup, teardown, writeSummary } from './lib/report.js';

export { setup, teardown };

const STEP = 100;
const STEPS = 6;
const STEP_MS = 2 * 60 * 1000;

const stages = [];
const thresholds = {};
for (let i = 1; i <= STEPS; i++) {
  stages.push({ duration: '30s', target: i * STEP }, { duration: '90s', target: i * STEP });
  thresholds[`http_req_duration{escalon:${i * STEP}}`] = ['p(95)<2000'];
  thresholds[`http_req_failed{escalon:${i * STEP}}`] = ['rate<0.01'];
}
stages.push({ duration: '1m', target: 0 });
// Los flujos también se reportan, sin cortar la corrida.
for (const flow of ['catalogo', 'login', 'pedido']) {
  thresholds[`http_req_duration{flow:${flow}}`] = ['p(95)<2000'];
  thresholds[`http_req_failed{flow:${flow}}`] = ['rate<0.01'];
}

export const options = {
  scenarios: {
    navegacion: { executor: 'ramping-vus', exec: 'navigation', startVUs: 0, stages },
    login: loginScenario(`${STEPS * 2 + 1}m`),
  },
  thresholds,
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function tagStep() {
  const step = Math.min(STEPS, Math.floor(exec.instance.currentTestRunDuration / STEP_MS) + 1);
  exec.vu.metrics.tags.escalon = String(step * STEP);
}

export function navigation() {
  tagStep();
  navigationIteration();
}

export function loginFlow() {
  tagStep();
  loginIteration();
}

export function handleSummary(data) {
  const lines = ['', '## Escalones', '', '| Usuarios | p95 | Errores | Cumple |', '|----------|-----|---------|--------|'];
  let firstFailure = null;
  for (let i = 1; i <= STEPS; i++) {
    const users = i * STEP;
    const duration = data.metrics[`http_req_duration{escalon:${users}}`];
    const failed = data.metrics[`http_req_failed{escalon:${users}}`];
    if (!duration) {
      continue;
    }
    const p95 = duration.values['p(95)'];
    const errors = failed ? failed.values.rate : 0;
    const ok = p95 < 2000 && errors < 0.01;
    if (!ok && firstFailure === null) {
      firstFailure = users;
    }
    lines.push(`| ${users} | ${Math.round(p95)} ms | ${(errors * 100).toFixed(2)} % | ${ok ? '✅' : '❌'} |`);
  }
  lines.push('', firstFailure === null
    ? `Límite: no se alcanzó hasta ${STEPS * STEP} usuarios.`
    : `Límite: los umbrales se incumplen desde ${firstFailure} usuarios concurrentes.`);
  return writeSummary('stress', data, lines);
}
