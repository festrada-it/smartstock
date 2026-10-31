-- SmartStock - Esquema inicial
-- Se ejecuta automáticamente la PRIMERA vez que se levanta el contenedor de PostgreSQL.
-- Para reiniciar la base: docker compose down -v && docker compose up

CREATE TABLE usuarios (
    id             BIGSERIAL PRIMARY KEY,
    nombre         VARCHAR(100) NOT NULL,
    email          VARCHAR(150) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    rol            VARCHAR(20)  NOT NULL CHECK (rol IN ('ADMIN', 'VENDEDOR', 'ANALISTA')),
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE categorias (
    id      BIGSERIAL PRIMARY KEY,
    nombre  VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE productos (
    id             BIGSERIAL PRIMARY KEY,
    sku            VARCHAR(50)    NOT NULL UNIQUE,
    nombre         VARCHAR(150)   NOT NULL,
    categoria_id   BIGINT         REFERENCES categorias(id),
    precio         NUMERIC(12, 2) NOT NULL CHECK (precio >= 0),
    stock_actual   INTEGER        NOT NULL DEFAULT 0 CHECK (stock_actual >= 0),
    stock_minimo   INTEGER        NOT NULL DEFAULT 0 CHECK (stock_minimo >= 0),
    activo         BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE TABLE ventas (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT         NOT NULL REFERENCES usuarios(id),
    fecha       TIMESTAMP      NOT NULL DEFAULT NOW(),
    total       NUMERIC(12, 2) NOT NULL CHECK (total >= 0)
);

CREATE TABLE detalle_venta (
    id               BIGSERIAL PRIMARY KEY,
    venta_id         BIGINT         NOT NULL REFERENCES ventas(id) ON DELETE CASCADE,
    producto_id      BIGINT         NOT NULL REFERENCES productos(id),
    cantidad         INTEGER        NOT NULL CHECK (cantidad > 0),
    precio_unitario  NUMERIC(12, 2) NOT NULL CHECK (precio_unitario >= 0)
);

CREATE INDEX idx_ventas_fecha ON ventas(fecha);
CREATE INDEX idx_detalle_producto ON detalle_venta(producto_id);
