-- Datos de desarrollo: contrato y medidas en docs/DATOS-INVENTARIO.md.
BEGIN;
INSERT INTO parametros(clave,valor,descripcion) VALUES
 ('dsi_rojo_bajo','7','Días de inventario por debajo de los cuales hay riesgo de quiebre'),
 ('dsi_amarillo_alto','45','Días de inventario desde los cuales hay dinero atrapado, nivel amarillo'),
 ('dsi_rojo_alto','90','Días de inventario desde los cuales hay dinero atrapado, nivel rojo'),
 ('dias_sin_ventas','90','Ventana en días para detectar productos con stock y sin ventas'),
 ('fill_rate_objetivo','95','Objetivo porcentual de unidades entregadas sobre solicitadas'),
 ('riesgo_amarillo_pct','5','Porcentaje del valor del stock en riesgo desde el cual se muestra amarillo'),
 ('riesgo_rojo_pct','10','Porcentaje del valor del stock en riesgo por encima del cual se muestra rojo')
ON CONFLICT(clave) DO NOTHING;

INSERT INTO proveedores(nombre,lead_time_dias,calidad,clasificacion)
SELECT 'Demo inventario - proveedor',3,'BUENA',5
WHERE NOT EXISTS (SELECT 1 FROM proveedores WHERE nombre='Demo inventario - proveedor');
INSERT INTO categorias(nombre,dias_alerta_vencimiento,umbral_rotacion) VALUES
 ('Demo inventario - rápida',60,0.10),
 ('Demo inventario - equilibrada',60,0.10),
 ('Demo inventario - excedente',60,0.10),
 ('Demo inventario - lenta',60,0.10),
 ('Demo inventario - sin ventas',60,0.10)
ON CONFLICT(nombre) DO NOTHING;

-- Solo los SKU nuevos reciben historia; una segunda ejecución no altera la demo ni datos del usuario.
DO $$
DECLARE
    p RECORD;
    dia DATE;
    cantidad INTEGER;
    unidades_historicas INTEGER;
    nueva_venta BIGINT;
    usuario BIGINT;
    cliente BIGINT;
    detalle BIGINT;
    lote BIGINT;
    hoy DATE := (CURRENT_TIMESTAMP AT TIME ZONE 'America/Argentina/Buenos_Aires')::DATE;
    tiene_lotes BOOLEAN := to_regclass('lotes') IS NOT NULL;
    tiene_pedidos BOOLEAN := EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema=current_schema() AND table_name='ventas' AND column_name='numero_orden');
    tiene_solicitada BOOLEAN := EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema()
        AND table_name='detalle_venta' AND column_name='cantidad_solicitada');
