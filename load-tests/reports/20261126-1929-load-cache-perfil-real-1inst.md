# Informe de carga: load (cache-perfil-real, 1 instancia)

- Commit: `3aa13ba-wip`  ·  Fecha: 2026-11-27T00:29:32.768Z
- URLs: http://localhost:8080
- Datos: load-tests/seed/seed.sql (200 restaurantes, 6000 productos, 300 clientes)
- Usuarios máximos: 300  ·  Duración: 483 s

| Flujo | p50 | p95 | p99 | Errores | Umbrales |
|-------|-----|-----|-----|---------|----------|
| catalogo | 16 ms | 587 ms | 1477 ms | 0.00 % | ✅ |
| login | 6052 ms | 32138 ms | 44441 ms | 4.27 % | ❌ |
| pedido | 157 ms | 5505 ms | 10011 ms | 10.67 % | ❌ |

Aciertos de caché: 99.87 % (96541 aciertos, 127 fallos)

Resultado: NO cumple algún umbral
