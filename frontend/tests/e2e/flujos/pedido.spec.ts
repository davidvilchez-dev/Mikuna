import { expect, test } from '@playwright/test'
import { customerWithAddress, loginUi, orderCode, OWNER, RESTAURANT, uniqueEmail } from './support'

// Specs 004 (catálogo), 005 (carrito e importes), 006 (confirmación del pedido), 007 (estados del pedido) y 009
// (seguimiento): el cliente pide en efectivo desde la interfaz y el restaurante lo acepta y prepara desde su panel;
// el cliente ve cada cambio en la página de su pedido.
test.describe('[004·005·006·007·009] Pedido en efectivo de principio a fin', () => {
  test('el cliente pide y el restaurante acepta, prepara y deja listo el pedido', async ({ page, browser, request }) => {
    const email = uniqueEmail('comprador')
    await customerWithAddress(request, email)

    // Cliente: catálogo → carrito → confirmación.
    await loginUi(page, email)
    await page.goto('/restaurantes')
    await page.getByText(RESTAURANT).first().click()
    await expect(page).toHaveURL(/\/restaurantes\/[0-9a-f-]{36}$/)
    await expect(page.getByText('Lomo saltado').first()).toBeVisible()
    const lomo = page.locator('article, li').filter({ hasText: 'Lomo saltado' }).first()
    await lomo.getByRole('button', { name: 'Agregar al carrito' }).click()

    await page.goto('/carrito')
    await expect(page.getByText('Lomo saltado').first()).toBeVisible()
    await expect(page.getByLabel('Elige dónde recibir tu pedido')).toHaveValue(/[0-9a-f-]{36}/)
    await expect(page.locator('.cart-total')).toContainText('S/')
    await page.getByRole('link', { name: 'Continuar' }).click()

    await expect(page).toHaveURL(/\/confirmar-pedido$/)
    await page.getByRole('radio', { name: 'Efectivo' }).check()
    await expect(page.getByText('Pagarás en efectivo al repartidor cuando recibas el pedido.')).toBeVisible()
    await page.getByRole('button', { name: 'Ordenar ahora' }).click()

    await expect(page).toHaveURL(/\/pedido\/[0-9a-f-]{36}$/)
    const orderId = page.url().split('/').pop()!
    const code = orderCode(orderId)
    await expect(page.getByRole('heading', { name: `Pedido ${code}` })).toBeVisible()
    await expect(page.getByText(`${RESTAURANT} · Pendiente`)).toBeVisible()

    // Restaurante: lo ve entre los nuevos, lo acepta, lo prepara y lo deja listo.
    const ownerContext = await browser.newContext()
    const owner = await ownerContext.newPage()
    await loginUi(owner, OWNER)
    await owner.goto('/mi-restaurante/pedidos')
    const card = (tab: string) => owner.getByRole('listitem').filter({ hasText: code }).filter({ hasText: tab })

    await expect(card('Pendiente')).toBeVisible()
    await card('Pendiente').getByRole('button', { name: 'Aceptar' }).click()

    await owner.getByRole('tab', { name: /Aceptados/ }).click()
    await card('Aceptado').getByRole('button', { name: 'Marcar en preparación' }).click()

    await owner.getByRole('tab', { name: /Preparando/ }).click()
    await card('En preparación').getByRole('button', { name: 'Marcar listo para recoger' }).click()

    await owner.getByRole('tab', { name: /Listos/ }).click()
    await expect(card('Listo para recoger')).toBeVisible()
    await ownerContext.close()

    // Cliente: su pedido refleja el avance.
    await page.reload()
    await expect(page.getByText(`${RESTAURANT} · Listo para recoger`)).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Seguimiento' })).toBeVisible()
  })
})
