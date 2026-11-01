-- Escenarios aislados para el dashboard: verde99%, amarillo96%, rojo90%.
-- Requiere08; no depende de07/10 ni modifica sus productos, grupos o clientes.
BEGIN;
SELECT pg_advisory_xact_lock(24,11);
DO $$
DECLARE
    escenario RECORD;
    usuario BIGINT;
    cliente BIGINT;
    producto BIGINT;
    categoria BIGINT;
    venta BIGINT;
    orden BIGINT;
    factura BIGINT;
    fecha_pedido TIMESTAMP := (CURRENT_TIMESTAMP AT TIME ZONE 'America/Argentina/Buenos_Aires') - INTERVAL '1 day';
BEGIN
    SELECT id INTO STRICT usuario FROM usuarios WHERE email='admin@smartstock.com' ORDER BY id LIMIT 1;
    INSERT INTO categorias(nombre)
        SELECT 'Demo Fill Rate' WHERE NOT EXISTS (SELECT 1 FROM categorias WHERE nombre='Demo Fill Rate')
        ON CONFLICT(nombre) DO NOTHING;
    SELECT id INTO STRICT categoria FROM categorias WHERE nombre='Demo Fill Rate';
    FOR escenario IN SELECT * FROM (VALUES
        ('DEMO-FR-VERDE','Cadena demo Verde',990,'BUENA',5),
        ('DEMO-FR-AMARILLO','Cadena demo Amarilla',960,'REGULAR',3),
        ('DEMO-FR-ROJO','Cadena demo Roja',900,'MALA',1)
    ) AS e(sku,nombre_cliente,entregada,calidad,clasificacion) LOOP
        INSERT INTO clientes(nombre,calidad,clasificacion)
            SELECT escenario.nombre_cliente,escenario.calidad,escenario.clasificacion
            WHERE NOT EXISTS (SELECT 1 FROM clientes WHERE nombre=escenario.nombre_cliente);
        SELECT id INTO STRICT cliente FROM clientes WHERE nombre=escenario.nombre_cliente ORDER BY id LIMIT 1;
        INSERT INTO productos(sku,nombre,marca,categoria_id,precio,costo,stock_actual,stock_minimo)
            SELECT escenario.sku,'Producto ' || escenario.nombre_cliente,'Demo Fill Rate',categoria,1.00,0.60,0,0
            WHERE NOT EXISTS (SELECT 1 FROM productos WHERE sku=escenario.sku)
            ON CONFLICT(sku) DO NOTHING;
        SELECT id INTO STRICT producto FROM productos WHERE sku=escenario.sku;
        IF NOT EXISTS (SELECT 1 FROM movimientos_stock
                       WHERE producto_id=producto AND observacion='Demo Fill Rate ' || escenario.sku AND venta_id IS NOT NULL) THEN
            orden := nextval('numero_orden_seq');
            factura := nextval('numero_factura_seq');
            INSERT INTO ventas(usuario_id,cliente_id,fecha,subtotal,total,forma_pago,descuento_pct,
                               numero_orden,numero_factura,estado,fecha_envio)
            VALUES (usuario,cliente,fecha_pedido,escenario.entregada,escenario.entregada,'EFECTIVO',0,
                    'OC-' || to_char(fecha_pedido,'YYYY') || '-' || lpad(orden::TEXT,greatest(6,length(orden::TEXT)),'0'),
                    'FAC-0001-' || lpad(factura::TEXT,greatest(8,length(factura::TEXT)),'0'),'ENTREGADO',fecha_pedido::DATE)
            RETURNING id INTO venta;
            INSERT INTO detalle_venta(venta_id,producto_id,cantidad_solicitada,cantidad,precio_unitario)
                VALUES (venta,producto,1000,escenario.entregada,1.00);
            -- Ingreso histórico y salida total: stock actual0, también sin lotes si07 ya corrió.
            INSERT INTO movimientos_stock(producto_id,tipo,cantidad,fecha,usuario_id,observacion)
                VALUES (producto,'COMPRA',escenario.entregada,fecha_pedido-INTERVAL '1 day',usuario,
                        'Demo Fill Rate ' || escenario.sku);
            INSERT INTO movimientos_stock(producto_id,tipo,cantidad,fecha,usuario_id,venta_id,observacion)
                VALUES (producto,'VENTA',-escenario.entregada,fecha_pedido,usuario,venta,
                        'Demo Fill Rate ' || escenario.sku);
        END IF;
    END LOOP;
END $$;
COMMIT;
