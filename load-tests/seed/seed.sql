-- Datos de volumen para las pruebas de carga (spec 015, FR-018). SOLO para la base dedicada mikuna_load_db:
-- vacía todas sus tablas y vuelve a cargar datos ficticios. Requiere que Flyway haya creado el esquema (arrancar
-- una vez el backend contra mikuna_load_db). Uso:
--   psql -U postgres -d mikuna_load_db -f load-tests/seed/seed.sql
--
-- Contraseña de todas las cuentas: CargaMikuna2026!  (hash PBKDF2 del codificador de la aplicación)
-- Clientes: carga1@mikuna.test .. carga300@mikuna.test   Administrador: admin-carga@mikuna.test

\set ON_ERROR_STOP on
-- El archivo está en UTF-8; sin esto, psql en Windows lo lee con la página de códigos local (WIN1252).
SET client_encoding = 'UTF8';

DO $$
BEGIN
    IF current_database() <> 'mikuna_load_db' THEN
        RAISE EXCEPTION 'La semilla de carga solo se ejecuta en mikuna_load_db (base actual: %)', current_database();
    END IF;
END $$;

BEGIN;

-- Base dedicada: se vacía entera salvo el historial de Flyway.
DO $$
DECLARE t text;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history' LOOP
        EXECUTE format('TRUNCATE TABLE %I CASCADE', t);
    END LOOP;
END $$;

CREATE TEMP TABLE carga_parametros AS
SELECT '{pbkdf2@SpringSecurity_v5_8}7097f211350c54d2f983126312e801ef774a25197d70daf68c4844f9c4c88e53222d965d795d4475b54155319f6fcd54'::text AS hash,
       ARRAY['Ayacucho', 'San Juan Bautista', 'Carmen Alto', 'Jesús Nazareno',
             'Andrés Avelino Cáceres Dorregaray']::text[] AS zonas,
       ARRAY['Criolla', 'Pollería', 'Pizzería', 'Chifa', 'Marina', 'Postres', 'Hamburguesas', 'Cafetería']::text[] AS categorias,
       ARRAY['Entradas', 'Fondos', 'Bebidas', 'Postres', 'Extras']::text[] AS secciones;

-- Administrador (lee las métricas de caché durante la corrida).
INSERT INTO cuentas (operation_enabled, created_at, id, role, status, name, email, password_hash)
SELECT true, now(), md5('carga-admin')::uuid, 'ADMIN', 'ENABLED', 'Administración de carga', 'admin-carga@mikuna.test', hash
FROM carga_parametros;

-- 200 titulares y 200 restaurantes aprobados, abiertos todo el día.
INSERT INTO cuentas (operation_enabled, created_at, id, role, status, name, email, password_hash)
SELECT true, now(), md5('carga-titular-' || n)::uuid, 'RESTAURANT', 'ENABLED', 'Titular ' || n,
       'titular' || n || '@mikuna.test', p.hash
FROM generate_series(1, 200) AS n, carga_parametros p;

INSERT INTO restaurantes (available, latitude, longitude, resolved_at, submitted_at, version, id, owner_id, phone,
                          status, category, name, zone, address, description)
SELECT true,
       -13.16 + (n % 20) * 0.002,
       -74.22 + (n / 20) * 0.002,
       now(),
       now() - make_interval(mins => n),
       0,
       md5('carga-restaurante-' || n)::uuid,
       md5('carga-titular-' || n)::uuid,
       '966000' || lpad(n::text, 3, '0'),
       'APPROVED',
       p.categorias[1 + n % array_length(p.categorias, 1)],
       'Carga Restaurante ' || n,
       p.zonas[1 + n % array_length(p.zonas, 1)],
       'Jr. Carga ' || n,
       'Restaurante de prueba de carga'
FROM generate_series(1, 200) AS n, carga_parametros p;

INSERT INTO horarios_restaurante (closes_at, opens_at, day_of_week, restaurante_id)
SELECT '23:59', '00:00', dia, md5('carga-restaurante-' || n)::uuid
FROM generate_series(1, 200) AS n,
     unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']) AS dia;

-- 30 productos activos y disponibles por restaurante.
INSERT INTO productos (active, available, price, created_at, updated_at, version, id, restaurant_id, category,
                       category_key, name, name_key, image_public_id, description, image_url)
SELECT true, true,
       (8 + (m * 7 + n) % 40)::numeric(8, 2),
       now(), now(), 0,
       md5('carga-producto-' || n || '-' || m)::uuid,
       md5('carga-restaurante-' || n)::uuid,
       p.secciones[1 + m % array_length(p.secciones, 1)],
       lower(p.secciones[1 + m % array_length(p.secciones, 1)]),
       'Plato ' || n || '-' || m,
       'plato ' || n || '-' || m,
       'mikuna/carga/' || n || '-' || m,
       'Plato de prueba de carga',
       'https://res.cloudinary.com/demo/image/upload/sample.jpg'
FROM generate_series(1, 200) AS n, generate_series(1, 30) AS m, carga_parametros p;

-- 300 clientes, cada uno con una dirección en un distrito cubierto.
INSERT INTO cuentas (operation_enabled, created_at, id, role, status, name, email, password_hash)
SELECT true, now(), md5('carga-cliente-' || n)::uuid, 'CUSTOMER', 'ENABLED', 'Cliente ' || n,
       'carga' || n || '@mikuna.test', p.hash
FROM generate_series(1, 300) AS n, carga_parametros p;

INSERT INTO direcciones (latitude, longitude, created_at, updated_at, customer_id, id, contact_phone, label, district,
                         street, reference)
SELECT -13.158, -74.223, now(), now(), md5('carga-cliente-' || n)::uuid, md5('carga-direccion-' || n)::uuid,
       '966111222', 'Casa', 'Ayacucho', 'Jr. Cliente ' || n, 'Prueba de carga'
FROM generate_series(1, 300) AS n;

COMMIT;

SELECT (SELECT count(*) FROM restaurantes) AS restaurantes,
       (SELECT count(*) FROM productos) AS productos,
       (SELECT count(*) FROM cuentas WHERE role = 'CUSTOMER') AS clientes,
       (SELECT count(*) FROM direcciones) AS direcciones;
