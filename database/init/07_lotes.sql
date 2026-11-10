-- Apertura estimada de existencias: no reconstruye vencimientos históricos.
BEGIN;
CREATE TABLE IF NOT EXISTS lotes (
    id BIGSERIAL PRIMARY KEY,
    producto_id BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
    proveedor_id BIGINT REFERENCES proveedores(id),
    cantidad_inicial INTEGER NOT NULL CHECK (cantidad_inicial >= 0),
    cantidad_disponible INTEGER NOT NULL CHECK (cantidad_disponible >= 0),
    fecha_vencimiento DATE,
    fecha_entrega DATE NOT NULL,
    costo_unitario NUMERIC(12,2) NOT NULL CHECK (costo_unitario >= 0),
    creado_en TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'America/Argentina/Buenos_Aires'),
    es_apertura BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_lotes_apertura ON lotes(producto_id) WHERE es_apertura;
CREATE INDEX IF NOT EXISTS idx_lotes_producto_vencimiento ON lotes(producto_id,fecha_vencimiento,id);
CREATE TABLE IF NOT EXISTS consumos_lote (
    id BIGSERIAL PRIMARY KEY,
    detalle_venta_id BIGINT NOT NULL REFERENCES detalle_venta(id) ON DELETE CASCADE,
    lote_id BIGINT NOT NULL REFERENCES lotes(id),
    cantidad INTEGER NOT NULL CHECK (cantidad > 0)
);
CREATE INDEX IF NOT EXISTS idx_consumos_lote_detalle ON consumos_lote(detalle_venta_id);
CREATE INDEX IF NOT EXISTS idx_consumos_lote_lote ON consumos_lote(lote_id);
ALTER TABLE movimientos_stock ADD COLUMN IF NOT EXISTS lote_id BIGINT REFERENCES lotes(id);
CREATE INDEX IF NOT EXISTS idx_movimientos_lote ON movimientos_stock(lote_id);

-- Cada producto conserva exactamente su stock; los ejemplos alternan dentro/fuera
-- de la alerta del grupo. La fecha es estimada, nunca un dato histórico conocido.
INSERT INTO lotes(producto_id,proveedor_id,cantidad_inicial,cantidad_disponible,
                  fecha_vencimiento,fecha_entrega,costo_unitario,es_apertura)
SELECT p.id,p.proveedor_id,p.stock_actual,p.stock_actual,
       (CURRENT_TIMESTAMP AT TIME ZONE 'America/Argentina/Buenos_Aires')::DATE
           + CASE WHEN p.id % 2 = 0 THEN GREATEST(1,COALESCE(c.dias_alerta_vencimiento,60)/2)
                  ELSE COALESCE(c.dias_alerta_vencimiento,60)+30 END,
       (CURRENT_TIMESTAMP AT TIME ZONE 'America/Argentina/Buenos_Aires')::DATE,p.costo,TRUE
FROM productos p LEFT JOIN categorias c ON c.id=p.categoria_id
WHERE NOT EXISTS (SELECT 1 FROM lotes l WHERE l.producto_id=p.id)
ON CONFLICT (producto_id) WHERE es_apertura DO NOTHING;
COMMENT ON COLUMN lotes.es_apertura IS 'Existencias iniciales; el vencimiento migrado es una estimación según el grupo.';
COMMIT;
