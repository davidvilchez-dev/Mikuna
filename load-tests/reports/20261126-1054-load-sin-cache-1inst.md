# Informe de carga: load (sin-cache, 1 instancia)

- Commit: `3aa13ba-wip`  ·  Fecha: 2026-11-26T15:54:26.762Z
- URLs: http://localhost:8080
- Datos: load-tests/seed/seed.sql (200 restaurantes, 6000 productos, 300 clientes)
- Usuarios máximos: 200  ·  Duración: 483 s

| Flujo | p50 | p95 | p99 | Errores | Umbrales |
|-------|-----|-----|-----|---------|----------|
| catalogo | 2443 ms | 6641 ms | 8362 ms | 9.48 % | ❌ |
| login | 3360 ms | 5824 ms | 6091 ms | 8.92 % | ❌ |
| pedido | 3895 ms | 9299 ms | 14419 ms | 13.42 % | ❌ |

Aciertos de caché: no medidos (sin credenciales de administrador o caché apagada)

Resultado: NO cumple algún umbral
