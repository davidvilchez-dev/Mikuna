import { expect, test } from '@playwright/test'

const description = 'Descubre restaurantes cerca de ti y disfruta tus favoritos sin salir de casa.'

test.describe('US1 — inicio público', () => {
  test('presenta la marca y la propuesta exacta sin pedir acceso', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle('Mikuna — Tu antojo, en camino.')
    await expect(page.locator('html')).toHaveAttribute('lang', 'es')
    await expect(page.getByRole('banner').getByRole('link', { name: 'Mikuna, ir al inicio' })).toBeVisible()
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Tu antojo, en camino.')
    await expect(page.getByText(description, { exact: true })).toBeVisible()
    await expect(page.getByRole('search', { name: 'Buscar restaurantes por dirección' })).toBeVisible()
    await expect(page.getByLabel('Tu dirección o distrito')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Ver restaurantes', exact: true })).toBeVisible()
    await expect(page.locator('#presentacion').getByRole('link', { name: 'Crear cuenta', exact: true })).toBeVisible()
    await expect(page.getByRole('dialog')).toHaveCount(0)
  })

  test('carga tres alimentos independientes en la composición', async ({ page }) => {
    await page.goto('/')
    const composition = page.getByTestId('food-composition')
    await expect(composition).toBeVisible()
    for (const id of ['wings', 'pizzaWhole', 'heroBurger']) {
      const image = composition.locator('img[data-asset="' + id + '"]')
      await expect(image).toBeVisible()
      await expect.poll(() => image.evaluate((element: HTMLImageElement) => element.naturalWidth)).toBeGreaterThan(0)
    }
  })
})


const categoryNames = ['Hamburguesas', 'Pollo', 'Pizza', 'Comida peruana', 'Postres', 'Bebidas']
const futureNames = categoryNames
const noticeMessage = 'Esta función estará disponible próximamente'

async function activateFuture(page: import('@playwright/test').Page, name: string) {
  await page.evaluate(() => document.fonts.ready)
  const action = page.getByRole('button', { name, exact: true })
  if (!await action.isVisible()) {
    await page.locator('[data-mobile-menu-toggle]').click()
  }
  await action.scrollIntoViewIfNeeded()
  const previous = await page.evaluate(() => ({ url: location.href, y: scrollY }))
  await action.locator('[data-keep-clear]').click()
  return previous
}

test.describe('US2 — categorías', () => {
  test('muestra seis categorías específicas y termina en ellas', async ({ page }) => {
    await page.goto('/')
    const section = page.getByTestId('categories')
    await expect(section.getByRole('heading', { level: 2 })).toHaveText('¿Qué se te antoja hoy?')
    await expect(section.locator('[data-category-label]')).toHaveText(categoryNames)
    await expect(section.getByRole('button')).toHaveCount(6)
    for (const name of categoryNames) {
      const card = section.getByRole('button', { name, exact: true })
      await card.scrollIntoViewIfNeeded()
      const image = card.locator('img')
      await expect.poll(() => image.evaluate((element: HTMLImageElement) => element.naturalWidth)).toBeGreaterThan(0)
    }
    await expect(section.locator('[data-asset="categoryBurger"]')).toHaveCount(1)
  })

  test('la categoría responde sin filtrar ni navegar', async ({ page }) => {
    await page.goto('/')
    const previous = await activateFuture(page, 'Comida peruana')
    await expect(page.getByRole('status')).toHaveText(noticeMessage)
    expect(page.url()).toBe(previous.url)
    expect(await page.evaluate(() => scrollY)).toBeCloseTo(previous.y, 0)
    await expect(page.locator('[aria-pressed="true"]')).toHaveCount(0)
  })
})

