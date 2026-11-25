-- Esquema inicial de Mikuna (specs 001 a 013), tal como lo dejaba Hibernate con ddl-auto=update.
-- Las bases ya existentes se marcan en esta versión sin ejecutarla (spring.flyway.baseline-on-migrate);
-- en una base vacía crea todo desde cero. Desde aquí, cada cambio del esquema es una migración nueva
-- (V2__..., V3__...) y Hibernate solo valida que las entidades coincidan (ddl-auto=validate).
--
-- Sin CHECK en las columnas de enums: los valores se validan al mapear el enum y un CHECK obligaría a una
-- migración por cada valor nuevo (antes los borraba EnumCheckConstraintCleaner en cada arranque).

    create table acciones_admin_cuenta (
        created_at timestamp(6) with time zone not null,
        account_id uuid not null,
        action varchar(16) not null,
        admin_id uuid not null,
        id uuid not null,
        scope varchar(16) not null,
        primary key (id)
    );

    create table avisos (
        created_at timestamp(6) with time zone not null,
        read_at timestamp(6) with time zone,
        id uuid not null,
        order_id uuid not null,
        recipient_id uuid not null,
        type varchar(16) not null,
        source_key varchar(80) not null,
        message varchar(200) not null,
        primary key (id),
        constraint uk_avisos_origen_destinatario unique (source_key, recipient_id)
    );

    create table calificaciones (
        value integer not null,
        created_at timestamp(6) with time zone not null,
        customer_id uuid not null,
        id uuid not null,
        order_id uuid not null,
        primary key (id),
        constraint uk_calificaciones_pedido unique (order_id),
        check (value between 1 and 5)
    );

    create table cambios_estado_pedido (
        changed_at timestamp(6) with time zone not null,
        actor_id uuid,
        actor_role varchar(16) not null,
        from_status varchar(16) not null,
        id uuid not null,
        order_id uuid not null,
        to_status varchar(16) not null,
        reason varchar(200),
        primary key (id)
    );

    create table carritos (
        created_at timestamp(6) with time zone not null,
        updated_at timestamp(6) with time zone not null,
        customer_id uuid not null,
        id uuid not null,
        restaurant_id uuid,
        selected_address_id uuid,
        primary key (id),
        constraint uk_carritos_cliente unique (customer_id)
    );

    create table cuentas (
        operation_enabled boolean not null default true,
        created_at timestamp(6) with time zone not null,
        id uuid not null,
        role varchar(20) not null,
        status varchar(20) not null,
        name varchar(100) not null,
        email varchar(254) not null,
        password_hash varchar(255) not null,
        primary key (id),
        constraint uk_cuentas_email unique (email)
    );

    create table direcciones (
        latitude float(53),
        longitude float(53),
        created_at timestamp(6) with time zone not null,
        updated_at timestamp(6) with time zone not null,
        customer_id uuid not null,
        id uuid not null,
        contact_phone varchar(20),
        label varchar(50),
        district varchar(100) not null,
        street varchar(150) not null,
        reference varchar(200),
        primary key (id)
    );

    create table entregas (
        assigned_at timestamp(6) with time zone,
        ready_at timestamp(6) with time zone not null,
        courier_id uuid,
        id uuid not null,
        order_id uuid not null,
        primary key (id),
        constraint uk_entregas_pedido unique (order_id)
    );

    create table hitos_entrega (
        latitude float(53) not null,
        longitude float(53) not null,
        created_at timestamp(6) with time zone not null,
        recorded_at timestamp(6) with time zone not null,
        id uuid not null,
        order_id uuid not null,
        type varchar(16) not null,
        primary key (id),
        constraint uk_hitos_pedido_tipo unique (order_id, type)
    );

    create table horarios_restaurante (
        closes_at time(0),
        opens_at time(0),
        day_of_week varchar(10) not null,
        restaurante_id uuid not null,
        constraint uk_horarios_dia unique (restaurante_id, day_of_week)
    );

    create table lineas_carrito (
        quantity integer not null,
        added_at timestamp(6) with time zone not null,
        cart_id uuid not null,
        id uuid not null,
        product_id uuid not null,
        primary key (id),
        constraint uk_lineas_carrito_producto unique (cart_id, product_id)
    );

    create table lineas_pedido (
        line_total numeric(10,2) not null,
        quantity integer not null,
        unit_price numeric(10,2) not null,
        id uuid not null,
        order_id uuid not null,
        product_id uuid not null,
        product_name varchar(255) not null,
        primary key (id)
    );

    create table pagos (
        amount numeric(10,2) not null,
        currency varchar(3) not null,
        created_at timestamp(6) with time zone not null,
        updated_at timestamp(6) with time zone not null,
        customer_id uuid not null,
        id uuid not null,
        order_id uuid not null,
        status varchar(16) not null,
        gateway_ref varchar(255) not null,
        primary key (id),
        constraint uk_pagos_pedido unique (order_id)
    );

    create table pedidos (
        commission numeric(10,2) not null,
        delivery_fee numeric(10,2) not null,
        delivery_latitude float(53),
        delivery_longitude float(53),
        subtotal numeric(10,2) not null,
        total numeric(10,2) not null,
        created_at timestamp(6) with time zone not null,
        expires_at timestamp(6) with time zone,
        scheduled_for timestamp(6) with time zone,
        customer_id uuid not null,
        id uuid not null,
        payment_method varchar(16) not null,
        restaurant_id uuid not null,
        status varchar(16) not null,
        receiver_phone varchar(20),
        confirmation_key varchar(64) not null,
        receiver_name varchar(80),
        receiver_note varchar(300),
        store_note varchar(300),
        delivery_district varchar(255) not null,
        delivery_label varchar(255),
        delivery_phone varchar(255),
        delivery_reference varchar(255),
        delivery_street varchar(255) not null,
        restaurant_name varchar(255) not null,
        primary key (id),
        constraint uk_pedidos_cliente_clave unique (customer_id, confirmation_key)
    );

    create table productos (
        active boolean,
        available boolean not null,
        price numeric(8,2) not null,
        created_at timestamp(6) with time zone not null,
        updated_at timestamp(6) with time zone not null,
        version bigint not null,
        withdrawn_at timestamp(6) with time zone,
        id uuid not null,
        restaurant_id uuid not null,
        category varchar(50) not null,
        category_key varchar(50) not null,
        name varchar(100) not null,
        name_key varchar(100) not null,
        image_public_id varchar(200) not null,
        description varchar(300),
        image_url varchar(500) not null,
        primary key (id),
        constraint uk_productos_activo unique (restaurant_id, name_key, category_key, active)
    );

    create table rechazos_entrega (
        rejected_at timestamp(6) with time zone not null,
        courier_id uuid not null,
        delivery_id uuid not null,
        id uuid not null,
        primary key (id),
        constraint uk_rechazos_entrega_repartidor unique (delivery_id, courier_id)
    );

    create table repartidores_disponibilidad (
        available boolean not null,
        free_since timestamp(6) with time zone not null,
        updated_at timestamp(6) with time zone not null,
        courier_id uuid not null,
        primary key (courier_id)
    );

    create table restaurantes (
        available boolean not null,
        latitude float(53),
        longitude float(53),
        resolved_at timestamp(6) with time zone,
        submitted_at timestamp(6) with time zone not null,
        version bigint not null,
        id uuid not null,
        owner_id uuid not null,
        resolved_by uuid,
        phone varchar(20) not null,
        status varchar(20) not null,
        category varchar(50) not null,
        name varchar(100) not null,
        zone varchar(100) not null,
        address varchar(200) not null,
        cover_public_id varchar(200),
        logo_public_id varchar(200),
        description varchar(300),
        rejection_reason varchar(300),
        cover_url varchar(500),
        logo_url varchar(500),
        primary key (id)
    );

    create table transacciones_pago (
        amount numeric(10,2) not null,
        created_at timestamp(6) with time zone not null,
        customer_id uuid not null,
        id uuid not null,
        order_id uuid,
        status varchar(16) not null,
        type varchar(16) not null,
        confirmation_key varchar(64),
        gateway_ref varchar(255),
        result_code varchar(255),
        result_message varchar(255),
        primary key (id)
    );

    create table ubicaciones_repartidor (
        latitude float(53) not null,
        longitude float(53) not null,
        recorded_at timestamp(6) with time zone not null,
        courier_id uuid not null,
        order_id uuid not null,
        primary key (courier_id)
    );

    create index ix_acciones_cuenta_fecha
       on acciones_admin_cuenta (account_id, created_at);

    create index ix_avisos_destinatario_fecha
       on avisos (recipient_id, created_at desc);

    create index ix_avisos_destinatario_leido
       on avisos (recipient_id, read_at);

    create index ix_cambios_pedido_fecha
       on cambios_estado_pedido (order_id, changed_at);

    create index idx_direcciones_cliente
       on direcciones (customer_id);

    create index ix_entregas_repartidor
       on entregas (courier_id);

    create index ix_entregas_lista
       on entregas (ready_at);

    create index ix_pedidos_cliente_fecha
       on pedidos (customer_id, created_at);

    create index ix_pedidos_restaurante_fecha
       on pedidos (restaurant_id, created_at);

    create index ix_pedidos_estado_fecha
       on pedidos (status, created_at);

    create index ix_pedidos_estado_vence
       on pedidos (status, expires_at);

    create index idx_productos_restaurante
       on productos (restaurant_id, active, available);

    create index idx_restaurantes_owner
       on restaurantes (owner_id);

    create index ix_transacciones_pedido
       on transacciones_pago (order_id);

    create index ix_transacciones_clave
       on transacciones_pago (customer_id, confirmation_key);

    alter table if exists horarios_restaurante
       add constraint FKgai7yqnkatofge42jn009lw5s
       foreign key (restaurante_id)
       references restaurantes;

    alter table if exists lineas_carrito
       add constraint FK23bokmt1rlgppjisqpo0hjsxe
       foreign key (cart_id)
       references carritos;

    alter table if exists lineas_pedido
       add constraint FKibt7fx88j4ql1bam0p5ormqdg
       foreign key (order_id)
       references pedidos;
