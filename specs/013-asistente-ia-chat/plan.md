# Implementation Plan: Asistente de IA en chat flotante

**Branch**: `013-asistente-ia-chat` (sin rama Git nueva) | **Date**: 2026-11-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/013-asistente-ia-chat/spec.md`

## Summary

Un chat flotante para clientes que responde dudas sobre Mikuna y recomienda platos y restaurantes con datos
reales, usando Gemini (plan gratuito) con *function calling* desde el backend.

- **Backend, módulo nuevo `assistant`** (constitución I: la funcionalidad lo necesita):
  `POST /api/customer/assistant/chat` (protegido, rol `CUSTOMER`) recibe el historial reciente y responde por
  **SSE** (research §2). `AssistantService` valida, aplica el límite por cliente y ejecuta el bucle de
  herramientas; el proveedor queda tras la interfaz `AssistantModel` con la implementación `GeminiModel`
  (constitución II, enmienda 3.2.0; research §1).
- **Herramientas de solo lectura** sobre el catálogo público (research §3): `buscar_platos`,
  `restaurantes_abiertos` y `listar_categorias`, resueltas por `AssistantCatalogTools`, que reutiliza
  `RestaurantRepository.findVisible`, `ProductRepository` y `OrderEligibility` (misma regla de «abierto»).
- **Protección** (research §4, §5): límite de 10 mensajes/min por cuenta en memoria (429 con `Retry-After`),
  validación de longitud y roles del historial, errores del proveedor traducidos a códigos amables
  (`ASSISTANT_BUSY`, `ASSISTANT_UNAVAILABLE`), clave solo en `backend/.env`.
- **Frontend, `features/assistant`:** botón flotante + panel montados una sola vez en `App.tsx`, visibles solo
  con sesión de cliente; consumo del SSE con `fetch` y lector de flujo (el `Authorization` no cabe en
  `EventSource`); historial en `sessionStorage` por sesión (research §6, §7). Lógica pura con Vitest.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.2); TypeScript ~6.0 con React 19

**Primary Dependencies**: las ya instaladas; **ninguna nueva**. El backend llama a la API REST de Gemini con
`RestClient` + `JdkClientHttpRequestFactory`, igual que `StripeGateway`; el frontend usa `fetch`.

**Storage**: ninguna tabla ni columna nueva (FR-017). El contador de uso vive en memoria; el historial, en
`sessionStorage` del navegador.

**Testing**: JUnit 5 + Mockito (servicio del chat, herramientas del catálogo, límite de uso, traducción de
errores, `GeminiModel` con servidor HTTP simulado de `MockRestServiceServer`/`HttpServer` local);
`@SpringBootTest` + MockMvc sobre `mikuna_test_db` con `AssistantModel` simulado (401/403, 429, validación,
flujo SSE); Vitest para el lector de SSE y el estado del chat. JaCoCo ≥ 90 % de líneas de negocio. Sin
llamadas a Gemini real (constitución V); una prueba manual opcional contra el servicio real en el quickstart.

**Target Platform**: igual que 002–012.

**Project Type**: Aplicación web (frontend React + monolito Spring Boot)

**Performance Goals**: SC-001 primera respuesta < 10 s y SC-007 primer texto < 3 s en condiciones normales;
dependen casi por completo del proveedor. Tope de 3 vueltas de herramientas por mensaje y de 10 resultados
por herramienta.

**Constraints**:
- Solo datos públicos al proveedor; nada de cuentas, pedidos, direcciones ni pagos (FR-008, constitución III).
- Herramientas de solo lectura; el asistente no ejecuta acciones (FR-009).
- La clave de Gemini solo en `backend/.env`, enviada por cabecera (nunca en la URL, que podría registrarse).
- Sin clave configurada, la aplicación arranca igual y el chat responde `ASSISTANT_UNAVAILABLE` (no se
  bloquea el resto de Mikuna por una funcionalidad opcional).
- El backend no guarda conversaciones; el cliente reenvía el historial recortado (research §6).

**Scale/Scope**: 1 endpoint, 1 módulo backend (~8 clases), 3 herramientas, 1 propiedad nueva de arranque
opcional, 4 variables de entorno, 1 componente flotante y 3 archivos de apoyo en frontend.

## Constitution Check

| Principio | Comprobación | Estado |
| --- | --- | --- |
| I. Monolito modular | Módulo `assistant` nuevo porque lo pide la funcionalidad; depende de `catalog` y `restaurant` en lectura (sin ciclos: ninguno depende de `assistant`). Sin infraestructura nueva (ni Redis ni colas). | ✅ |
| II. Responsabilidades | Controlador solo HTTP/SSE; `AssistantService` coordina; reglas del catálogo en `AssistantCatalogTools`; proveedor tras `AssistantModel` (excepción permitida por la enmienda 3.2.0). El backend valida y recorta lo que envía el cliente. | ✅ |
| III. Seguridad | Gemini permitido por 3.2.0; HTTPS; clave en `backend/.env`; solo texto del cliente y catálogo público; ruta protegida bajo `/api/customer/**` (rol, estado de cuenta vía `AccountStatusFilter`); límite por cliente; sin acciones de escritura. 401/403 probados. | ✅ |
| IV. Concurrencia | Sin operaciones críticas ni escrituras. El contador usa estructura concurrente; no es una invariante de negocio. | ✅ n/a |
| V. Pruebas | Unitarias con dobles, integración acotada, Vitest; JaCoCo ≥ 90 %; sin llamadas reales a Gemini. | ✅ |
| VI. Trazabilidad | Instrucción del docente vía el usuario (sin RF/RNF atribuido); apoya RF-09/RF-10 (consulta) y RF-06 (restaurante habilitado) sin modificarlos; RNF-07 aplicado a la nueva ruta; RNF-11. | ✅ |
| VII. Incremental | Solo lo de la spec; sin pedidos desde el chat, voz ni adjuntos; sin investigación de alternativas (research §1 decide directamente). | ✅ |

**Puntos a señalar (sin ampliar el alcance):**

1. **Datos de restaurantes en el plan gratuito.** Nombres, descripciones y precios de platos son datos
   comerciales públicos (RF-09/RF-10), pero en el plan gratuito Google puede usarlos para mejorar sus
   productos. Se acepta por ser públicos; lo escrito por el cliente en el chat también viaja, y por eso el
   asistente avisa en el saludo de no compartir datos personales (research §8).
2. **Descripciones escritas por restaurantes** son texto no confiable. El resultado de las herramientas se
   marca como datos y se limita su longitud para reducir inyección de instrucciones (research §8).
3. **Límite en memoria** con varias instancias sería por instancia; en esta etapa local hay una sola (RNF-04
   aplazado). No es una garantía de negocio.
4. **Nombre del modelo** configurable (`GEMINI_MODEL`); los topes del plan gratuito cambian y deben revisarse
   al ejecutar el quickstart (research §1).

Resultado: **PASA** (re-evaluado tras la fase 1, sin cambios).

## Project Structure

### Documentation (this feature)

```text
specs/013-asistente-ia-chat/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/assistant-api.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/mikuna/backend/
├── assistant/                                  # módulo nuevo
│   ├── controller/   AssistantController (SSE)
│   ├── dto/          ChatRequest, ChatMessageDto, DishSuggestion, OpenRestaurant
│   ├── service/      AssistantService, AssistantModel (interfaz), GeminiModel,
│   │                 GeminiProperties, AssistantCatalogTools, AssistantUsageLimiter,
│   │                 AssistantPrompt
├── common/exception/ AssistantUnavailableException, AssistantBusyException,
│                     AssistantRateLimitedException (+ GlobalExceptionHandler)
backend/src/main/resources/application.properties   # + gemini.* y mikuna.assistant.*
backend/.env.example                                 # + GEMINI_API_KEY, GEMINI_MODEL, límites
backend/src/test/...                                 # unitarias + integración

frontend/src/features/assistant/
├── AssistantWidget.tsx        # botón flotante + panel
├── assistant-api.ts           # fetch + lector de SSE
├── assistant-logic.ts (+ test)  # estado del chat, recorte del historial, análisis de eventos
└── assistant.css
frontend/src/App.tsx           # monta <AssistantWidget /> una vez
```

**Structure Decision**: módulo `assistant` independiente (constitución I) porque no pertenece a `catalog` ni
a `order`; solo lee de otros módulos. El proveedor tiene interfaz propia únicamente por la decisión del
usuario recogida en la constitución 3.2.0 (II); no se crean interfaces para el resto.

## Complexity Tracking

Sin violaciones que justificar. La interfaz `AssistantModel` es una excepción expresa de la constitución
3.2.0, no una violación.
