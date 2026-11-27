// Informe reproducible de cada corrida (spec 015, FR-019 y FR-020): versión, datos, configuración, percentiles y
// errores por flujo, umbrales cumplidos y proporción de aciertos de la caché.
import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { PASSWORD, urls } from './flows.js';

const CACHES = ['public-restaurants', 'public-catalog'];
const cacheHits = new Counter('cache_hits');
const cacheMisses = new Counter('cache_misses');

function adminToken(base) {
  const email = __ENV.ADMIN_EMAIL || 'admin-carga@mikuna.test';
  const password = __ENV.ADMIN_PASSWORD || PASSWORD;
  const res = http.post(`${base}/api/auth/login`, JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { flow: 'metricas' } });
  return res.status === 200 ? res.json('accessToken') : null;
}

function readCount(base, token, cache, result) {
  const res = http.get(`${base}/actuator/metrics/cache.gets?tag=cache:${cache}&tag=result:${result}`,
    { headers: { Authorization: `Bearer ${token}` }, tags: { flow: 'metricas' } });
  return res.status === 200 ? res.json('measurements.0.value') || 0 : 0;
}

// Suma de aciertos y fallos de todas las instancias (cada una lleva su propia estadística).
function snapshot() {
  const totals = { hit: 0, miss: 0, available: false };
  for (const base of urls()) {
    const token = adminToken(base);
    if (!token) {
      continue;
    }
    totals.available = true;
    for (const cache of CACHES) {
      totals.hit += readCount(base, token, cache, 'hit');
      totals.miss += readCount(base, token, cache, 'miss');
    }
  }
  return totals;
}

export function setup() {
  return { cacheBefore: snapshot() };
}

export function teardown(data) {
  const after = snapshot();
  if (data && data.cacheBefore && data.cacheBefore.available && after.available) {
    cacheHits.add(Math.max(0, after.hit - data.cacheBefore.hit));
    cacheMisses.add(Math.max(0, after.miss - data.cacheBefore.miss));
  }
}

function stamp() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}-${pad(d.getHours())}${pad(d.getMinutes())}`;
}

function ms(value) {
  return value === undefined ? '—' : `${Math.round(value)} ms`;
}

function pct(value) {
  return value === undefined ? '—' : `${(value * 100).toFixed(2)} %`;
}

function trend(data, name) {
  const metric = data.metrics[name];
  return metric ? metric.values : {};
}

function thresholdsOk(data, name) {
  const metric = data.metrics[name];
  if (!metric || !metric.thresholds) {
    return null;
  }
  return Object.values(metric.thresholds).every((t) => t.ok);
}

export function buildSummary(scenario, data, extraSections = []) {
  const flows = ['catalogo', 'login', 'pedido'];
  const label = __ENV.CACHE_LABEL || 'sin-etiqueta';
  const instances = urls().length;
  const hits = (data.metrics.cache_hits || { values: {} }).values.count;
  const misses = (data.metrics.cache_misses || { values: {} }).values.count;
  const ratio = hits !== undefined && misses !== undefined && hits + misses > 0 ? hits / (hits + misses) : undefined;

  const rows = flows.map((flow) => {
    const d = trend(data, `http_req_duration{flow:${flow}}`);
    const f = trend(data, `http_req_failed{flow:${flow}}`);
    const ok = thresholdsOk(data, `http_req_duration{flow:${flow}}`) !== false
      && thresholdsOk(data, `http_req_failed{flow:${flow}}`) !== false;
    return { flow, p50: d.med, p95: d['p(95)'], p99: d['p(99)'], errors: f.rate, ok };
  });
  const allOk = rows.every((row) => row.ok);

  const result = {
    escenario: scenario,
    commit: __ENV.GIT_SHA || 'unknown',
    fecha: new Date().toISOString(),
    cache: label,
    instancias: instances,
    baseUrls: urls(),
    datos: 'load-tests/seed/seed.sql (200 restaurantes, 6000 productos, 300 clientes)',
    usuariosMaximos: (data.metrics.vus_max || { values: {} }).values.max,
    duracionMs: data.state ? data.state.testRunDurationMs : undefined,
    flujos: rows,
    aciertosCache: { hits, misses, proporcion: ratio },
    umbralesCumplidos: allOk,
  };

  const md = [
    `# Informe de carga: ${scenario} (${label}, ${instances} instancia${instances > 1 ? 's' : ''})`,
    '',
    `- Commit: \`${result.commit}\`  ·  Fecha: ${result.fecha}`,
    `- URLs: ${urls().join(', ')}`,
    `- Datos: ${result.datos}`,
    `- Usuarios máximos: ${result.usuariosMaximos}  ·  Duración: ${Math.round((result.duracionMs || 0) / 1000)} s`,
    '',
    '| Flujo | p50 | p95 | p99 | Errores | Umbrales |',
    '|-------|-----|-----|-----|---------|----------|',
    ...rows.map((r) => `| ${r.flow} | ${ms(r.p50)} | ${ms(r.p95)} | ${ms(r.p99)} | ${pct(r.errors)} | ${r.ok ? '✅' : '❌'} |`),
    '',
    `Aciertos de caché: ${ratio === undefined ? 'no medidos (sin credenciales de administrador o caché apagada)' : `${pct(ratio)} (${hits} aciertos, ${misses} fallos)`}`,
    '',
    `Resultado: ${allOk ? 'cumple los umbrales (p95 < 2 s, errores < 1 %)' : 'NO cumple algún umbral'}`,
    ...extraSections,
    '',
  ].join('\n');

  const base = `reports/${stamp()}-${scenario}-${label}-${instances}inst`;
  return { md, json: JSON.stringify(result, null, 2), base };
}

export function writeSummary(scenario, data, extraSections = []) {
  const { md, json, base } = buildSummary(scenario, data, extraSections);
  return {
    stdout: `\n${md}\n`,
    [`${base}.md`]: md,
    [`${base}.json`]: json,
  };
}
