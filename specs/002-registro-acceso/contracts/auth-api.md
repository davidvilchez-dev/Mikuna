# Contract: API de autenticación

Base: `http://localhost:8080/api/auth`. JSON en UTF-8. Los errores siguen `ProblemDetail`
(`application/problem+json`) con los campos `type`, `title`, `status` y `detail`. En los errores
de validación se añade `errors: { campo: mensaje }`. Ninguna respuesta incluye la contraseña, el
hash ni trazas internas.

## POST `/register`: **público**

Request (cualquier campo desconocido → 400):

```json
{ "name": "Ana Quispe", "email": " Ana@Mail.com ", "password": "contraseña-segura",
  "passwordConfirmation": "contraseña-segura", "role": "CUSTOMER" }
```

`role` ∈ `CUSTOMER | RESTAURANT | COURIER`.

| Resultado | Código | Cuerpo |
| --- | --- | --- |
| Creada | 201 | `AccountResponse` |
| Campos inválidos, confirmación distinta, `role` = `ADMIN` o desconocido, campo privilegiado | 400 | ProblemDetail + `errors` |
| Correo (normalizado) ya registrado, con cualquier rol | 409 | ProblemDetail `"El correo ya está registrado"` |

`AccountResponse`:

```json
{ "id": "uuid", "name": "Ana Quispe", "email": "ana@mail.com", "role": "CUSTOMER",
  "status": "ENABLED", "restaurantApproval": null }
```

Para `RESTAURANT`, `restaurantApproval` vale `"PENDING"`.

## POST `/login`: **público**

Request: `{ "email": "...", "password": "..." }`

| Resultado | Código | Cuerpo |
| --- | --- | --- |
| Credenciales válidas y cuenta `ENABLED` | 200 | `{ "accessToken": "jwt", "tokenType": "Bearer", "expiresAt": "ISO-8601", "account": AccountResponse }` |
| Correo inexistente o contraseña incorrecta | 401 | `"Correo o contraseña incorrectos"` (mismo mensaje en los dos casos) |
| Credenciales válidas pero cuenta `DISABLED` | 403 | `"La cuenta no está habilitada"` |
| Cuerpo inválido | 400 | ProblemDetail |

## GET `/me`: **protegido** (`Authorization: Bearer <jwt>`)

| Resultado | Código | Cuerpo |
| --- | --- | --- |
| Token válido y cuenta `ENABLED` | 200 | `AccountResponse` del titular del token |
| Sin token, firma manipulada, token vencido o cuenta inexistente | 401 | ProblemDetail (+ `WWW-Authenticate: Bearer`) |
| Cuenta `DISABLED` | 403 | ProblemDetail |

`/me` no acepta parámetros de identidad. Lo que envíe el cliente para elegir otra cuenta, rol o
estado se ignora (US3-4).

## Otras rutas

- `GET /actuator/health`: público.
- Cualquier otra ruta `/api/**`: protegida (401 sin token).
- No hay endpoint de logout (ver research §2).
