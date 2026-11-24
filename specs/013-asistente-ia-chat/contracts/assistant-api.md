# Contract: API del asistente

## `POST /api/customer/assistant/chat` — protegido (rol `CUSTOMER`)

Declarado **protegido** (constitución III): exige JWT válido y cuenta habilitada; la regla
`/api/customer/**` ya exige el rol. Visitantes: 401; otros roles: 403.

### Petición (`application/json`)

```json
{ "messages": [ { "role": "user", "text": "quiero algo vegetariano bajo 20 soles" } ] }
```

| Campo | Regla |
| --- | --- |
| `messages` | 1 a 10 elementos; el último con `role = user` |
| `messages[].role` | `user` o `assistant` |
| `messages[].text` | no vacío, máximo 500 caracteres (configurable) |

Campos desconocidos se rechazan (configuración Jackson del proyecto).

### Respuesta de éxito (`200`, `text/event-stream`)

Secuencia de eventos:

| Evento | Datos (JSON) | Significado |
| --- | --- | --- |
| `delta` | `{ "text": "…" }` | Fragmento de texto de la respuesta, en orden. |
| `done` | `{}` | Fin normal del flujo. |
| `error` | `{ "code": "…", "message": "…" }` | Fallo con el flujo ya abierto; es el último evento. |

Códigos de `error` en el flujo: `ASSISTANT_BUSY`, `ASSISTANT_UNAVAILABLE`.

### Errores previos al flujo (`ProblemDetail`)

| HTTP | `code` | Cuándo | Mensaje |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Cuerpo inválido: vacío, rol inválido, mensaje vacío o demasiado largo, último no `user`. | Mensaje de validación existente. |
| 401 | — | Sin sesión válida. | Respuesta estándar de seguridad. |
| 403 | — | Rol distinto de cliente. | Respuesta estándar de seguridad. |
| 429 | `ASSISTANT_RATE_LIMITED` | Más de 10 mensajes en 60 s por cliente. Incluye cabecera `Retry-After` (segundos). | «Escribes muy rápido. Intenta de nuevo en N segundos.» |
| 503 | `ASSISTANT_UNAVAILABLE` | Proveedor sin configurar o inalcanzable antes de empezar. | «El asistente no está disponible por ahora. Intenta más tarde.» |

Mensajes fijos para `ASSISTANT_BUSY`: «El asistente está ocupado. Intenta de nuevo en un minuto.»

### Garantías

- Al proveedor solo se envían: instrucciones del sistema, los mensajes recibidos y los resultados de las
  herramientas. Nunca identificadores de cuenta, correos, pedidos, direcciones ni pagos.
- Un mensaje rechazado por validación o límite no llega al proveedor.
- Las respuestas de error nunca incluyen la clave, trazas ni el cuerpo del proveedor.

## Herramientas del modelo (interno, no son endpoints)

| Nombre | Parámetros | Devuelve |
| --- | --- | --- |
| `buscar_platos` | `texto?`, `categoria?`, `precio_maximo?`, `restaurante?`, `limite?` (máx. 10) | Lista de `DishSuggestion` por precio ascendente |
| `restaurantes_abiertos` | `categoria?` | Lista de `OpenRestaurant` (máx. 10) |
| `listar_categorias` | — | Categorías de platos y de restaurantes |

Todas son de solo lectura y sin parámetros que identifiquen al cliente.