BEGIN
    SELECT id INTO usuario FROM usuarios
    ORDER BY CASE WHEN email='vendedor@smartstock.com' THEN 0 ELSE 1 END,id LIMIT 1;
    SELECT MIN(id) INTO cliente FROM clientes WHERE nombre='Consumidor final';
    IF usuario IS NULL THEN
        RAISE EXCEPTION 'Los datos demo de inventario requieren un usuario existente (scripts 01..06)';
    END IF;
    FOR p IN
        INSERT INTO productos(sku,nombre,categoria_id,marca,proveedor_id,precio,costo,stock_actual,stock_minimo)
        SELECT d.sku,d.nombre,c.id,'Demo Inventario',
            (SELECT MIN(id) FROM proveedores WHERE nombre='Demo inventario - proveedor'),150,100,d.stock,10
        FROM (VALUES
            ('DEMO-INV-RAP-01','Demo inventario rápida A','Demo inventario - rápida',20),
            ('DEMO-INV-RAP-02','Demo inventario rápida B','Demo inventario - rápida',20),
            ('DEMO-INV-EQU-01','Demo inventario equilibrada','Demo inventario - equilibrada',40),
            ('DEMO-INV-EQU-02','Demo inventario excedente','Demo inventario - excedente',120),
            ('DEMO-INV-LEN-01','Demo inventario lenta A','Demo inventario - lenta',100),
            ('DEMO-INV-LEN-02','Demo inventario lenta B','Demo inventario - lenta',120),
            ('DEMO-INV-SIN-01','Demo inventario sin ventas A','Demo inventario - sin ventas',50),
            ('DEMO-INV-SIN-02','Demo inventario sin ventas B','Demo inventario - sin ventas',70)
        ) d(sku,nombre,grupo,stock)
        JOIN categorias c ON c.nombre=d.grupo
        ON CONFLICT(sku) DO NOTHING RETURNING id,sku,precio,stock_actual
    LOOP
        unidades_historicas := CASE
            WHEN p.sku LIKE 'DEMO-INV-RAP-%' THEN 900
            WHEN p.sku LIKE 'DEMO-INV-EQU-%' THEN 180
            WHEN p.sku LIKE 'DEMO-INV-LEN-%' THEN 6 ELSE 0 END;
        IF tiene_lotes THEN
            INSERT INTO lotes(producto_id,proveedor_id,cantidad_inicial,cantidad_disponible,
                fecha_entrega,costo_unitario,es_apertura)
            SELECT id,proveedor_id,p.stock_actual+unidades_historicas,p.stock_actual,hoy-90,costo,TRUE
            FROM productos WHERE id=p.id RETURNING id INTO lote;
        END IF;
        -- La compra inicial cubre toda la historia; los saldos nunca son negativos.
        INSERT INTO movimientos_stock(producto_id,tipo,cantidad,fecha,usuario_id,observacion)
        VALUES(p.id,'COMPRA',p.stock_actual+unidades_historicas,(hoy-90)+TIME '08:00',usuario,
            'Demo inventario #56: existencia inicial e historia de 90 días');
        FOR dia IN SELECT generate_series(hoy-90,hoy-1,INTERVAL '1 day')::DATE LOOP
            cantidad := CASE
                WHEN p.sku LIKE 'DEMO-INV-RAP-%' THEN 10
                WHEN p.sku LIKE 'DEMO-INV-EQU-%' THEN 2
                WHEN p.sku LIKE 'DEMO-INV-LEN-%' AND (hoy-dia)%15=0 THEN 1 ELSE 0 END;
            IF cantidad > 0 THEN
                IF tiene_pedidos THEN
                    INSERT INTO ventas(usuario_id,cliente_id,fecha,total,subtotal,estado,estado_entrega,
                        numero_orden,numero_factura,forma_pago,descuento_pct)
                    VALUES(usuario,cliente,dia+TIME '12:00',p.precio*cantidad,p.precio*cantidad,
                        'ENTREGADO','ENTREGADA',
                        'OC-'||to_char(dia,'YYYY')||'-'||lpad(nextval('numero_orden_seq')::TEXT,12,'0'),
                        'FAC-0001-'||lpad(nextval('numero_factura_seq')::TEXT,12,'0'),'EFECTIVO',0)
                    RETURNING id INTO nueva_venta;
                ELSE
                    INSERT INTO ventas(usuario_id,cliente_id,fecha,total)
                    VALUES(usuario,cliente,dia+TIME '12:00',p.precio*cantidad) RETURNING id INTO nueva_venta;
                END IF;
                -- 08 puede imponer NOT NULL sin DEFAULT: no se lo presupone para correr sobre 01..06.
                IF tiene_solicitada THEN
                    INSERT INTO detalle_venta(venta_id,producto_id,cantidad,precio_unitario,cantidad_solicitada)
                    VALUES(nueva_venta,p.id,cantidad,p.precio,cantidad) RETURNING id INTO detalle;
                ELSE
                    INSERT INTO detalle_venta(venta_id,producto_id,cantidad,precio_unitario)
                    VALUES(nueva_venta,p.id,cantidad,p.precio) RETURNING id INTO detalle;
                END IF;
                IF tiene_lotes THEN
                    INSERT INTO consumos_lote(detalle_venta_id,lote_id,cantidad) VALUES(detalle,lote,cantidad);
                END IF;
                INSERT INTO movimientos_stock(producto_id,tipo,cantidad,fecha,usuario_id,venta_id)
                VALUES(p.id,'VENTA',-cantidad,dia+TIME '12:00',usuario,nueva_venta);
            END IF;
        END LOOP;
        IF tiene_lotes THEN
            UPDATE movimientos_stock SET lote_id=lote WHERE producto_id=p.id;
        END IF;
    END LOOP;
END $$;
COMMIT;
