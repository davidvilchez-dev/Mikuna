# Data Model: Asistente de IA en chat flotante

**Sin cambios de esquema**: ninguna tabla, columna ni índice nuevo (FR-017). Todo es transitorio o de solo
lectura sobre datos existentes.

## Entidades transitorias

### Conversación (navegador)

- Lista ordenada de mensajes en `sessionStorage` bajo `mikuna.assistant`; se borra al cerrar sesión o expirar.
- Solo se envían al servidor los últimos 10 mensajes.

### Mensaje

| Campo | Tipo | Regla |
| --- | --- | --- |
| `role` | `user` \| `assistant` | Solo esos dos valores; cualquier otro se rechaza. |
| `text` | texto | No vacío tras recortar espacios; máximo 500 caracteres. |

Reglas de la petición (`ChatRequest`): entre 1 y 10 mensajes; el último es `user`; los roles pueden
alternarse libremente pero no se admite otro rol.

### Contador de uso (servidor, memoria)

| Campo | Significado |
| --- | --- |
| clave | `accountId` del cliente autenticado |
| valor | instantes de los mensajes aceptados en los últimos 60 s |

Se purga al consultar. Se pierde al reiniciar (aceptable: protege cuota, no es regla de negocio).

## Vistas de solo lectura (resultado de las herramientas)

### DishSuggestion

| Campo | Origen |
| --- | --- |
| `name` | `Product.name` |
| `description` | `Product.description`, recortada a 200 caracteres |
| `category` | `Product.category` |
| `price` | `Product.price` (soles) |
| `restaurant` | `Restaurant.name` |
| `open` | `OrderEligibility.attentionStatus(restaurant) == OPEN` |

Solo productos con `active = true` y `available = true` de restaurantes que devuelve `findVisible()`
(aprobados, cuenta habilitada). No incluye identificadores, imágenes, propietario ni datos personales.

### OpenRestaurant

| Campo | Origen |
| --- | --- |
| `name` | `Restaurant.name` |
| `category` | `Restaurant.category` |
| `zone` | `Restaurant.zone` |
| `todaySchedule` | horario del día en la zona de la plataforma |

Solo restaurantes con atención `OPEN` según `OrderEligibility`.

## Configuración (no es dato persistente)

| Propiedad | Variable de entorno | Por defecto |
| --- | --- | --- |
| `gemini.api-key` | `GEMINI_API_KEY` | vacía (el chat responde `ASSISTANT_UNAVAILABLE`) |
| `gemini.model` | `GEMINI_MODEL` | modelo «flash» vigente (research §1) |
| `mikuna.assistant.rate-limit-per-minute` | `MIKUNA_ASSISTANT_RATE_LIMIT` | 10 |
| `mikuna.assistant.max-message-length` | `MIKUNA_ASSISTANT_MAX_LENGTH` | 500 |
