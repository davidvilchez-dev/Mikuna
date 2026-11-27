# Informe de carga: load (cache-redis-2inst, 2 instancias)

- Commit: `3aa13ba-wip`  ·  Fecha: 2026-11-27T01:09:00.498Z
- URLs: http://localhost:8080, http://localhost:8081
- Datos: load-tests/seed/seed.sql (200 restaurantes, 6000 productos, 300 clientes)
- Usuarios máximos: 246  ·  Duración: 488 s

| Flujo | p50 | p95 | p99 | Errores | Umbrales |
|-------|-----|-----|-----|---------|----------|
| catalogo | 812 ms | 5372 ms | 6860 ms | 0.52 % | ❌ |
| login | 5340 ms | 9530 ms | 11233 ms | 0.34 % | ❌ |
| pedido | 597 ms | 6556 ms | 9358 ms | 0.61 % | ❌ |

Aciertos de caché: 88.45 % (360 aciertos, 47 fallos)

Resultado: NO cumple algún umbral
