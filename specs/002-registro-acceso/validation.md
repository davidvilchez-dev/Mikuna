# Validación: Registro e inicio de sesión (002)

**Fecha**: 2026-11-17 · **Constitución**: 3.0.0 · **Entorno**: Windows 11, Java 25, PostgreSQL 16 local, Node + pnpm

## Comprobaciones automatizadas

| Comprobación | Resultado |
| --- | --- |
| `.\mvnw verify` (backend) | ✅ 63 pruebas, 0 fallos: 37 unitarias (Mockito) y 26 de integración contra `mikuna_test_db` |
| JaCoCo, total del código medido | ✅ 201/207 líneas (**97,1 %**). `AccountRegistrationService`, `AuthService`, `TokenService`, `AccountStatusFilter` y `JwtProperties` al 100 %. Exclusiones: `BackendApplication`, `dto/**`, `SecurityConfig` (solo configuración). |
| `pnpm test:coverage` (frontend) | ✅ 16 pruebas. `auth-validation.ts` y `session-storage.ts`: 97,6 % de líneas y 100 % de ramas |
| `pnpm lint` y `pnpm build` | ✅ sin errores |
| Playwright | No se ejecutó ni se modificó (constitución 3.0.0, principio V) |

## Comprobación local contra la aplicación real (`mikuna_db`)

Con el backend en `:8080` y Vite en `:5173`:

| Caso | Resultado |
| --- | --- |
| Registro de restaurante | ✅ 201 con `restaurantApproval: PENDING` |
| Correo repetido en mayúsculas y con otro rol | ✅ 409 «El correo ya está registrado» |
| Contraseña incorrecta | ✅ 401 genérico |
| Login y `GET /me` con token | ✅ 200 con los datos del titular |
| `/me` sin token | ✅ 401 `application/problem+json` con `WWW-Authenticate: Bearer` |
| Nombre y contraseña con «ñ» enviados como UTF-8 | ✅ 201 |
| Rutas directas `/registro?tipo=restaurante`, `/iniciar-sesion` y `/mi-cuenta` | ✅ 200; Vite sirve la SPA |
| Proxy `/api` de Vite al backend | ✅ |
| Perfil `admin-setup` (quickstart §2), primera ejecución | ✅ «Administrador creado», salida 0 |
| Perfil `admin-setup`, segunda ejecución con el mismo correo | ✅ falla con salida 1 y no modifica la cuenta |
| Login del ADMIN creado | ✅ 200, rol `ADMIN` |
| Contraseñas en el log del backend | ✅ 0 apariciones; los hashes guardados empiezan por `{pbkdf2@SpringSecurity_v5_8}` |
| Restricciones de la tabla `cuentas` | ✅ solo `cuentas_pkey` y `uk_cuentas_email`, sin `CHECK` (ver data-model.md) |

Las cuentas de prueba (`*@prueba.local`) se borraron de `mikuna_db` al terminar.

## Comprobación manual en el navegador

✅ El usuario recorrió el quickstart §3 en el navegador el 2026-11-17 y confirmó que las pruebas
pasaron: registro de los tres tipos, alta y acceso del administrador, inicio y cierre de sesión y
navegación desde el inicio.

## Estado de requisitos

| Requisito | Estado |
| --- | --- |
| RF-01 | **Verificado en local**: API, persistencia, pruebas automatizadas y comprobación manual en el navegador. |
| RF-04 | **Parcial**: rol y estado se comprueban en cada acceso implementado. Las relaciones con pedidos, restaurantes y entregas siguen pendientes. |
| RNF-07 | **Verificado en los accesos implementados**: 401 y 403, token manipulado, vencido o firmado con otro secreto, suplantación y rutas públicas declaradas. |
| RNF-11 | **Cumplido en este incremento**: 97,1 % de líneas en el backend medido y 97,6 % en la lógica del frontend; reporte de ramas incluido. |
| RNF-06 (HTTPS) y pruebas E2E | Aplazados según la constitución; no se declaran cumplidos. |
| RF-05 y RF-06 | Pendientes. Registrar un restaurante no lo aprueba. |
| RF-02 y RF-03 | Fuera de este incremento. |

## Desviaciones respecto al plan

- **Contraseña sin mínimo de 12 (temporal):** por decisión del usuario del 2026-11-17, para agilizar
  las pruebas manuales, la contraseña solo debe no estar vacía y tener hasta 128 caracteres.
  **Pendiente:** restaurar `PASSWORD_MIN = 12` en `AccountRegistrationService` y `auth-validation.ts`
  (más la anotación de `RegisterRequest` y las pruebas) antes de cerrar el proyecto.

- **Enums de `Account`:** se guardan como `String` en lugar de `@Enumerated`. Hibernate 7 genera
  `CHECK` incluso con conversores o `columnDefinition` (se comprobó en PostgreSQL). Se actualizaron
  data-model.md y T007.
- **`JwtProperties`:** se creó como `@ConfigurationProperties` validado, con su propia prueba
  (resuelve I3 de `/speckit-analyze`).
- **Extras sobre lo planificado:** el registro valida longitudes después del recorte y el login
  limita la longitud de sus campos (U1); el estado se muestra en español (U2); el frontend rechaza
  correos con espacios (U3); el quickstart usa `pnpm test:coverage`, igual que T047 (I2).
- **T050** no se aplicó: `/speckit-implement` no modifica archivos de checklist.
