-- SmartStock - Esquema inicial
-- Se ejecuta automáticamente la PRIMERA vez que se levanta el contenedor de PostgreSQL.
-- Para reiniciar la base: docker compose down -v && docker compose up

CREATE TABLE usuarios (
    id             BIGSERIAL PRIMARY KEY,
    nombre         VARCHAR(100) NOT NULL,
    email          VARCHAR(150) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    rol            VARCHAR(20)  NOT NULL CHECK (rol IN ('ADMIN', 'VENDEDOR')),
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
    costo          NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (costo >= 0),
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

-- Toda variación de stock queda registrada: ventas (negativas), compras/ingresos (positivas) y ajustes.
-- El microservicio de analítica la usa para reconstruir la evolución histórica del stock.
CREATE TABLE movimientos_stock (
    id           BIGSERIAL PRIMARY KEY,
    producto_id  BIGINT       NOT NULL REFERENCES productos(id),
    tipo         VARCHAR(20)  NOT NULL CHECK (tipo IN ('VENTA', 'COMPRA', 'AJUSTE')),
    cantidad     INTEGER      NOT NULL,
    fecha        TIMESTAMP    NOT NULL DEFAULT NOW(),
    usuario_id   BIGINT       REFERENCES usuarios(id),
    venta_id     BIGINT       REFERENCES ventas(id),
    observacion  VARCHAR(255)
);

-- Parámetros configurables por el Administrador
CREATE TABLE parametros (
    clave        VARCHAR(50)  PRIMARY KEY,
    valor        VARCHAR(100) NOT NULL,
    descripcion  VARCHAR(255)
);

CREATE INDEX idx_ventas_fecha ON ventas(fecha);
CREATE INDEX idx_detalle_producto ON detalle_venta(producto_id);
CREATE INDEX idx_movimientos_producto_fecha ON movimientos_stock(producto_id, fecha);
