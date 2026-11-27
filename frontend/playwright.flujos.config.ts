import { defineConfig } from '@playwright/test'

// Pruebas E2E de flujos completos contra el backend real y PostgreSQL (base dedicada mikuna_e2e_db).
// Uso: pnpm test:e2e:flujos  (ver tests/e2e/flujos/README.md). La suite de la portada (playwright.config.ts) sigue
// sin backend.
const isWindows = process.platform === 'win32'
const backendPort = 8080
const frontendPort = 5174

export default defineConfig({
  testDir: './tests/e2e/flujos',
  // Comparten una base de datos: en serie, en orden y sin reintentos que oculten fallos.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: Boolean(process.env.CI),
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report-flujos' }]],
  outputDir: 'test-results-flujos',
  use: {
    browserName: 'chromium',
    baseURL: `http://127.0.0.1:${frontendPort}`,
    locale: 'es-PE',
    colorScheme: 'light',
    reducedMotion: 'reduce',
    viewport: { width: 1440, height: 900 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'semilla', testMatch: /semilla\.setup\.ts/ },
    { name: 'flujos', testMatch: /.*\.spec\.ts/, dependencies: ['semilla'] },
  ],
  webServer: [
    {
      // Backend real contra la base E2E. Nunca se reutiliza uno ya levantado: podría apuntar a la base de desarrollo.
      command: isWindows ? String.raw`.\mvnw.cmd -q -ntp spring-boot:run` : 'sh ./mvnw -q -ntp spring-boot:run',
      cwd: '../backend',
      url: `http://127.0.0.1:${backendPort}/actuator/health`,
      reuseExistingServer: false,
      timeout: 240_000,
      stdout: 'ignore',
      stderr: 'pipe',
      env: {
        DB_URL: process.env.E2E_DB_URL ?? 'jdbc:postgresql://localhost:5432/mikuna_e2e_db',
        SERVER_PORT: String(backendPort),
      },
    },
    {
      // Servidor de desarrollo de Vite: su proxy envía /api al backend del puerto 8080.
      command: `pnpm dev --host 127.0.0.1 --port ${frontendPort} --strictPort`,
      url: `http://127.0.0.1:${frontendPort}`,
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
    },
  ],
})
