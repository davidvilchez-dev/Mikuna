# Quickstart: validar registro e inicio de sesión

## Prerrequisitos

- Java 25, pnpm y PostgreSQL local en ejecución.
- Bases locales: `mikuna_db` (aplicación) y `mikuna_test_db` (pruebas de integración).
- `backend/.env` con `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MIKUNA_JWT_SECRET` (≥ 32 caracteres)
  y `MIKUNA_JWT_EXPIRATION`. Si no existe, copiar `backend/.env.example` y completarlo. Spring lo
  carga desde `application.properties`; no hace falta definir variables en PowerShell.

## 1. Pruebas automatizadas y cobertura

```powershell
cd backend; .\mvnw verify          # unit + integración + informe JaCoCo
# Informe: backend/target/site/jacoco/index.html (≥ 90 % de líneas en account/ y auth/ service)
cd ..\frontend; pnpm test:coverage          # Vitest: validación y sesión
pnpm build                                 # compilación TypeScript + Vite
```

Resultado esperado: todo en verde, incluidas las pruebas de 409 concurrente y de 401/403
(ver [contracts/auth-api.md](./contracts/auth-api.md)).

## 2. Crear un administrador (procedimiento local)

```powershell
$env:MIKUNA_ADMIN_NAME = "<tu nombre>"; $env:MIKUNA_ADMIN_EMAIL = "<tu correo>"; $env:MIKUNA_ADMIN_PASSWORD = "<tu contraseña>"
cd backend; .\mvnw spring-boot:run "-Dspring-boot.run.profiles=admin-setup" "-Dspring-boot.run.arguments=--spring.main.web-application-type=none"
Remove-Item Env:MIKUNA_ADMIN_PASSWORD
```

Esperado: un mensaje «Administrador creado» y la aplicación se cierra. Si se repite con el mismo
correo, falla sin modificar la cuenta.

## 3. Ejecutar y comprobar manualmente

```powershell
cd backend; .\mvnw spring-boot:run      # http://localhost:8080
cd frontend; pnpm dev                    # http://localhost:5173
```

| # | Pasos | Esperado |
| --- | --- | --- |
| 1 | En el inicio, pulsa «Crear cuenta» y registra un cliente | Confirmación con un enlace para iniciar sesión. |
| 2 | En «Para restaurantes», pulsa «Registrar mi restaurante» | Tipo preseleccionado; tras registrar, aviso de habilitación pendiente. |
| 3 | En «Sé repartidor», pulsa «Quiero ser repartidor» | Tipo repartidor; registro correcto. |
| 4 | Repite el correo del paso 1 (en mayúsculas o con otro rol) | Error «El correo ya está registrado»; no se crea una cuenta nueva. |
| 5 | Inicia sesión con cada uno de los cuatro roles | `/mi-cuenta` muestra el nombre, rol y estado propios. |
| 6 | Introduce una contraseña incorrecta o un correo inexistente | El mismo mensaje genérico en los dos casos. |
| 7 | Cierra sesión y abre `/mi-cuenta` directamente | Redirige a iniciar sesión. |
| 8 | Reinicia el backend e inicia sesión con una cuenta del paso 1 | La cuenta se conserva (SC-001). |
| 9 | Con el backend detenido, envía un registro | Error de operación no completada, sin confirmación. |
| 10 | Navega por las cinco secciones del inicio sin sesión | Todo accesible; «Pedir ahora» sigue en «próximamente». |
