# Validación del incremento 005: carrito, direcciones y cálculo de importes

Fecha de las comprobaciones: 2026-11-17. Estados: **verificado** (comprobado con pruebas o ejecución),
**parcial** (comprobado solo en lo implementado) o **pendiente** (no comprobado).

## Resultado de las comprobaciones automáticas

| Comprobación | Resultado |
| --- | --- |
| `./mvnw verify` (backend) | 331 pruebas, 0 fallos (88 nuevas de este incremento; el resto de 002–004 sigue en verde). |
| JaCoCo, líneas de lógica de negocio | **97,1 %** (1015/1045) en total; módulos `address` y `cart` 95,3 % (244/256). Ramas: 90,7 % (361/398) en total; `address` y `cart` 88,5 % (69/78). Exclusiones iguales a 002–004: `dto`, `SecurityConfig` y `BackendApplication`. |
| `pnpm test:coverage` (frontend) | 48 pruebas, 0 fallos. Líneas medidas 98,33 % (118/120); `cart-validation.ts` al 100 %. |
| `pnpm lint` y `pnpm build` | Sin errores. |
| Playwright | No se usó (constitución V). |

## Requisitos

| Requisito | Estado | Evidencia |
| --- | --- | --- |
| **RF-11** (agregar, modificar y eliminar productos del carrito) | **Verificado** en backend; interfaz verificada por compilación, lint y validación. Recorrido manual **pendiente** | `CartServiceTest`, `CartIntegrationTest`, `CartConcurrencyIntegrationTest`, `cart-validation.test.ts` |
| **RF-12** (registrar y seleccionar dirección dentro de la zona cubierta) | **Verificado** en backend; interfaz por compilación y lint. Recorrido manual **pendiente** | `DeliveryZoneTest`, `AddressServiceTest`, `AddressIntegrationTest`, `CartIntegrationTest` (selección) |
| **RF-13** (subtotal, envío, comisión y total automáticos) | **Verificado** en backend; interfaz por compilación y lint. Recorrido manual **pendiente** | `CartPricingTest`, `CartTotalsIntegrationTest` |
| **RF-04** (autorización por rol, estado de cuenta y relación con el recurso) | **Parcial**: verificado en carrito y direcciones (rol `CUSTOMER`, cuenta habilitada y pertenencia). Pedidos y entregas siguen pendientes | `AddressIntegrationTest`, `CartIntegrationTest` (401, 403, 404 por pertenencia) |
| **RNF-07** (denegar accesos sin autenticación o permisos) | **Verificado en los accesos implementados** | Sin token, otros roles (restaurante, repartidor, administrador), cuenta inhabilitada y recursos de otro cliente |
| **RNF-11** (≥ 90 % de líneas de lógica de negocio) | **Verificado localmente**: 97,1 % en backend y 98,33 % en frontend; publicación en CI aplazada | Informes de JaCoCo (`backend/target/site/jacoco`) y de Vitest |
| **RNF-01** (1 000 usuarios concurrentes, < 2 s) y **RNF-03** (caché) | **Pendiente (aplazado)** | Sin evaluación de carga ni caché en esta etapa |
| **RNF-06**, **RNF-12** | **Pendiente (aplazado)** | HTTPS del despliegue y CI/CD |

## Criterios de éxito de la spec

| Criterio | Estado | Evidencia |
| --- | --- | --- |
| SC-001 (carrito de 3 productos y dirección en < 3 min; persiste tras reiniciar) | **Parcial** | Persistencia verificada en BD por las pruebas de integración; el cronometraje y el reinicio de la aplicación dependen del recorrido manual |
| SC-002 (un solo restaurante; nada no elegible al agregar) | **Verificado** | `CartIntegrationTest`, `CartConcurrencyIntegrationTest` |
| SC-003 (dirección fuera de zona rechazada sin guardarse) | **Verificado** | `AddressIntegrationTest`, `AddressServiceTest` |
| SC-004 (importes correctos y recalculados) | **Verificado** | `CartPricingTest` (redondeo), `CartTotalsIntegrationTest` (recálculo tras cada cambio) |
| SC-005 (accesos indebidos rechazados) | **Verificado** | `AddressIntegrationTest`, `CartIntegrationTest` |
| SC-006 (< 2 s con datos locales) | **Pendiente**: no se midió de forma formal. Las pruebas responden en milisegundos con pocos datos, lo que no acredita el criterio | Cronometrar durante el recorrido manual |

