# Informe de carga: stress (cache-pool25, 1 instancia)

- Commit: `3aa13ba-wip`  ·  Fecha: 2026-11-27T02:09:14.099Z
- URLs: http://localhost:8080
- Datos: load-tests/seed/seed.sql (200 restaurantes, 6000 productos, 300 clientes)
- Usuarios máximos: 700  ·  Duración: 783 s

| Flujo | p50 | p95 | p99 | Errores | Umbrales |
|-------|-----|-----|-----|---------|----------|
| catalogo | 114 ms | 554 ms | 1074 ms | 0.00 % | ✅ |
| login | 11735 ms | 28157 ms | 30065 ms | 0.06 % | ❌ |
| pedido | 101 ms | 542 ms | 1416 ms | 0.45 % | ✅ |

Aciertos de caché: 99.92 % (516477 aciertos, 389 fallos)

Resultado: NO cumple algún umbral

## Escalones

| Usuarios | p95 | Errores | Cumple |
|----------|-----|---------|--------|
| 100 | 18 ms | 0.00 % | ✅ |
| 200 | 42 ms | 0.00 % | ✅ |
| 300 | 339 ms | 0.00 % | ✅ |
| 400 | 667 ms | 0.08 % | ✅ |
| 500 | 556 ms | 0.31 % | ✅ |
| 600 | 739 ms | 0.28 % | ✅ |

Límite: no se alcanzó hasta 600 usuarios.
