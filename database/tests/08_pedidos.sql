-- Ejecutar con psql -v ON_ERROR_STOP=1 sobre una base temporal inicializada con 01..06.
-- No requiere 07. Nunca ejecutar sobre una base de usuario.
\ir ../init/08_pedidos.sql
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM ventas WHERE estado <> CASE WHEN anulada THEN 'CANCELADO' ELSE 'ENTREGADO' END) THEN
        RAISE EXCEPTION 'Migración histórica incorrecta';
    END IF;
    IF EXISTS (SELECT 1 FROM ventas WHERE estado='ENTREGADO' AND numero_factura IS NULL) THEN
        RAISE EXCEPTION 'Historia entregada sin factura';
    END IF;
END $$;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM detalle_venta WHERE cantidad_solicitada<>cantidad) THEN
        RAISE EXCEPTION 'La historia no conserva cantidad solicitada igual a entregada';
    END IF;
END $$;
CREATE TEMP TABLE detalle_antes AS SELECT id,cantidad,cantidad_solicitada FROM detalle_venta;
CREATE TEMP TABLE antes AS SELECT id,fecha,numero_orden,numero_factura,estado FROM ventas;
CREATE TEMP TABLE secuencias_antes AS SELECT
    (SELECT last_value FROM numero_orden_seq) orden,
    (SELECT last_value FROM numero_factura_seq) factura;
INSERT INTO ventas(usuario_id,cliente_id,fecha,total,subtotal,numero_orden,estado)
SELECT u.id,c.id,'2026-10-31 12:30:00',10,10,'OC-2026-TEST-IDEMPOTENCIA','PENDIENTE'
FROM usuarios u CROSS JOIN clientes c ORDER BY u.id,c.id LIMIT 1;
INSERT INTO detalle_venta(venta_id,producto_id,cantidad,cantidad_solicitada,precio_unitario)
SELECT v.id,p.id,0,2,p.precio FROM ventas v CROSS JOIN productos p
WHERE v.numero_orden='OC-2026-TEST-IDEMPOTENCIA' ORDER BY p.id LIMIT 1;
\ir ../init/08_pedidos.sql
DO $$
BEGIN
    IF EXISTS (SELECT * FROM detalle_antes EXCEPT SELECT id,cantidad,cantidad_solicitada FROM detalle_venta) THEN
        RAISE EXCEPTION 'La segunda ejecución altera cantidades históricas';
    END IF;
    IF EXISTS (SELECT 1 FROM detalle_venta d JOIN ventas v ON v.id=d.venta_id
               WHERE v.numero_orden='OC-2026-TEST-IDEMPOTENCIA' AND (d.cantidad<>0 OR d.cantidad_solicitada<>2)) THEN
        RAISE EXCEPTION 'La segunda ejecución altera cantidades de un pendiente';
    END IF;
    IF EXISTS (SELECT * FROM antes EXCEPT SELECT id,fecha,numero_orden,numero_factura,estado FROM ventas) THEN
        RAISE EXCEPTION 'La segunda ejecución altera la historia';
    END IF;
    IF EXISTS (SELECT 1 FROM ventas WHERE numero_orden='OC-2026-TEST-IDEMPOTENCIA'
               AND (estado<>'PENDIENTE' OR numero_factura IS NOT NULL OR estado_entrega<>'SIN_PACTAR' OR subtotal<>0 OR total<>0)) THEN
        RAISE EXCEPTION 'La segunda ejecución altera un pedido nuevo';
    END IF;
    IF EXISTS (SELECT 1 FROM secuencias_antes WHERE orden<>(SELECT last_value FROM numero_orden_seq)
                OR factura<>(SELECT last_value FROM numero_factura_seq)) THEN
        RAISE EXCEPTION 'La segunda ejecución consume numeración';
    END IF;
END $$;
DELETE FROM ventas WHERE numero_orden='OC-2026-TEST-IDEMPOTENCIA';
