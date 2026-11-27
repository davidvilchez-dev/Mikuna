# Informe de carga: load (cache-tras-arreglo-login, 1 instancia)

- Commit: `3aa13ba-wip`  ·  Fecha: 2026-11-26T16:13:43.687Z
- URLs: http://localhost:8080
- Datos: load-tests/seed/seed.sql (200 restaurantes, 6000 productos, 300 clientes)
- Usuarios máximos: 200  ·  Duración: 484 s

| Flujo | p50 | p95 | p99 | Errores | Umbrales |
|-------|-----|-----|-----|---------|----------|
| catalogo | 32 ms | 243 ms | 542 ms | 0.00 % | ✅ |
| login | 20559 ms | 28020 ms | 33412 ms | 0.00 % | ❌ |
| pedido | 30 ms | 288 ms | 2727 ms | 0.00 % | ✅ |

Aciertos de caché: 99.78 % (57809 aciertos, 127 fallos)

Resultado: NO cumple algún umbral
