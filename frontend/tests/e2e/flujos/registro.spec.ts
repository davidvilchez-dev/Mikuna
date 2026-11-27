import { expect, test } from '@playwright/test'
import { PASSWORD, uniqueEmail } from './support'

// Specs 002 (registro y acceso) y 004 (catálogo público): una persona crea su cuenta de cliente desde la interfaz,
// inicia sesión y llega a la búsqueda de restaurantes, donde ve el restaurante aprobado.
test.describe('[002·004] Registro e inicio de sesión de un cliente', () => {
  test('crea la cuenta, inicia sesión y ve los restaurantes disponibles', async ({ page }) => {
    const email = uniqueEmail('cliente')

    await page.goto('/registro')
    await page.getByRole('radio', { name: /Cliente/ }).check()
    await page.getByLabel('Nombre del titular').fill('Ana Quispe')
    await page.getByLabel('Correo electrónico').fill(email)
    await page.getByLabel('Contraseña', { exact: true }).fill(PASSWORD)
    await page.getByLabel('Confirma la contraseña').fill(PASSWORD)
    await page.getByRole('button', { name: 'Crear cuenta', exact: true }).click()

    await expect(page.getByRole('heading', { name: '¡Cuenta creada!' })).toBeVisible()
    await page.getByRole('link', { name: 'Iniciar sesión', exact: true }).click()

    await page.getByLabel('Correo electrónico').fill(email)
    await page.getByLabel('Contraseña', { exact: true }).fill(PASSWORD)
    await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).click()

    await expect(page).toHaveURL(/\/restaurantes$/)
    await expect(page.getByText('Picantería E2E').first()).toBeVisible()
  })

  test('sin sesión, las páginas personales llevan a iniciar sesión', async ({ page }) => {
    for (const path of ['/carrito', '/mi-restaurante/pedidos', '/admin/pedidos']) {
      await page.goto(path)
      await expect(page).toHaveURL(/\/iniciar-sesion$/)
    }
  })

  test('una contraseña incorrecta no inicia sesión', async ({ page }) => {
    await page.goto('/iniciar-sesion')
    await page.getByLabel('Correo electrónico').fill('restaurante@e2e.test')
    await page.getByLabel('Contraseña', { exact: true }).fill('incorrecta-123')
    await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).click()

    await expect(page.getByRole('alert')).toBeVisible()
    await expect(page).toHaveURL(/iniciar-sesion/)
  })
})