test.describe('US3 — navegación y avisos', () => {
  test('las seis categorías comparten un aviso y conservan la vista', async ({ page }) => {
    await page.goto('/')
    for (const name of futureNames) {
      const previous = await activateFuture(page, name)
      await expect(page.getByRole('status')).toHaveText(noticeMessage)
      await expect(page.locator('[data-testid="coming-soon-notice"][data-visible="true"]')).toHaveCount(1)
      expect(page.url()).toBe(previous.url)
      expect(await page.evaluate(() => scrollY)).toBeCloseTo(previous.y, 0)
    }
  })

  test('el logo vuelve al comienzo', async ({ page }) => {
    await page.goto('/')
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
    await page.getByRole('link', { name: 'Mikuna, ir al inicio' }).click()
    await expect.poll(() => page.evaluate(() => scrollY)).toBe(0)
  })

  test('el panel usa teclado, cierra y se restablece al cambiar de ancho', async ({ page }) => {
    await page.goto('/')
    const toggle = page.locator('[data-mobile-menu-toggle]')
    if ((page.viewportSize()?.width ?? 1536) >= 1280) {
      await expect(toggle).toBeHidden()
      return
    }
    await toggle.focus()
    await page.keyboard.press('Enter')
    await expect(toggle).toHaveAttribute('aria-expanded', 'true')
    await expect(page.getByRole('navigation', { name: 'Navegación principal' }).getByRole('link', { name: 'Restaurantes', exact: true })).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await expect(toggle).toBeFocused()
    await toggle.click()
    await page.setViewportSize({ width: 1536, height: 1024 })
    await page.setViewportSize({ width: 390, height: 844 })
    await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  })

  test('reinicia cinco segundos activos sin acumular avisos', async ({ page }) => {
    await page.clock.install()
    await page.goto('/')
    await page.clock.pauseAt(await page.evaluate(() => Date.now()) + 1000)
    await activateFuture(page, 'Hamburguesas')
    await page.mouse.move(0, 0)
    await page.clock.runFor(3000)
    await expect(page.getByTestId('coming-soon-notice')).toHaveAttribute('data-visible', 'true')
    await activateFuture(page, 'Comida peruana')
    await page.mouse.move(0, 0)
    await page.clock.runFor(4999)
    await expect(page.getByTestId('coming-soon-notice')).toHaveAttribute('data-visible', 'true')
    await page.clock.runFor(2)
    await expect(page.getByTestId('coming-soon-notice')).toHaveAttribute('data-visible', 'false')
    await expect(page.getByRole('status')).toHaveText('')
  })

  test('combina pausas por puntero, foco y documento oculto', async ({ page }) => {
    await page.clock.install()
    await page.goto('/')
    await page.clock.pauseAt(await page.evaluate(() => Date.now()) + 1000)
    await activateFuture(page, 'Comida peruana')
    await page.clock.runFor(1)
    const notice = page.getByTestId('coming-soon-notice')
    await notice.hover()
    await page.getByRole('button', { name: 'Cerrar aviso' }).focus()
    await page.evaluate(() => {
      Object.defineProperty(document, 'hidden', { configurable: true, value: true })
      document.dispatchEvent(new Event('visibilitychange'))
    })
    await page.mouse.move(0, 0)
    await page.clock.runFor(12000)
    await expect(notice).toHaveAttribute('data-visible', 'true')
    await page.getByRole('button', { name: 'Comida peruana', exact: true }).focus()
    await page.clock.runFor(12000)
    await expect(notice).toHaveAttribute('data-visible', 'true')
    await page.evaluate(() => {
      Object.defineProperty(document, 'hidden', { configurable: true, value: false })
      document.dispatchEvent(new Event('visibilitychange'))
    })
    await page.clock.runFor(5001)
    await expect(notice).toHaveAttribute('data-visible', 'false')
  })

  test('el cierre manual conserva un foco útil', async ({ page }) => {
    await page.goto('/')
    await activateFuture(page, 'Comida peruana')
    await page.getByRole('button', { name: 'Cerrar aviso' }).click()
    await expect(page.getByTestId('coming-soon-notice')).toHaveAttribute('data-visible', 'false')
    await expect(page.getByRole('button', { name: 'Comida peruana', exact: true })).toBeFocused()
  })
})

