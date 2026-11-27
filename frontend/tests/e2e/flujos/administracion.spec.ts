import { expect, test } from '@playwright/test'
import {
  ADMIN, apiLogin, cashOrder, customerWithAddress, loginUi, orderCode, ownerAccepts, registerAccount, RESTAURANT,
  uniqueEmail,
} from './support'

// Specs 003 (registro de restaurantes), 011 (administración) y 014 (cancelación administrativa): lo que solo hace
// el administrador, comprobado en la pantalla de quien resulta afectado.
test.describe('[003·011·014·015] Administración de la plataforma', () => {
  test('aprobar una solicitud hace aparecer el restaurante en la búsqueda pública', async ({ page, request }) => {
    const name = `Pollería Nueva ${Date.now()}`
    const ownerEmail = uniqueEmail('titular')
    await registerAccount(request, ownerEmail, 'RESTAURANT', 'Titular Nuevo')
    const ownerToken = await apiLogin(request, ownerEmail)
    const created = await request.post('http://127.0.0.1:8080/api/restaurant/me', {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { name, category: 'Pollería', description: 'Pollo a la brasa', address: 'Av. Cusco 400',
        zone: 'Ayacucho', phone: '+51 966 222 333' },
    })
    expect(created.status()).toBe(201)

    // Antes de aprobarla no aparece (la búsqueda pública ya quedó en caché, spec 015).
    await page.goto('/restaurantes')
    await expect(page.getByText(RESTAURANT).first()).toBeVisible()
    await expect(page.getByText(name)).toHaveCount(0)

    await loginUi(page, ADMIN)
    await page.goto('/admin/solicitudes')
    const request_ = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name }) })
    await expect(request_.getByText('Pendiente')).toBeVisible()
    await request_.getByRole('button', { name: 'Aprobar' }).click()
    await expect(page.getByRole('heading', { name })).toHaveCount(0) // sale de la lista de pendientes

    // La aprobación descarta la caché pública: el restaurante aparece en la siguiente búsqueda.
    await page.goto('/restaurantes')
    await expect(page.getByText(name).first()).toBeVisible()
  })

  test('cancelar un pedido aceptado avisa al cliente con el motivo', async ({ page, browser, request }) => {
    const email = uniqueEmail('cancelado')
    const token = await customerWithAddress(request, email)
    const orderId = await cashOrder(request, token)
    await ownerAccepts(request, orderId)
    const code = orderCode(orderId)
    const reason = 'El restaurante se quedó sin gas'

    const adminContext = await browser.newContext()
    const admin = await adminContext.newPage()
    await loginUi(admin, ADMIN)
    await admin.goto('/admin/pedidos')
    const row = admin.getByRole('row').filter({ hasText: `Pedido ${code}` })
    await expect(row).toContainText('Aceptado')
    await row.getByRole('button', { name: 'Ver detalle' }).click()
    await admin.getByRole('button', { name: 'Cancelar pedido' }).click()

    const dialog = admin.getByRole('dialog', { name: `Cancelar pedido ${code}` })
    await expect(dialog).toContainText('se pagaba en efectivo: no hay cobro que devolver')
    await dialog.getByLabel('Motivo de la cancelación').fill(reason)
    await dialog.getByRole('button', { name: 'Cancelar pedido' }).click()
    await expect(dialog).toBeHidden()
    await expect(row).toContainText('Cancelado')
    await adminContext.close()

    // El cliente lo ve cancelado por la plataforma, con el motivo.
    await loginUi(page, email)
    await page.goto(`/pedido/${orderId}`)
    await expect(page.getByText('Cancelado por la plataforma')).toBeVisible()
    await expect(page.getByText(`Motivo: ${reason}`)).toBeVisible()
  })
})
