// Flujos de las pruebas de carga (spec 015, contracts/configuracion-y-metricas.md).
// catalogo: búsqueda, filtros y carta pública. login: inicio de sesión. pedido: carrito → dirección →
// checkout → confirmación con EFECTIVO (FR-017: nunca llama a la pasarela de pagos).
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URLS = (__ENV.BASE_URLS || 'http://localhost:8080')
  .split(',')
  .map((url) => url.trim().replace(/\/$/, ''))
  .filter(Boolean);

export const PASSWORD = __ENV.LOAD_PASSWORD || 'CargaMikuna2026!';
export const CUSTOMERS = 300; // carga1..carga300@mikuna.test (seed.sql)

const CATEGORIES = ['Criolla', 'Pollería', 'Pizzería', 'Chifa', 'Marina', 'Postres', 'Hamburguesas', 'Cafetería'];
const ZONES = ['Ayacucho', 'San Juan Bautista', 'Carmen Alto', 'Jesús Nazareno'];
const JSON_HEADERS = { 'Content-Type': 'application/json' };

// Reparto en ronda entre instancias (dos backends sin balanceador). Cada usuario virtual empieza en otra URL.
let turn = __VU;
export function baseUrl() {
  turn += 1;
  return BASE_URLS[turn % BASE_URLS.length];
}

export function urls() {
  return BASE_URLS;
}

function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

function params(flow, name, token) {
  const headers = token ? { ...JSON_HEADERS, Authorization: `Bearer ${token}` } : JSON_HEADERS;
  return { headers, tags: { flow, name } };
}

// Restaurantes vistos por este usuario virtual: se reutilizan en el flujo de pedido.
let knownRestaurants = [];

export function catalogo() {
  const filter = Math.random() < 0.5
    ? ''
    : Math.random() < 0.5
      ? `&category=${encodeURIComponent(pick(CATEGORIES))}`
      : `&zone=${encodeURIComponent(pick(ZONES))}`;
  const list = http.get(`${baseUrl()}/api/public/restaurants?page=0&size=20${filter}`,
    params('catalogo', 'GET /restaurants'));
  check(list, { 'lista 200': (r) => r.status === 200 });
  if (list.status === 200) {
    const items = list.json('items') || [];
    if (items.length > 0) {
      knownRestaurants = items.map((item) => item.id);
    }
  }

  const filters = http.get(`${baseUrl()}/api/public/restaurants/filters`, params('catalogo', 'GET /filters'));
  check(filters, { 'filtros 200': (r) => r.status === 200 });

  if (knownRestaurants.length > 0) {
    const catalog = http.get(`${baseUrl()}/api/public/restaurants/${pick(knownRestaurants)}/catalog?page=0&size=24`,
      params('catalogo', 'GET /restaurants/{id}/catalog'));
    check(catalog, { 'carta 200': (r) => r.status === 200 });
  }
}

export function customerEmail() {
  return `carga${((__VU - 1) % CUSTOMERS) + 1}@mikuna.test`;
}

export function login(email, flow = 'login') {
  const res = http.post(`${baseUrl()}/api/auth/login`, JSON.stringify({ email, password: PASSWORD }),
    params(flow, 'POST /auth/login'));
  check(res, { 'login 200': (r) => r.status === 200 });
  return res.status === 200 ? res.json('accessToken') : null;
}

// Sesión y dirección del cliente de este usuario virtual, reutilizadas entre iteraciones.
let session = null;
let addressId = null;

export function pedido() {
  if (!session) {
    session = login(customerEmail(), 'pedido');
    if (!session) {
      return;
    }
  }
  if (knownRestaurants.length === 0) {
    catalogo();
    if (knownRestaurants.length === 0) {
      return;
    }
  }

  const carta = http.get(`${baseUrl()}/api/public/restaurants/${pick(knownRestaurants)}/catalog?page=0&size=24`,
    params('pedido', 'GET /restaurants/{id}/catalog'));
  const products = carta.status === 200 ? carta.json('products.items') || [] : [];
  if (products.length === 0) {
    return;
  }

  http.del(`${baseUrl()}/api/customer/cart`, null, params('pedido', 'DELETE /cart', session));
  const line = http.post(`${baseUrl()}/api/customer/cart/lines`,
    JSON.stringify({ productId: pick(products).id, quantity: 1 }), params('pedido', 'POST /cart/lines', session));
  if (!check(line, { 'línea agregada': (r) => r.status === 200 || r.status === 201 })) {
    return;
  }

  if (!addressId) {
    const addresses = http.get(`${baseUrl()}/api/customer/addresses`, params('pedido', 'GET /addresses', session));
    const list = addresses.status === 200 ? addresses.json() : [];
    addressId = list && list.length > 0 ? list[0].id : null;
    if (!addressId) {
      return;
    }
  }
  const address = http.put(`${baseUrl()}/api/customer/cart/address`, JSON.stringify({ addressId }),
    params('pedido', 'PUT /cart/address', session));
  check(address, { 'dirección elegida': (r) => r.status === 200 });

  const checkout = http.get(`${baseUrl()}/api/customer/checkout`, params('pedido', 'GET /checkout', session));
  if (!check(checkout, { 'checkout listo': (r) => r.status === 200 && r.json('ready') === true })) {
    return;
  }

  const confirm = params('pedido', 'POST /orders', session);
  confirm.headers['Idempotency-Key'] = `carga-${__VU}-${__ITER}-${Date.now()}`;
  const order = http.post(`${baseUrl()}/api/customer/orders`,
    JSON.stringify({ paymentMethod: 'CASH', expectedTotal: checkout.json('cart.totals.total') }), confirm);
  check(order, { 'pedido 201': (r) => r.status === 201 });
}

// Navegación de un usuario real: inicia sesión una sola vez (dentro de pedido) y reutiliza su token. Mezcla:
// catálogo 80 %, pedido 20 %, con 1 s de pausa entre acciones.
export function navigationIteration() {
  if (Math.random() < 0.80) {
    catalogo();
  } else {
    pedido();
  }
  sleep(1);
}

// Inicios de sesión a ritmo fijo (escenario aparte). PBKDF2 cuesta ~0,7 s de CPU por login a propósito: medirlo a
// un ritmo realista evita que una cola de logins repetidos por el mismo usuario domine toda la corrida.
let loginTurn = 0;
export function loginIteration() {
  loginTurn += 1;
  login(`carga${((__VU * 7 + loginTurn) % CUSTOMERS) + 1}@mikuna.test`);
}

export const LOGIN_RATE = Number(__ENV.LOGIN_RATE || 5);

// Escenario de login a ritmo fijo durante toda la corrida.
export function loginScenario(duration) {
  return {
    executor: 'constant-arrival-rate',
    exec: 'loginFlow',
    rate: LOGIN_RATE,
    timeUnit: '1s',
    duration,
    preAllocatedVUs: 20,
    maxVUs: 100,
  };
}

// Umbrales de la propuesta por flujo (FR-016): p95 < 2 s y menos de 1 % de errores.
export function flowThresholds() {
  const thresholds = {};
  for (const flow of ['catalogo', 'login', 'pedido']) {
    thresholds[`http_req_duration{flow:${flow}}`] = ['p(95)<2000'];
    thresholds[`http_req_failed{flow:${flow}}`] = ['rate<0.01'];
  }
  return thresholds;
}
