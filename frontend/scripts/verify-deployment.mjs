import { validateDeployment } from './validate-deployment.mjs'

const publicUrl = validateDeployment(process.env)
const requestOptions = { redirect: 'manual', signal: AbortSignal.timeout(30_000) }
let currentUrl = publicUrl
let response
for (let redirects = 0; redirects < 6; redirects++) {
  response = await fetch(currentUrl, { ...requestOptions, signal: AbortSignal.timeout(30_000) })
  if (![301, 302, 303, 307, 308].includes(response.status)) break
  const location = response.headers.get('location')
  if (!location) throw new Error('La redirección HTTPS no tiene destino')
  const next = new URL(location, currentUrl)
  if (next.protocol !== 'https:' || next.host !== publicUrl.host) throw new Error('Redirección fuera del destino HTTPS esperado')
  currentUrl = next
}
if (!response?.ok) throw new Error('La página pública no respondió correctamente por HTTPS')
if (!(response.headers.get('content-type') ?? '').includes('text/html')) throw new Error('El destino no entrega HTML')
if (!(await response.text()).includes('<title>Mikuna')) throw new Error('El HTML público no identifica a Mikuna')

const httpUrl = new URL(publicUrl)
httpUrl.protocol = 'http:'
const redirect = await fetch(httpUrl, { redirect: 'manual', signal: AbortSignal.timeout(30_000) })
if (![301, 302, 303, 307, 308].includes(redirect.status)) throw new Error('HTTP no redirige a HTTPS')
const location = redirect.headers.get('location')
if (!location) throw new Error('La respuesta HTTP no indica el destino cifrado')
const destination = new URL(location, httpUrl)
if (destination.protocol !== 'https:' || destination.host !== publicUrl.host) throw new Error('HTTP no redirige al host HTTPS esperado')
console.log('Entrega verificada: HTML de Mikuna, HTTPS y redirección HTTP válidos.')
