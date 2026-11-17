# Implementation Plan: Registro e inicio de sesión de Mikuna

**Branch**: `002-registro-acceso` (directorio de spec; sin rama Git nueva) | **Date**: 2026-11-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-registro-acceso/spec.md`

## Summary

Este incremento implementa RF-01 (registro público de cliente, restaurante y repartidor; inicio y
cierre de sesión de los cuatro roles; alta local de administradores) y la autorización mínima de
RF-04 sobre los accesos que se implementan. Las piezas son:

- **Frontend:** pantallas React de registro, acceso y sesión propia, conectadas a los botones
  provisionales del inicio existente.
- **Backend:** el módulo Spring Boot `account` (cuenta, persistencia y reglas de registro) y el
  módulo `auth` (login, tokens JWT firmados con vencimiento y consulta de la sesión propia).
- **Seguridad común:** sin estado y basada en tokens. En cada acceso protegido se vuelven a leer
  el rol y el estado desde PostgreSQL.
- **Persistencia:** PostgreSQL local mediante JPA con `ddl-auto=update`. La unicidad del correo
  la garantiza una restricción `UNIQUE`.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.2); TypeScript ~6.0 con React 19

**Primary Dependencies**:
- Backend: Spring Web MVC, Spring Data JPA, Spring Security, Bean Validation y
  `spring-boot-starter-security-oauth2-resource-server` (Nimbus JWT).
- Frontend: Vite 8, Tailwind 4 y `react-router` para las rutas `/registro`, `/iniciar-sesion` y
  `/mi-cuenta`.

**Storage**: PostgreSQL local (`mikuna_db`, creada por el usuario). Tabla `cuentas` generada por
`spring.jpa.hibernate.ddl-auto=update`, sin herramientas de migración.

**Testing**:
- Backend: JUnit 5 + Mockito (unitarias); `@SpringBootTest` + MockMvc contra una base local
  `mikuna_test_db` (integración); JaCoCo para RNF-11.
- Frontend: Vitest para la lógica de validación y de sesión.
- Comprobación manual del navegador. Sin Playwright.

**Target Platform**: Navegador de escritorio y móvil sobre HTTP en localhost. Vite en el puerto
5173, que reenvía `/api` al backend en el puerto 8080.

**Project Type**: Aplicación web (frontend React + monolito Spring Boot)

**Performance Goals**: Sin objetivo propio. RNF-01 está aplazado y no aplica a la autenticación.

**Constraints**:
- La API no guarda sesiones en memoria.
- Contraseñas protegidas con un hash adaptativo con sal.
- Ninguna respuesta ni registro expone contraseñas o secretos.
- Errores de login genéricos.
- 401 o 403 en todos los accesos protegidos.

**Scale/Scope**: 3 endpoints, 1 entidad, 3 pantallas nuevas y ajustes en 5 componentes del inicio.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Comprobación | Estado |
| --- | --- | --- |
| I. Monolito modular | Solo dos módulos nuevos (`account`, `auth`) más `common/security` y `common/exception`. No se crean interfaces vacías ni módulos futuros. | ✅ |
| II. Responsabilidades | Controladores para HTTP, servicios para las reglas y repositorio JPA. El backend revalida todo e ignora el rol o estado que envíe el cliente; el rol de administrador no se acepta desde fuera. | ✅ |
| III. Seguridad | HTTP local. Tokens JWT sin estado. Contraseñas con hash PBKDF2 con sal. Rutas públicas: register y login; protegida: `/api/auth/me`. 401/403 probados. Sin secretos en respuestas, registros ni commits (el secreto JWT y las credenciales de la BD están en `backend/.env`, que no se versiona). | ✅ |
| IV. Concurrencia | El invariante «un correo normalizado = una cuenta» lo garantiza la restricción `UNIQUE` de PostgreSQL y se verifica con una prueba de integración concurrente. | ✅ |
| V. Pruebas | JUnit + Mockito, integración contra PostgreSQL local, JaCoCo ≥ 90 % de líneas de negocio y Vitest para la lógica del frontend. Sin Playwright. | ✅ |
| VI. Trazabilidad | RF-01 y RF-04 (parcial). RF-05 y RF-06 se mantienen pendientes. RNF-07 y RNF-11 son aplicables; RNF-06 sigue aplazado. | ✅ |
| VII. Incremental | Solo lo que exige la spec, con tecnologías ya elegidas. `react-router` y el starter de resource server son decisiones rutinarias (ver research.md). | ✅ |

**Discrepancias heredadas (se señalan sin ampliar el alcance):**

1. **Flyway:** `backend/pom.xml` incluye `spring-boot-starter-flyway`, `flyway-database-postgresql`
   y `spring-boot-starter-flyway-test`. Esto contradice la regla de la constitución de no añadir
   herramientas de migración y usar `ddl-auto=update`. Este incremento es el primero que persiste
   datos, así que **se retiran** como dependencia necesaria.
2. **Playwright:** se mantiene instalado por decisión del usuario (constitución 3.0.0, principio
   V). En este incremento no se configura, no se amplía ni se ejecuta.

Resultado: **PASA** (re-evaluado después del diseño de la fase 1, sin cambios).

## Project Structure

### Documentation (this feature)

```text
specs/002-registro-acceso/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── auth-api.md
│   └── ui-routes.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks (pendiente)
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                   # − flyway; + oauth2-resource-server, jacoco
├── .env                                      # valores locales (no versionado)
├── .env.example                              # plantilla versionada
├── src/main/resources/application.properties # importa .env; + JWT, Jackson estricto
└── src/main/java/com/mikuna/backend/
    ├── account/
    │   ├── model/        Account, Role, AccountStatus, RestaurantApproval
    │   ├── repository/   AccountRepository
    │   ├── service/      AccountRegistrationService, EmailNormalizer
    │   └── AdminAccountInitializer.java      # perfil admin-setup
    ├── auth/
    │   ├── controller/   AuthController
    │   ├── dto/          RegisterRequest, LoginRequest, LoginResponse, AccountResponse
    │   └── service/      AuthService, TokenService
    └── common/
        ├── security/     SecurityConfig, AccountStatusFilter, AccountPrincipal
        └── exception/    GlobalExceptionHandler, EmailAlreadyRegisteredException, …
backend/src/test/java/com/mikuna/backend/
├── account/   (unit: registro, normalización; integration: unicidad concurrente, inicializador admin)
├── auth/      (unit: AuthService, TokenService; integration: endpoints 200/400/401/403/409)
└── resources/application.properties          # datasource → mikuna_test_db

frontend/
├── package.json        # + react-router, vitest, @vitest/coverage-v8
├── vite.config.ts      # proxy /api → http://localhost:8080; config de test
└── src/
    ├── App.tsx         # rutas: /, /registro, /iniciar-sesion, /mi-cuenta
    ├── features/auth/
    │   ├── RegisterPage.tsx, LoginPage.tsx, AccountPage.tsx
    │   ├── auth-api.ts            # fetch a /api/auth/*
    │   ├── auth-validation.ts     # reglas espejo (+ .test.ts)
    │   ├── session-storage.ts     # token + expiración (+ .test.ts)
    │   └── auth.css
    └── features/home/ y features/public-pages/   # los botones de cuenta pasan a enlaces reales
```

**Structure Decision**: Se mantiene la aplicación web existente (`backend/` + `frontend/`). El
backend se organiza por funcionalidad, como pide la constitución (I). En el frontend se añade
`features/auth` al lado de `features/home`.

## Complexity Tracking

Sin violaciones que justificar.
