import { pathToFileURL } from 'node:url'

const required = [
  'AWS_REGION', 'AWS_DEPLOY_ROLE_ARN', 'MIKUNA_SITE_BUCKET',
  'MIKUNA_CLOUDFRONT_DISTRIBUTION_ID', 'MIKUNA_PUBLIC_URL',
]

export function validateDeployment(environment) {
  for (const name of required) {
    if (!environment[name]?.trim()) throw new Error('Falta configuración requerida: ' + name)
  }
  const checks = [
    ['AWS_REGION', /^[a-z]{2}(?:-[a-z0-9]+)+-\d+$/],
    ['AWS_DEPLOY_ROLE_ARN', /^arn:aws(?:-cn|-us-gov)?:iam::\d{12}:role\/[A-Za-z0-9+=,.@_/-]+$/],
    ['MIKUNA_SITE_BUCKET', /^(?!\d+\.\d+\.\d+\.\d+$)[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$/],
    ['MIKUNA_CLOUDFRONT_DISTRIBUTION_ID', /^[A-Z0-9]+$/],
  ]
  for (const [name, pattern] of checks) {
    if (!pattern.test(environment[name])) throw new Error('Formato inválido: ' + name)
  }
  if (environment.MIKUNA_SITE_BUCKET.includes('..')) throw new Error('Formato inválido: MIKUNA_SITE_BUCKET')
  let publicUrl
  try { publicUrl = new URL(environment.MIKUNA_PUBLIC_URL) }
  catch { throw new Error('Formato inválido: MIKUNA_PUBLIC_URL') }
  if (publicUrl.protocol !== 'https:' || !publicUrl.hostname || publicUrl.username || publicUrl.password ||
      publicUrl.pathname !== '/' || publicUrl.search || publicUrl.hash) {
    throw new Error('MIKUNA_PUBLIC_URL debe ser la raíz HTTPS del sitio, sin credenciales, consulta o fragmento')
  }
  return publicUrl
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    validateDeployment(process.env)
    console.log('Configuración de entrega validada.')
  } catch (error) {
    console.error(error instanceof Error ? error.message : 'No se pudo validar la configuración de entrega')
    process.exitCode = 1
  }
}