test.describe('US4 — adaptación y fallos', () => {
  test('conserva columnas, contenido y áreas activables sin overflow', async ({ page }) => {
    await page.goto('/')
    const width = page.viewportSize()?.width ?? 1536
    const expectedColumns = width >= 1280 ? 6 : width >= 768 ? 3 : 2
    const grid = page.getByTestId('category-grid')
    const columns = await grid.evaluate((element) => getComputedStyle(element).gridTemplateColumns.split(' ').length)
    expect(columns).toBe(expectedColumns)
    const geometry = await page.evaluate(() => ({
      viewport: innerWidth,
      document: document.documentElement.scrollWidth,
      controls: Array.from(document.querySelectorAll<HTMLElement>('button, a[href]'))
        .filter((element) => element.getClientRects().length > 0)
        .map((element) => ({ name: element.textContent, width: element.getBoundingClientRect().width, height: element.getBoundingClientRect().height })),
    }))
    expect(geometry.document).toBeLessThanOrEqual(geometry.viewport)
    for (const control of geometry.controls) {
      expect(control.width, control.name ?? '').toBeGreaterThanOrEqual(44)
      expect(control.height, control.name ?? '').toBeGreaterThanOrEqual(44)
    }
    for (const image of await page.getByTestId('categories').locator('img').all()) {
      await image.scrollIntoViewIfNeeded()
      await expect.poll(() => image.evaluate((element: HTMLImageElement) => element.naturalWidth)).toBeGreaterThan(0)
    }
    await page.evaluate(() => window.scrollTo(0, 0))
    await page.screenshot({ path: test.info().outputPath('inicio.png'), fullPage: true })
  })

  test('mantiene el aviso dentro de la ventana en menú, acciones y última categoría', async ({ page }) => {
    await page.goto('/')
    for (const name of ['Hamburguesas', 'Comida peruana', 'Bebidas']) {
      await activateFuture(page, name)
      const notice = page.getByTestId('coming-soon-notice')
      await expect(page.getByRole('status')).toHaveText(noticeMessage)
      const box = await notice.boundingBox()
      const viewport = page.viewportSize()!
      expect(box).not.toBeNull()
      expect(box!.x).toBeGreaterThanOrEqual(0)
      expect(box!.y).toBeGreaterThanOrEqual(0)
      expect(box!.x + box!.width).toBeLessThanOrEqual(viewport.width)
      expect(box!.y + box!.height).toBeLessThanOrEqual(viewport.height)
      const action = page.getByRole('button', { name, exact: true })
      const target = await action.isVisible()
        ? action.locator('[data-keep-clear]')
        : page.locator('[data-mobile-menu-toggle]')
      const targetBox = await target.boundingBox()
      expect(targetBox).not.toBeNull()
      const overlapWidth = Math.max(0, Math.min(box!.x + box!.width, targetBox!.x + targetBox!.width) - Math.max(box!.x, targetBox!.x))
      const overlapHeight = Math.max(0, Math.min(box!.y + box!.height, targetBox!.y + targetBox!.height) - Math.max(box!.y, targetBox!.y))
      expect(overlapWidth * overlapHeight).toBe(0)
      await page.screenshot({ path: test.info().outputPath('aviso-' + name + '.png') })
    }
  })

  test('respeta la paleta de marca', async ({ page }) => {
    await page.goto('/')
    const colors = await page.evaluate(() => ({
      hero: getComputedStyle(document.querySelector('#presentacion')!).backgroundColor,
      title: getComputedStyle(document.querySelector('h1')!).color,
      orange: getComputedStyle(document.querySelector('.header-cta')!).backgroundColor,
      categories: getComputedStyle(document.querySelector('#categorias')!).backgroundColor,
    }))
    expect(colors).toEqual({
      hero: 'rgb(255, 255, 255)', title: 'rgb(17, 9, 76)',
      orange: 'rgb(239, 80, 2)', categories: 'rgb(255, 255, 255)',
    })
  })

  test('el buscador de dirección valida y avisa', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: 'Ver restaurantes', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('Escribe tu dirección o tu distrito')
    await page.getByLabel('Tu dirección o distrito').fill('Jr. Inexistente 123, Ciudad Lejana')
    await expect(page.getByRole('alert')).toHaveCount(0)
  })

  test('sigue siendo utilizable cuando fallan todas las imágenes', async ({ page }) => {
    await page.route(/\.png(?:\?|$)/, (route) => route.abort())
    await page.goto('/')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Tu antojo, en camino.')
    await expect(page.getByText(description, { exact: true })).toBeVisible()
    await expect(page.getByTestId('categories').locator('[data-category-label]')).toHaveText(categoryNames)
    await activateFuture(page, 'Bebidas')
    await expect(page.getByRole('status')).toHaveText(noticeMessage)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  })
})
