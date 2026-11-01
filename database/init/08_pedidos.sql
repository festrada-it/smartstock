BEGIN;

CREATE SEQUENCE IF NOT EXISTS numero_orden_seq;
CREATE SEQUENCE IF NOT EXISTS numero_factura_seq;

ALTER TABLE detalle_venta ADD COLUMN IF NOT EXISTS cantidad_solicitada INTEGER;
UPDATE detalle_venta SET cantidad_solicitada=cantidad WHERE cantidad_solicitada IS NULL;
ALTER TABLE detalle_venta ALTER COLUMN cantidad_solicitada SET NOT NULL;
ALTER TABLE detalle_venta DROP CONSTRAINT IF EXISTS detalle_venta_cantidad_check;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='detalle_venta'::regclass AND conname='ck_detalle_entrega') THEN
        ALTER TABLE detalle_venta ADD CONSTRAINT ck_detalle_entrega CHECK (
            cantidad_solicitada > 0 AND cantidad >= 0 AND cantidad <= cantidad_solicitada
        );
    END IF;
END $$;

ALTER TABLE ventas ADD COLUMN IF NOT EXISTS numero_orden VARCHAR(40);
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS forma_pago VARCHAR(20) DEFAULT 'EFECTIVO';
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS descuento_pct NUMERIC DEFAULT 0;
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS subtotal NUMERIC(12,2);
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS fecha_envio DATE;
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS estado VARCHAR(20);
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS estado_entrega VARCHAR(20);
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS numero_factura VARCHAR(40);

-- Solo las filas aún sin estado son históricas. Una segunda ejecución no cambia pedidos nuevos.
UPDATE ventas SET estado = CASE WHEN anulada THEN 'CANCELADO' ELSE 'ENTREGADO' END
WHERE estado IS NULL;
UPDATE ventas SET subtotal = total WHERE subtotal IS NULL;
-- Actualiza también pedidos creados con la versión previa de08: aún no entregaron mercadería.
UPDATE detalle_venta d SET cantidad=0 FROM ventas v WHERE d.venta_id=v.id AND v.estado='PENDIENTE';
UPDATE ventas SET subtotal=0,total=0 WHERE estado='PENDIENTE';

DO $$
DECLARE
    venta RECORD;
    numero BIGINT;
BEGIN
    FOR venta IN SELECT id,fecha,estado FROM ventas WHERE numero_orden IS NULL ORDER BY id LOOP
        numero := nextval('numero_orden_seq');
        UPDATE ventas SET numero_orden = 'OC-' || to_char(venta.fecha,'YYYY') || '-' ||
            lpad(numero::TEXT,greatest(6,length(numero::TEXT)),'0') WHERE id=venta.id;
    END LOOP;
    FOR venta IN SELECT id FROM ventas WHERE estado='ENTREGADO' AND numero_factura IS NULL ORDER BY id LOOP
        numero := nextval('numero_factura_seq');
        UPDATE ventas SET numero_factura = 'FAC-0001-' ||
            lpad(numero::TEXT,greatest(8,length(numero::TEXT)),'0') WHERE id=venta.id;
    END LOOP;
END $$;

UPDATE ventas SET estado_entrega = CASE
    WHEN estado='ENTREGADO' THEN 'ENTREGADA'
    WHEN fecha_envio IS NOT NULL THEN 'PACTADA'
    ELSE 'SIN_PACTAR' END;
UPDATE ventas SET anulada = (estado='CANCELADO');
ALTER TABLE ventas ALTER COLUMN numero_orden SET NOT NULL;
ALTER TABLE ventas ALTER COLUMN forma_pago SET NOT NULL;
ALTER TABLE ventas ALTER COLUMN descuento_pct SET NOT NULL;
ALTER TABLE ventas ALTER COLUMN subtotal SET NOT NULL;
ALTER TABLE ventas ALTER COLUMN estado SET NOT NULL;
ALTER TABLE ventas ALTER COLUMN estado SET DEFAULT 'CONFIRMADO';
ALTER TABLE ventas ALTER COLUMN estado_entrega SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ventas_numero_orden ON ventas(numero_orden);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ventas_numero_factura ON ventas(numero_factura);
CREATE INDEX IF NOT EXISTS idx_ventas_estado_fecha ON ventas(estado,fecha DESC,id DESC);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='ventas'::regclass AND conname='ck_ventas_pedido') THEN
        ALTER TABLE ventas ADD CONSTRAINT ck_ventas_pedido CHECK (
            estado IN ('PENDIENTE','CONFIRMADO','ENVIADO','ENTREGADO','CANCELADO') AND
            estado_entrega IN ('SIN_PACTAR','PACTADA','ENTREGADA') AND
            forma_pago IN ('EFECTIVO','TRANSFERENCIA','TARJETA','CUENTA_CORRIENTE') AND
            descuento_pct BETWEEN 0 AND 20 AND subtotal >= 0 AND
            (fecha_envio IS NULL OR fecha_envio >= fecha::DATE)
        );
    END IF;
END $$;

-- Los consumidores SQL conservan los mismos invariantes que el modelo Java.
CREATE OR REPLACE FUNCTION sincronizar_estado_pedido() RETURNS TRIGGER AS $$
BEGIN
    NEW.anulada := NEW.estado='CANCELADO';
    NEW.estado_entrega := CASE
        WHEN NEW.estado='ENTREGADO' THEN 'ENTREGADA'
        WHEN NEW.fecha_envio IS NOT NULL THEN 'PACTADA'
        ELSE 'SIN_PACTAR' END;
    RETURN NEW;
END $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS ventas_estado_pedido ON ventas;
CREATE TRIGGER ventas_estado_pedido BEFORE INSERT OR UPDATE ON ventas
FOR EACH ROW EXECUTE FUNCTION sincronizar_estado_pedido();

COMMIT;
