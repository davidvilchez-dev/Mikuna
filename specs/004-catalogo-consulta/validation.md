# Validación del incremento 004: catálogo y consulta de restaurantes y productos

Fecha de las comprobaciones: 2026-11-17. Estados: **verificado** (comprobado con pruebas o ejecución),
**parcial** (comprobado solo en lo implementado) o **pendiente** (no comprobado).

## Resultado de las comprobaciones automáticas

| Comprobación | Resultado |
| --- | --- |
| `./mvnw verify` (backend) | 243 pruebas, 0 fallos. Incluye 78 de este incremento (75 nuevas y 3 añadidas a `OrderEligibilityTest`) y las de 002 y 003. |
| JaCoCo, líneas de lógica de negocio | **97,7 %** (757/775); ramas 90,9 % (291/320). `catalog.service` 97,3 % (256/263). Exclusiones iguales a 002: `dto`, `SecurityConfig` y `BackendApplication`. |
| `pnpm test:coverage` (frontend) | 40 pruebas, 0 fallos. Líneas medidas 98,07 % (102/104); `catalog-validation.ts` 96,15 %. |
| `pnpm lint` y `pnpm build` | Sin errores. |
| Playwright | No se usó (constitución V). |
| Llamadas reales a Cloudinary en las pruebas | Ninguna: `CloudinaryImageStorage` se sustituye por un mock y su firma se prueba con un servidor simulado. |

## Requisitos

| Requisito | Estado | Evidencia |
| --- | --- | --- |
| **RF-08** (alta, edición y retiro de productos con categoría, precio, imagen y disponibilidad) | **Verificado** en backend; interfaz verificada por compilación, lint y validación. Recorrido manual con Cloudinary real **pendiente** (T042) | `ProductServiceTest`, `ProductManagementIntegrationTest`, `ImageValidatorTest`, `CloudinaryImageStorageTest`, `catalog-validation.test.ts` |
| **RF-09** (búsqueda de restaurantes por nombre, categoría y ubicación) | **Verificado** en backend; interfaz por compilación y lint | `PublicCatalogServiceTest`, `PublicSearchIntegrationTest` |
| **RF-10** (productos disponibles de cada restaurante) | **Verificado** en backend; interfaz por compilación y lint | `PublicCatalogServiceTest`, `PublicCatalogIntegrationTest` |
| **RF-04** (autorización por rol, estado de cuenta y relación con el recurso) | **Parcial**: verificado en gestión del catálogo (rol, estado de cuenta, estado del restaurante y pertenencia) y en las rutas públicas de solo lectura. Las relaciones con pedidos y entregas siguen pendientes | `ProductManagementIntegrationTest` (401, 403, 404 por pertenencia), `PublicSearchIntegrationTest` |
| **RNF-07** (denegar accesos sin autenticación o permisos) | **Verificado en los accesos implementados** | Sin token, token manipulado, otros roles, cuenta inhabilitada, restaurante pendiente o rechazado, producto ajeno y escritura sobre `/api/public/**` |
| **RNF-11** (≥ 90 % de líneas de lógica de negocio) | **Verificado localmente**: 97,7 % en backend y 98,07 % en frontend; publicación en CI aplazada | Informe de JaCoCo (`backend/target/site/jacoco`) y de Vitest |
| **RNF-01** (1 000 usuarios concurrentes, < 2 s) y **RNF-03** (caché) | **Pendiente (aplazado)** | Sin evaluación de carga ni caché en esta etapa |
| **RNF-06**, **RNF-12** | **Pendiente (aplazado)** | HTTPS del despliegue y CI/CD |

## Criterios de éxito de la spec

| Criterio | Estado | Evidencia |
| --- | --- | --- |
| SC-001 (producto completo en < 2 min; persiste tras reiniciar) | **Parcial** | Persistencia verificada en BD por las pruebas de integración; el cronometraje depende del recorrido manual (T042) |
| SC-002 (encontrar un restaurante y abrir su catálogo en < 1 min) | **Pendiente** | Recorrido manual (T042) |
| SC-003 (no visibles no aparecen; pausado y fuera de horario con su aviso) | **Verificado** | `PublicSearchIntegrationTest`, `PublicCatalogIntegrationTest` |
| SC-004 (retirado o no disponible nunca visible) | **Verificado** | `PublicCatalogIntegrationTest` (cambio de disponibilidad y retiro) |
| SC-005 (accesos indebidos rechazados; sin datos de cuenta en lo público) | **Verificado** | `ProductManagementIntegrationTest` y la comprobación de claves en `PublicSearchIntegrationTest` y `PublicCatalogIntegrationTest` |
| SC-006 (consultas en < 2 s con datos locales) | **Pendiente**: no se midió de forma formal. Las pruebas de integración responden en milisegundos con pocos datos, lo que no acredita el criterio | Cronometrar durante T042 |

## Consistencia y concurrencia (constitución IV)

- **Duplicado de nombre y categoría:** cuatro altas simultáneas idénticas crean una sola fila (una 201 y
  tres 409), verificado contra PostgreSQL.
- **Edición simultánea:** dos ediciones con la misma versión terminan con un 200 y un 409; la versión
  persistida avanza una sola vez.

## Cambios respecto al plan y las tareas

- `CloudinaryImageStorage.upload(content, filename)` no recibe el tipo de contenido (no hace falta para la
  subida firmada).
- Un archivo mayor de 2 MB responde **413** también desde `ImageValidator` (`ImageTooLargeException`), no
  solo desde el límite del contenedor; así el contrato es uno solo y se puede probar sin servidor real.
- La paginación en memoria vive en `Paging` (paquete `service`), no en un DTO, para que cuente en la
  medición de cobertura.
- El borrado de la imagen anterior al sustituirla se ejecuta tras confirmar la transacción.
- US3 depende de US2 en el código (comparten `PublicCatalogService`, el controlador y los DTO de
  restaurante), por lo que se implementó después de US2.
- `OrderEligibility` se refactorizó sin cambiar su resultado; sus pruebas de 003 siguen en verde.

## Pendiente y aplazado

- **Recorrido manual (T042)** con las credenciales de Cloudinary de `backend/.env`, backend en 8080 y
  frontend en 5173: lo realiza el responsable del proyecto. Sube imágenes reales a su cuenta.
- Aplazados por la constitución: E2E automatizadas, carga (RNF-01), caché y réplica (RNF-03), HTTPS
  (RNF-06) y CI/CD (RNF-12). No se declaran cumplidos.
- Fuera de alcance: carrito y direcciones (spec 005), pedidos, pagos, entregas y gestión administrativa de
  catálogos (RF-32).