## Consistencia y concurrencia (constitución IV)

- **Altas simultáneas del mismo producto:** ocho altas de 2 unidades suman exactamente 16 en una sola
  línea y un solo carrito, verificado contra PostgreSQL.
- **Máximo por producto bajo concurrencia:** seis altas de 5 unidades terminan con 4 aceptadas y 2 con
  409; el persistido es 20.
- **Un solo restaurante:** dos altas simultáneas de restaurantes distintos en un carrito vacío terminan
  con una 200 y una 409; no se mezclan restaurantes.

## Regla de importes (FR-010)

Todo importe se calcula en el servidor con `BigDecimal`: total de línea exacto, comisión redondeada una
sola vez (medio hacia arriba) y total igual a la suma de los componentes ya redondeados. Un importe o
precio enviado por el cliente se rechaza (400). Las respuestas salen siempre con dos decimales. La
spec 006 debe volver a invocar `CartPricing` al confirmar el pedido.

## Valores de negocio

La zona (distritos cubiertos), la tarifa fija de envío y el porcentaje de comisión son **configuración sin
valores por defecto** (`MIKUNA_DELIVERY_DISTRICTS`, `MIKUNA_DELIVERY_FEE`, `MIKUNA_COMMISSION_RATE` en
`backend/.env`). Las pruebas usan valores solo de prueba (Ayacucho, San Juan Bautista y Carmen Alto;
envío 5.00; comisión 5 %). **El usuario debe indicar los valores reales**; sin ellos el backend no
arranca.

## Cambios respecto al plan y las tareas

- `DeliveryProperties` vive en `common/config` (con `DeliveryConfig`), no en `cart/service`: la usan
  `address` (zona) y `cart` (importes) y así no hay dependencia circular.
- `AddressResponse` no incluye `selected`: la dirección seleccionada se ve en `CartResponse.address`. Si
  `address` leyera la selección del carrito dependería de `cart`, que ya depende de `address`.
- Al eliminar una dirección, `address` publica `AddressDeletedEvent` y `CartService` lo escucha dentro de
  la misma transacción para dejar de seleccionarla.
- El carrito se crea con `INSERT … ON CONFLICT DO NOTHING` y luego se bloquea la fila: una violación de
  `UNIQUE` abortaría la transacción de PostgreSQL y no se podría releer.
- `get` no crea el carrito: un cliente sin carrito ve uno vacío, sin escribir.
- Se desactivó `spring.jackson.deserialization.accept-float-as-int` (principal y pruebas): por defecto
  Jackson aceptaba `1.5` como cantidad `1`.
- `ApiError` del frontend expone el `code` del `ProblemDetail` (distingue `CART_OTHER_RESTAURANT`).
- No hay aviso de «precio cambiado» (el carrito no guarda precios): se muestra siempre el vigente.
- El botón «Agregar al carrito» se muestra a cualquier sesión; el backend responde 403 a quien no es
  cliente. El enlace a carrito y direcciones está en «Mi cuenta» (no en la cabecera del inicio, que no
  conoce el rol).
- Se quitó un `customer()` privado de `AccessControlIntegrationTest`, idéntico al que ahora ofrece
  `IntegrationTestSupport`.

## Pendiente

- Recorrido manual de [quickstart.md](./quickstart.md) con los valores reales (requiere que el usuario
  indique los valores de negocio) y medición de SC-001 y SC-006.
