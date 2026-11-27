# Informe de carga: load (cache-2niveles-2inst, 2 instancias)

- Commit: `3aa13ba-wip`  ·  Fecha: 2026-11-27T01:55:44.587Z
- URLs: http://localhost:8080, http://localhost:8081
- Datos: load-tests/seed/seed.sql (200 restaurantes, 6000 productos, 300 clientes)
- Usuarios máximos: 242  ·  Duración: 484 s

| Flujo | p50 | p95 | p99 | Errores | Umbrales |
|-------|-----|-----|-----|---------|----------|
| catalogo | 4 ms | 44 ms | 198 ms | 0.00 % | ✅ |
| login | 1670 ms | 7171 ms | 10716 ms | 0.00 % | ❌ |
| pedido | 11 ms | 37 ms | 150 ms | 0.00 % | ✅ |

Aciertos de caché: 99.16 % (259846 aciertos, 2198 fallos)

Resultado: NO cumple algún umbral
