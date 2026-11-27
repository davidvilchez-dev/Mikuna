# Informe de carga: smoke (cache-perfil-real, 1 instancia)

- Commit: `3aa13ba-wip`  ·  Fecha: 2026-11-27T00:21:03.598Z
- URLs: http://localhost:8080
- Datos: load-tests/seed/seed.sql (200 restaurantes, 6000 productos, 300 clientes)
- Usuarios máximos: 4  ·  Duración: 33 s

| Flujo | p50 | p95 | p99 | Errores | Umbrales |
|-------|-----|-----|-----|---------|----------|
| catalogo | 5 ms | 15 ms | 199 ms | 0.00 % | ✅ |
| login | 834 ms | 1100 ms | 1291 ms | 0.00 % | ✅ |
| pedido | 16 ms | 71 ms | 908 ms | 0.00 % | ✅ |

Aciertos de caché: 81.12 % (159 aciertos, 37 fallos)

Resultado: cumple los umbrales (p95 < 2 s, errores < 1 %)
