import { test as setup } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

// Recarga mikuna_e2e_db con la semilla mínima. Corre una vez, con el backend ya levantado (Flyway ya creó el
// esquema) y antes de que ninguna prueba consulte la API, así la caché del catálogo empieza vacía.
const here = dirname(fileURLToPath(import.meta.url))
const backendEnv = join(here, '..', '..', '..', '..', 'backend', '.env')

/** Valor de backend/.env (solo en local; en CI llegan como variables de entorno). */
function fromBackendEnv(name: string): string | undefined {
  if (!existsSync(backendEnv)) {
    return undefined
  }
  const line = readFileSync(backendEnv, 'utf8').split(/\r?\n/).find((l) => l.startsWith(name + '='))
  return line?.slice(name.length + 1).trim()
}

function psqlPath(): string {
  if (process.env.PSQL) {
    return process.env.PSQL
  }
  const windowsDefault = 'C:\\Program Files\\PostgreSQL\\16\\bin\\psql.exe'
  return process.platform === 'win32' && existsSync(windowsDefault) ? windowsDefault : 'psql'
}

setup('carga la semilla E2E en mikuna_e2e_db', () => {
  const password = process.env.DB_PASSWORD ?? fromBackendEnv('DB_PASSWORD') ?? ''
  const user = process.env.DB_USERNAME ?? fromBackendEnv('DB_USERNAME') ?? 'postgres'
  execFileSync(psqlPath(), ['-h', 'localhost', '-U', user, '-d', 'mikuna_e2e_db', '-q', '-f',
    join(here, 'seed-e2e.sql')], {
    env: { ...process.env, PGPASSWORD: password },
    stdio: ['ignore', 'ignore', 'pipe'],
  })
})
