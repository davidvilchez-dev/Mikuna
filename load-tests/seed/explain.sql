-- Planes de ejecución de las consultas de mayor tráfico (spec 015, FR-012) sobre mikuna_load_db con la semilla.
-- Uso: psql -U postgres -d mikuna_load_db -f load-tests/seed/explain.sql > load-tests/reports/explain-<antes|despues>.txt
-- Las consultas reproducen las de la aplicación (JPA) con parámetros reales de la semilla.

\set ON_ERROR_STOP on
\pset pager off
ANALYZE;

\echo '== 1. Restaurantes visibles (findVisible, se ejecuta al llenar la instantánea)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT DISTINCT r.*, h.*
FROM restaurantes r LEFT JOIN horarios_restaurante h ON h.restaurante_id = r.id
WHERE r.status = 'APPROVED'
  AND EXISTS (SELECT 1 FROM cuentas a WHERE a.id = r.owner_id AND a.status = 'ENABLED' AND a.operation_enabled = true);

\echo '== 2. Portada de respaldo de todos los restaurantes (una sola consulta, DISTINCT ON)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT DISTINCT ON (restaurant_id) restaurant_id, image_url
FROM productos
WHERE restaurant_id IN (SELECT id FROM restaurantes) AND active = true AND available = true
ORDER BY restaurant_id, category, name;

\echo '== 3. Carta pública de un restaurante (activos y disponibles, en orden de carta)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT * FROM productos
WHERE restaurant_id = md5('carga-restaurante-100')::uuid AND active = true AND available = true
ORDER BY category, name;

\echo '== 4. Inicio de sesión: cuenta por correo'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT * FROM cuentas WHERE email = 'carga150@mikuna.test';

\echo '== 5. Pedidos del cliente (historial, más recientes primero)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT * FROM pedidos WHERE customer_id = md5('carga-cliente-150')::uuid ORDER BY created_at DESC LIMIT 20;

\echo '== 6. Pedidos del restaurante por estado (panel)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT * FROM pedidos WHERE restaurant_id = md5('carga-restaurante-100')::uuid AND status = 'PENDING'
ORDER BY created_at DESC LIMIT 20;

\echo '== Índices de productos'
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'productos' ORDER BY indexname;
