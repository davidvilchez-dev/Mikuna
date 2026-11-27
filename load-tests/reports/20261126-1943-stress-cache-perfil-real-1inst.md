# Informe de carga: stress (cache-perfil-real, 1 instancia)

- Commit: `3aa13ba-wip`  ·  Fecha: 2026-11-27T00:43:09.822Z
- URLs: http://localhost:8080
- Datos: load-tests/seed/seed.sql (200 restaurantes, 6000 productos, 300 clientes)
- Usuarios máximos: 679  ·  Duración: 785 s

| Flujo | p50 | p95 | p99 | Errores | Umbrales |
|-------|-----|-----|-----|---------|----------|
| catalogo | 97 ms | 3201 ms | 4633 ms | 0.02 % | ❌ |
| login | 6495 ms | 14926 ms | 21184 ms | 5.90 % | ❌ |
| pedido | 267 ms | 7616 ms | 10290 ms | 16.44 % | ❌ |

Aciertos de caché: 99.75 % (195986 aciertos, 494 fallos)

Resultado: NO cumple algún umbral

## Escalones

| Usuarios | p95 | Errores | Cumple |
|----------|-----|---------|--------|
| 100 | 84 ms | 0.00 % | ✅ |
| 200 | 5142 ms | 3.25 % | ❌ |
| 300 | 5910 ms | 6.65 % | ❌ |
| 400 | 5594 ms | 7.49 % | ❌ |
| 500 | 5231 ms | 6.29 % | ❌ |
| 600 | 5498 ms | 6.68 % | ❌ |

Límite: los umbrales se incumplen desde 200 usuarios concurrentes.
