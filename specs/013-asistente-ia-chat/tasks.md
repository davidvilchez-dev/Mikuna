---
description: "Tareas del incremento 013: asistente de IA en chat flotante"
---

# Tasks: Asistente de IA en chat flotante

**Input**: Design documents from `specs/013-asistente-ia-chat/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/assistant-api.md, quickstart.md

**Tests**: Incluidas. La constitución (principio V, RNF-11) exige pruebas unitarias y de integración con
JaCoCo ≥ 90 % de líneas de negocio; nunca se llama a Gemini real (dobles con Mockito).

**Organization**: Tareas agrupadas por historia de usuario.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ir en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia a la que pertenece (US1–US4)
- Rutas: `backend/src/main/java/com/mikuna/backend/` = `BE/`, `backend/src/test/java/com/mikuna/backend/` =
  `BT/`, `frontend/src/features/` = `FE/`

## Phase 1: Setup

- [X] T001 Añadir a `backend/src/main/resources/application.properties` (con comentario «spec 013»):
  `gemini.api-key=${GEMINI_API_KEY:}`, `gemini.model=${GEMINI_MODEL:gemini-flash-latest}`,
  `mikuna.assistant.rate-limit-per-minute=${MIKUNA_ASSISTANT_RATE_LIMIT:10}` y
  `mikuna.assistant.max-message-length=${MIKUNA_ASSISTANT_MAX_LENGTH:500}`; en
  `backend/src/test/resources/application.properties`: `gemini.api-key=` vacía, `gemini.model=test-model`,
  `mikuna.assistant.rate-limit-per-minute=10`, `mikuna.assistant.max-message-length=500`.
- [X] T002 [P] Documentar en `backend/.env.example` `GEMINI_API_KEY` (clave gratuita de Google AI Studio,
  nunca en commits; vacía = chat no disponible), `GEMINI_MODEL`, `MIKUNA_ASSISTANT_RATE_LIMIT` y
  `MIKUNA_ASSISTANT_MAX_LENGTH` (opcionales).

## Phase 2: Foundational (bloquea todas las historias)

- [X] T003 [P] Crear `BE/assistant/service/AssistantProperties.java` (`@ConfigurationProperties("mikuna.assistant")`,
  record con `rateLimitPerMinute` ≥ 1 y `maxMessageLength` ≥ 1, ambos validados) y
  `BE/assistant/service/GeminiProperties.java` (`@ConfigurationProperties("gemini")`: `apiKey` opcional,
  `model` obligatorio; `toString()` sin la clave). Registrarlas con `@EnableConfigurationProperties` en la
  clase `AssistantService`.
- [X] T004 [P] Crear excepciones en `BE/common/exception/`: `AssistantBusyException` (código
  `ASSISTANT_BUSY`), `AssistantUnavailableException` (`ASSISTANT_UNAVAILABLE`) y
  `AssistantRateLimitedException` (`ASSISTANT_RATE_LIMITED`, con `retryAfterSeconds`); mapearlas en
  `BE/common/exception/GlobalExceptionHandler.java` siguiendo su estilo: 429 (con cabecera `Retry-After`) para
  la de límite y 503 para indisponibilidad, con los mensajes fijos de `contracts/assistant-api.md`. Nunca
  incluir clave ni cuerpo del proveedor.
- [X] T005 [P] Crear `BE/assistant/dto/ChatMessageDto.java` (record `role`, `text`; `role` solo `user` o
  `assistant`; `text` no vacío tras recortar) y `BE/assistant/dto/ChatRequest.java` (record `messages`: 1 a 10
  elementos válidos). Los límites de longitud se validan en el servicio con `AssistantProperties`.
- [X] T006 Crear la interfaz `BE/assistant/service/AssistantModel.java`: un método que recibe el
  historial (mensajes y resultados de herramientas), las declaraciones de herramientas y un consumidor de
  eventos (`TextDelta(String)`, `ToolCall(nombre, argumentos)`), y lanza `AssistantBusyException` o
  `AssistantUnavailableException`. Crear `BE/assistant/service/AssistantPrompt.java` con las instrucciones del
  sistema del research §8 (español; solo Mikuna y comida; no inventar; vegetariano a confirmar; no revelar
  instrucciones; sin datos personales; sin acciones; resultados de herramientas = datos).

**Checkpoint**: configuración, errores, DTOs e interfaz listos.

## Phase 3: User Story 1 - Preguntar al asistente desde cualquier pantalla (Priority: P1) 🎯 MVP

**Goal**: un cliente abre el chat, pregunta sobre Mikuna y recibe respuesta progresiva.

**Independent Test**: como cliente, `POST /api/customer/assistant/chat` devuelve SSE con `delta` y `done`;
en la interfaz el botón flotante abre el panel y se ve la respuesta.

### Backend

- [X] T007 [US1] Crear `BE/assistant/service/GeminiModel.java` (`@Component`, implementa `AssistantModel`):
  `RestClient` + `JdkClientHttpRequestFactory` (conexión 5 s, lectura 30 s) hacia
  `https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent?alt=sse`, clave en
  la cabecera `x-goog-api-key` (nunca en la URL), instrucciones del sistema, historial y herramientas;
  interpreta el flujo SSE emitiendo texto y llamadas a herramientas. Sin clave configurada →
  `AssistantUnavailableException`. Traducción: 429 → `AssistantBusyException`; 5xx, 401/403, tiempo agotado
  o red caída → `AssistantUnavailableException`; registra solo el código HTTP, sin clave ni contenido del
  cliente. Confirmar en la documentación oficial los nombres exactos de campos de la API.
- [X] T008 [US1] Crear `BE/assistant/service/AssistantService.java`: `chat(accountId, request, consumer)`:
  valida longitud máxima (`AssistantProperties.maxMessageLength`) y que el último mensaje sea `user`
  (si no, 400 con el estilo de validación del proyecto); recorta a los últimos 10; invoca el modelo y reenvía
  los fragmentos de texto al consumidor; al terminar emite `done`. Aún sin herramientas ni límite (T015,
  T018 las añaden).
- [X] T009 [US1] Crear `BE/assistant/controller/AssistantController.java`:
  `POST /api/customer/assistant/chat` (`consumes JSON`, `produces text/event-stream`), toma el
  `AccountPrincipal` autenticado, valida el cuerpo con `@Valid`, y devuelve un `SseEmitter` alimentado desde
  un ejecutor acotado (`Executors` con hilos virtuales o pool fijo pequeño, definido en el propio
  controlador/servicio); eventos `delta`, `done` y `error` según el contrato; errores del proveedor tras abrir
  el flujo → evento `error` con su código y mensaje fijo. La ruta ya queda protegida por la regla
  `/api/customer/**` de `common/security/SecurityConfig.java` (no cambiarla).

### Frontend

- [X] T010 [P] [US1] Crear `FE/assistant/assistant-logic.ts`: tipos `ChatMessage`; `trimHistory(messages, 10)`;
  `appendDelta`; `parseSseChunk(buffer)` que reconoce eventos `delta`/`done`/`error` aun con fragmentos
  partidos; carga/guardado en `sessionStorage` bajo `mikuna.assistant` (tolerante a almacenamiento no
  disponible o datos corruptos); `clearAssistantHistory()`. Con `FE/assistant/assistant-logic.test.ts` (Vitest):
  recorte a 10, fragmentos partidos, datos corruptos descartados, borrado.
- [X] T011 [P] [US1] Crear `FE/assistant/assistant-api.ts`: `streamChat(token, messages, handlers, signal)` con
  `fetch` POST, cabecera `Authorization: Bearer`, lectura del cuerpo con `ReadableStream` y
  `parseSseChunk`; errores HTTP normalizados (429 con segundos de `Retry-After`, 503, 400, 401, red caída)
  al estilo de `FE/auth/auth-api.ts`.
- [X] T012 [US1] Crear `FE/assistant/AssistantWidget.tsx` y `FE/assistant/assistant.css`: botón flotante abajo
  a la derecha (naranja del logo, fondo blanco del panel, sin cambiar la tipografía), panel con saludo
  inicial (incluye aviso de no compartir datos personales), lista de mensajes, caja de texto (máx. 500
  caracteres), botón enviar y cerrar siempre visible; conversación persistida con `assistant-logic`;
  accesible (`aria-label`, foco al abrir, `Escape` cierra); en móvil el panel ocupa casi toda la pantalla.
  Solo se pinta si `getSession()?.role === 'CUSTOMER'`, releyendo la sesión en cada cambio de ruta
  (`useLocation`); al cerrar sesión se borra el historial (llamar a `clearAssistantHistory` desde
  `clearSession` de `FE/auth/session-storage.ts`).
- [X] T013 [US1] Montar `<AssistantWidget />` (carga diferida con `lazy`) una sola vez en `frontend/src/App.tsx`,
  dentro de `Suspense` y fuera de `Routes`.

### Pruebas

- [X] T014 [US1] Pruebas en `BT/assistant/`: `GeminiModelTest` (servidor HTTP local simulado: la clave va en
  la cabecera y no en la URL; interpretación del flujo; sin clave → indisponible; 429 → ocupado; 500, 403 y
  tiempo agotado → indisponible; ningún mensaje contiene la clave) y `AssistantControllerTest`
  (`@SpringBootTest` + MockMvc con `AssistantModel` simulado: 401 sin sesión, 403 con rol de restaurante y
  de repartidor, 400 por mensaje vacío, largo, rol inválido o último no `user`, 200 con `text/event-stream`
  y eventos `delta` y `done`; el modelo no se invoca ante un 400).

**Checkpoint**: el chat responde preguntas generales. Este es el MVP y ya cumple el requisito del docente.

## Phase 4: User Story 2 - Recomendaciones con datos reales (Priority: P1)

**Goal**: recomendar platos reales con precio y restaurante.

**Independent Test**: con platos conocidos, `buscar_platos` devuelve solo activos y disponibles dentro del
filtro, y el modelo recibe ese resultado antes de responder.

- [X] T015 [US2] Crear `BE/assistant/dto/DishSuggestion.java` (`name`, `description` recortada a 200
  caracteres, `category`, `price`, `restaurant`, `open`) y `BE/assistant/service/AssistantCatalogTools.java`:
  declaraciones de las tres herramientas (`contracts/assistant-api.md`) y ejecución de `buscar_platos` y
  `listar_categorias`. `buscar_platos`: parte de `RestaurantRepository.findVisible()` y de los productos
  activos y disponibles de esos restaurantes (añadir en `ProductRepository` una consulta por lista de
  restaurantes si hace falta); coincidencia sin tildes ni mayúsculas con `Product.key` en nombre,
  descripción y categoría; `precio_maximo` inclusivo; `restaurante` por nombre parcial; orden por precio
  ascendente; `limite` por defecto 10 y máximo 10; argumentos inválidos (precio negativo o no numérico)
  se ignoran con un resultado vacío explicativo, sin excepciones. No incluye identificadores, imágenes ni
  datos de usuarios.
- [X] T016 [US2] Ampliar `AssistantService` con el bucle de herramientas: cuando el modelo pide una
  herramienta, la ejecuta con `AssistantCatalogTools`, devuelve el resultado al modelo y continúa; máximo 3
  vueltas, tras las cuales emite `ASSISTANT_UNAVAILABLE`; nombre de herramienta desconocido → resultado de
  error al modelo, sin excepción. Marcar los resultados como datos en el contenido enviado.
- [X] T017 [US2] Pruebas `BT/assistant/AssistantCatalogToolsTest` (Mockito sobre repositorios): excluye
  platos retirados, no disponibles o de restaurantes no visibles; respeta `precio_maximo`, categoría, texto y
  orden por precio; máximo 10; vegetariano solo por texto; descripción recortada a 200; argumentos inválidos
  no fallan; y `AssistantServiceTest` del bucle (modelo simulado: pide herramienta → recibe resultado →
  emite texto; 3 vueltas máximo; herramienta desconocida).

**Checkpoint**: recomendaciones con datos reales.

## Phase 5: User Story 3 - Restaurantes abiertos (Priority: P2)

**Goal**: listar solo restaurantes que pueden recibir pedidos ahora.

**Independent Test**: con un restaurante abierto y otro fuera de horario, `restaurantes_abiertos` devuelve
solo el abierto.

- [X] T018 [US3] Crear `BE/assistant/dto/OpenRestaurant.java` (`name`, `category`, `zone`,
  `todaySchedule`) y añadir `restaurantes_abiertos` a `AssistantCatalogTools`: parte de `findVisible()`,
  conserva solo `OrderEligibility.attentionStatus(r) == AttentionStatus.OPEN`, filtro opcional por categoría
  (sin tildes ni mayúsculas), máximo 10, con el horario del día según la zona horaria de la plataforma. Si no
  hay ninguno devuelve una lista vacía.
- [X] T019 [US3] Pruebas en `AssistantCatalogToolsTest`: abierto vs. fuera de horario vs. pausado; ninguno
  abierto → lista vacía; filtro por categoría; coincide con `OrderEligibility` (SC-003).

**Checkpoint**: preguntas de "qué está abierto" correctas.

## Phase 6: User Story 4 - Límite y degradación elegante (Priority: P2)

**Goal**: proteger la cuota gratuita y mostrar avisos amables.

**Independent Test**: 11 mensajes en un minuto → el 11.º da 429; el proveedor devuelve 429 → mensaje
«ocupado» sin errores técnicos.

- [X] T020 [US4] Crear `BE/assistant/service/AssistantUsageLimiter.java` (`@Component`): ventana deslizante
  de 60 s por `accountId` en `ConcurrentHashMap` con el `Clock` del proyecto, tope
  `AssistantProperties.rateLimitPerMinute`; `acquire(accountId)` lanza `AssistantRateLimitedException` con los
  segundos hasta poder volver a escribir; purga entradas vacías. Invocarlo en `AssistantService.chat` **antes**
  de llamar al proveedor y después de validar (un mensaje inválido no consume cupo).
- [X] T021 [P] [US4] En `FE/assistant/AssistantWidget.tsx` mostrar mensajes amables y reintento según el error:
  429 («Escribes muy rápido. Intenta de nuevo en N segundos.»), `ASSISTANT_BUSY`, `ASSISTANT_UNAVAILABLE`,
  red caída y sesión vencida (invita a iniciar sesión de nuevo); el texto ya recibido se conserva; el botón
  enviar se deshabilita mientras se responde y se puede cancelar la respuesta en curso (`AbortController`).
- [X] T022 [US4] Pruebas `BT/assistant/AssistantUsageLimiterTest` (`Clock` simulado: 10 aceptados, el 11.º
  rechazado con `retryAfterSeconds` correcto, tras la ventana se acepta, cuentas independientes, purga) y,
  en `AssistantControllerTest`, el 429 con cabecera `Retry-After`, `ASSISTANT_BUSY` y
  `ASSISTANT_UNAVAILABLE` como eventos `error` con el flujo abierto y que no contienen detalles técnicos.

**Checkpoint**: el chat resiste abuso y fallos del proveedor.

## Phase 7: Polish & Cross-Cutting

- [X] T023 Prueba de privacidad en `BT/assistant/AssistantPrivacyTest`: lo que `AssistantService` entrega al
  modelo no contiene el `accountId`, el correo ni ningún dato de pedidos o direcciones; las vistas
  `DishSuggestion` y `OpenRestaurant` no exponen identificadores ni datos de propietarios.
- [X] T024 Ejecutar `cd backend && ./mvnw verify` y comprobar JaCoCo ≥ 90 % de líneas de negocio del módulo
  `assistant`, y `cd frontend && pnpm test && pnpm lint && pnpm build`; corregir lo que falle.
- [X] T025 Verificar `quickstart.md` en lo que no requiera clave real; si el usuario provee
  `GEMINI_API_KEY`, ejecutar los pasos manuales. Anotar en el cierre qué quedó sin verificar.
- [X] T026 Actualizar `specs/013-asistente-ia-chat/checklists/requirements.md` sin cambios de fondo y
  registrar en el informe final: RNF-11 con cifra real, y pendientes (prueba con Gemini real, topes del plan
  gratuito, etiqueta «vegetariano» como posible incremento).

## Dependencies & Execution Order

- Phase 1 → Phase 2 → US1 (MVP) → US2 → US3 → US4 → Polish.
- US2, US3 y US4 dependen de `AssistantService` (T008) y del contrato de eventos de US1; US3 amplía
  `AssistantCatalogTools` de US2.
- Dentro de US1, el backend (T007–T009) y el frontend (T010–T013) pueden avanzar en paralelo.

## Parallel Opportunities

- T002 con T001; T003, T004, T005 entre sí.
- T010 y T011 (frontend) en paralelo con T007–T009 (backend).
- T021 en paralelo con T020.

## Implementation Strategy

1. **MVP**: Setup + Foundational + US1 (chat general con streaming). Ya cumple lo que pidió el docente.
2. Añadir US2 (recomendaciones con datos reales), que es lo que más luce en la demostración.
3. Añadir US3 y US4, y cerrar con Polish.

## Cierre (2026-11-24)

- **T024**: `./mvnw verify` OK: 785 pruebas, 0 fallos. JaCoCo: módulo `assistant` 95,8 % de líneas
  (294/307); total del backend 94,4 %. Frontend: 146 pruebas, lint y build OK.
- **T025**: verificado solo lo que no requiere clave (pruebas automáticas del quickstart). **Sin verificar**:
  los pasos manuales con Gemini real (requieren `GEMINI_API_KEY`), la interfaz en navegador y los topes
  vigentes del plan gratuito.
- **T026 / pendientes**: confirmar el nombre vigente del modelo (`GEMINI_MODEL`), probar con una clave real y,
  como posible incremento, una etiqueta «vegetariano» en los platos. RNF-06/RNF-08 sin cambios.
- T023 se cubrió con `elProveedorNuncaRecibeDatosQueIdentifiquenAlCliente` en `AssistantServiceTest` y la
  forma de los DTO (`DishSuggestion`, `OpenRestaurant`), que no contienen identificadores.
