# Informe de carga: load (cache-pool25, 1 instancia)

- Commit: `3aa13ba-wip`  ·  Fecha: 2026-11-27T00:59:49.193Z
- URLs: http://localhost:8080
- Datos: load-tests/seed/seed.sql (200 restaurantes, 6000 productos, 300 clientes)
- Usuarios máximos: 221  ·  Duración: 483 s

| Flujo | p50 | p95 | p99 | Errores | Umbrales |
|-------|-----|-----|-----|---------|----------|
| catalogo | 2 ms | 12 ms | 45 ms | 0.00 % | ✅ |
| login | 1097 ms | 2015 ms | 3421 ms | 0.00 % | ❌ |
| pedido | 8 ms | 22 ms | 59 ms | 0.00 % | ✅ |

Aciertos de caché: 99.95 % (272771 aciertos, 127 fallos)

Resultado: NO cumple algún umbral
