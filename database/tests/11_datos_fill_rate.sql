-- Base temporal inicializada con01..06 y08; puede incluir07/10.
\ir ../init/11_datos_fill_rate.sql
DO $$
DECLARE
    filas INTEGER;
BEGIN
    SELECT count(*) INTO filas FROM (
        SELECT c.nombre,100.0*sum(d.cantidad)/sum(d.cantidad_solicitada) tasa
        FROM ventas v JOIN detalle_venta d ON d.venta_id=v.id JOIN clientes c ON c.id=v.cliente_id
        WHERE c.nombre IN ('Cadena demo Verde','Cadena demo Amarilla','Cadena demo Roja') AND v.estado='ENTREGADO'
        GROUP BY c.nombre
        HAVING 100.0*sum(d.cantidad)/sum(d.cantidad_solicitada)=CASE c.nombre
            WHEN 'Cadena demo Verde' THEN 99 WHEN 'Cadena demo Amarilla' THEN 96 ELSE 90 END
    ) tasas;
    IF filas<>3 THEN RAISE EXCEPTION 'Las tres tasas demo no son99/96/90'; END IF;
    IF EXISTS (SELECT 1 FROM productos p WHERE p.sku LIKE 'DEMO-FR-%' AND
               (p.stock_actual<>0 OR (SELECT sum(m.cantidad) FROM movimientos_stock m WHERE m.producto_id=p.id)<>0)) THEN
        RAISE EXCEPTION 'Stock demo o movimientos no compensados';
    END IF;
END $$;
CREATE TEMP TABLE demo_antes AS
    SELECT v.id,v.numero_orden,v.numero_factura,v.fecha,v.total,d.cantidad,d.cantidad_solicitada
    FROM ventas v JOIN detalle_venta d ON d.venta_id=v.id JOIN productos p ON p.id=d.producto_id
    WHERE p.sku LIKE 'DEMO-FR-%';
CREATE TEMP TABLE secuencias_demo_antes AS SELECT
    (SELECT last_value FROM numero_orden_seq) orden,(SELECT last_value FROM numero_factura_seq) factura;
\ir ../init/11_datos_fill_rate.sql
DO $$
BEGIN
    IF (SELECT count(*) FROM demo_antes)<>3 OR
       (SELECT count(*) FROM ventas v JOIN detalle_venta d ON d.venta_id=v.id
        JOIN productos p ON p.id=d.producto_id WHERE p.sku LIKE 'DEMO-FR-%')<>3 THEN
        RAISE EXCEPTION 'Faltan o se duplicaron pedidos demo';
    END IF;
    IF EXISTS (SELECT * FROM demo_antes EXCEPT
               SELECT v.id,v.numero_orden,v.numero_factura,v.fecha,v.total,d.cantidad,d.cantidad_solicitada
               FROM ventas v JOIN detalle_venta d ON d.venta_id=v.id JOIN productos p ON p.id=d.producto_id
               WHERE p.sku LIKE 'DEMO-FR-%') THEN
        RAISE EXCEPTION 'La segunda ejecución altera la historia demo';
    END IF;
    IF EXISTS (SELECT 1 FROM secuencias_demo_antes WHERE orden<>(SELECT last_value FROM numero_orden_seq)
                OR factura<>(SELECT last_value FROM numero_factura_seq)) THEN
        RAISE EXCEPTION 'La segunda ejecución consume órdenes o facturas';
    END IF;
END $$;
