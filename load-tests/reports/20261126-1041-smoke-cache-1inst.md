# Informe de carga: smoke (cache, 1 instancia)

- Commit: `3aa13ba-wip`  ·  Fecha: 2026-11-26T15:41:57.211Z
- URLs: http://localhost:8080
- Datos: load-tests/seed/seed.sql (200 restaurantes, 6000 productos, 300 clientes)
- Usuarios máximos: 2  ·  Duración: 33 s

| Flujo | p50 | p95 | p99 | Errores | Umbrales |
|-------|-----|-----|-----|---------|----------|
| catalogo | 4 ms | 10 ms | 22 ms | 0.00 % | ✅ |
| login | 734 ms | 756 ms | 757 ms | 0.00 % | ✅ |
| pedido | 14 ms | 87 ms | 733 ms | 0.00 % | ✅ |

Aciertos de caché: 81.98 % (141 aciertos, 31 fallos)

Resultado: cumple los umbrales (p95 < 2 s, errores < 1 %)
