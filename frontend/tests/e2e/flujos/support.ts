import { expect, type APIRequestContext, type Page } from '@playwright/test'

// Datos y atajos compartidos por los flujos E2E. Lo que la prueba no quiere verificar en pantalla (crear el
// cliente, su dirección o un pedido de partida) se prepara por la API real; lo que sí verifica, se hace en la UI.

export const PASSWORD = 'MikunaE2e2026!'
export const ADMIN = 'admin@e2e.test'
export const OWNER = 'restaurante@e2e.test'
export const RESTAURANT = 'Picantería E2E'
const API = 'http://127.0.0.1:8080/api'

/** Correo único por prueba para no chocar con datos de otras. */
export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@e2e.test`
}

/** Mismo formato que orderCode() del frontend: # y los 4 últimos caracteres del id. */
export function orderCode(id: string): string {
  return '#' + id.replace(/-/g, '').slice(-4).toUpperCase()
}

async function ok<T>(response: Awaited<ReturnType<APIRequestContext['get']>>): Promise<T> {
  expect(response.ok(), `${response.url()} → ${response.status()} ${await response.text()}`).toBeTruthy()
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export async function apiLogin(request: APIRequestContext, email: string): Promise<string> {
  const body = await ok<{ accessToken: string }>(await request.post(`${API}/auth/login`,
    { data: { email, password: PASSWORD } }))
  return body.accessToken
}

function auth(token: string) {
  return { Authorization: `Bearer ${token}` }
}

export async function registerAccount(request: APIRequestContext, email: string,
  role: 'CUSTOMER' | 'RESTAURANT', name = 'Cuenta E2E'): Promise<void> {
  await ok(await request.post(`${API}/auth/register`,
    { data: { name, email, password: PASSWORD, passwordConfirmation: PASSWORD, role } }))
}

/**
 * Cliente nuevo con una dirección en un distrito cubierto, ya elegida como destino del carrito; devuelve su token.
 * (El alta de direcciones exige marcar un punto en el mapa, que no es lo que estos flujos verifican.)
 */
export async function customerWithAddress(request: APIRequestContext, email: string): Promise<string> {
  await registerAccount(request, email, 'CUSTOMER', 'Cliente E2E')
  const token = await apiLogin(request, email)
  const address = await ok<{ id: string }>(await request.post(`${API}/customer/addresses`, {
    headers: auth(token),
    data: { label: 'Casa', street: 'Jr. Libertad 245', district: 'Ayacucho', reference: 'Puerta verde',
      contactPhone: '966123456', latitude: -13.158, longitude: -74.223 },
  }))
  await ok(await request.put(`${API}/customer/cart/address`, { headers: auth(token), data: { addressId: address.id } }))
  return token
}

/** Pedido en efectivo del restaurante sembrado, hecho por API; devuelve su id. */
export async function cashOrder(request: APIRequestContext, token: string): Promise<string> {
  const restaurants = await ok<{ items: { id: string; name: string }[] }>(
    await request.get(`${API}/public/restaurants?name=${encodeURIComponent(RESTAURANT)}`))
  const restaurantId = restaurants.items.find((r) => r.name === RESTAURANT)!.id
  const catalog = await ok<{ products: { items: { id: string }[] } }>(
    await request.get(`${API}/public/restaurants/${restaurantId}/catalog`))
  const addresses = await ok<{ id: string }[]>(await request.get(`${API}/customer/addresses`, { headers: auth(token) }))

  await ok(await request.delete(`${API}/customer/cart`, { headers: auth(token) }))
  await ok(await request.post(`${API}/customer/cart/lines`,
    { headers: auth(token), data: { productId: catalog.products.items[0].id, quantity: 2 } }))
  await ok(await request.put(`${API}/customer/cart/address`, { headers: auth(token), data: { addressId: addresses[0].id } }))
  const checkout = await ok<{ cart: { totals: { total: number } } }>(
    await request.get(`${API}/customer/checkout`, { headers: auth(token) }))
  const order = await ok<{ id: string }>(await request.post(`${API}/customer/orders`, {
    headers: { ...auth(token), 'Idempotency-Key': `e2e-${Date.now()}` },
    data: { paymentMethod: 'CASH', expectedTotal: checkout.cart.totals.total },
  }))
  return order.id
}

export async function ownerAccepts(request: APIRequestContext, orderId: string): Promise<void> {
  const token = await apiLogin(request, OWNER)
  await ok(await request.post(`${API}/restaurant/orders/${orderId}/accept`, { headers: auth(token) }))
}

/** Inicio de sesión por la interfaz, como lo haría la persona. */
export async function loginUi(page: Page, email: string): Promise<void> {
  await page.goto('/iniciar-sesion')
  await page.getByLabel('Correo electrónico').fill(email)
  await page.getByLabel('Contraseña', { exact: true }).fill(PASSWORD)
  await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).click()
  await expect(page).not.toHaveURL(/iniciar-sesion/)
}
