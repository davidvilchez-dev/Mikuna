-- Semilla de las pruebas E2E de flujos. SOLO para la base dedicada mikuna_e2e_db: la vacía y carga lo mínimo que
-- no se puede crear desde la interfaz pública (un administrador y un restaurante ya aprobado con su carta). Cada
-- prueba crea por API sus propios clientes y pedidos. Requiere que Flyway haya creado el esquema (el backend de
-- las E2E arranca contra esta base antes de ejecutar la semilla).
--
-- Contraseña de las cuentas sembradas: MikunaE2e2026!  (hash PBKDF2 del codificador de la aplicación)

\set ON_ERROR_STOP on
-- El archivo está en UTF-8; sin esto, psql en Windows lo lee con la página de códigos local (WIN1252).
SET client_encoding = 'UTF8';

DO $$
BEGIN
    IF current_database() <> 'mikuna_e2e_db' THEN
        RAISE EXCEPTION 'La semilla E2E solo se ejecuta en mikuna_e2e_db (base actual: %)', current_database();
    END IF;
END $$;

BEGIN;

DO $$
DECLARE t text;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history' LOOP
        EXECUTE format('TRUNCATE TABLE %I CASCADE', t);
    END LOOP;
END $$;

INSERT INTO cuentas (operation_enabled, created_at, id, role, status, name, email, password_hash) VALUES
    (true, now(), md5('e2e-admin')::uuid, 'ADMIN', 'ENABLED', 'Admin E2E', 'admin@e2e.test',
     '{pbkdf2@SpringSecurity_v5_8}dd07c7367921837cfc35a42e0832f260cadf9b2b58e489e28d67aeb31384fafc8a9bf60bfeacb3b71a1ec863b1f91951'),
    (true, now(), md5('e2e-titular')::uuid, 'RESTAURANT', 'ENABLED', 'Titular E2E', 'restaurante@e2e.test',
     '{pbkdf2@SpringSecurity_v5_8}dd07c7367921837cfc35a42e0832f260cadf9b2b58e489e28d67aeb31384fafc8a9bf60bfeacb3b71a1ec863b1f91951');

INSERT INTO restaurantes (available, latitude, longitude, resolved_at, submitted_at, version, id, owner_id, phone,
                          status, category, name, zone, address, description)
VALUES (true, -13.158, -74.223, now(), now() - interval '1 day', 0, md5('e2e-restaurante')::uuid,
        md5('e2e-titular')::uuid, '966000111', 'APPROVED', 'Criolla', 'Picantería E2E', 'Ayacucho', 'Jr. Lima 123',
        'Restaurante de las pruebas E2E');

-- Abierto todo el día, todos los días, para que el flujo de pedido no dependa de la hora.
INSERT INTO horarios_restaurante (closes_at, opens_at, day_of_week, restaurante_id)
SELECT '23:59', '00:00', dia, md5('e2e-restaurante')::uuid
FROM unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']) AS dia;

INSERT INTO productos (active, available, price, created_at, updated_at, version, id, restaurant_id, category,
                       category_key, name, name_key, image_public_id, description, image_url) VALUES
    (true, true, 22.50, now(), now(), 0, md5('e2e-lomo')::uuid, md5('e2e-restaurante')::uuid, 'Fondos', 'fondos',
     'Lomo saltado', 'lomo saltado', 'mikuna/e2e/lomo', 'Con papas fritas y arroz',
     'https://res.cloudinary.com/demo/image/upload/sample.jpg'),
    (true, true, 25.00, now(), now(), 0, md5('e2e-cebiche')::uuid, md5('e2e-restaurante')::uuid, 'Entradas',
     'entradas', 'Cebiche', 'cebiche', 'mikuna/e2e/cebiche', 'Pescado del día',
     'https://res.cloudinary.com/demo/image/upload/sample.jpg'),
    (true, true, 6.00, now(), now(), 0, md5('e2e-chicha')::uuid, md5('e2e-restaurante')::uuid, 'Bebidas',
     'bebidas', 'Chicha morada', 'chicha morada', 'mikuna/e2e/chicha', 'Vaso de 500 ml',
     'https://res.cloudinary.com/demo/image/upload/sample.jpg');

COMMIT;
