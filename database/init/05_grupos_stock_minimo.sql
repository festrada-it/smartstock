BEGIN;
DO $$
DECLARE primera BOOLEAN := NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=current_schema() AND table_name='categorias' AND column_name='dias_alerta_vencimiento');
BEGIN
    ALTER TABLE categorias ADD COLUMN IF NOT EXISTS dias_alerta_vencimiento INTEGER NOT NULL DEFAULT 60 CHECK (dias_alerta_vencimiento > 0);
    ALTER TABLE categorias ADD COLUMN IF NOT EXISTS umbral_rotacion NUMERIC NOT NULL DEFAULT 0.10 CHECK (umbral_rotacion BETWEEN 0 AND 1);
    IF primera THEN
        UPDATE categorias SET dias_alerta_vencimiento=CASE nombre
            WHEN 'Lácteos' THEN 15 WHEN 'Frescos' THEN 10
            WHEN 'Bebidas' THEN 90 WHEN 'Limpieza' THEN 120 ELSE 60 END;
        UPDATE productos SET stock_minimo=30 WHERE sku IN ('ALM-001','ALM-002','ALM-003','ALM-004','BEB-001','BEB-002','LIM-001','LIM-002');
    END IF;
END $$;
ALTER TABLE productos ALTER COLUMN stock_minimo SET DEFAULT 30;
INSERT INTO categorias(nombre,dias_alerta_vencimiento,umbral_rotacion) VALUES
 ('Lácteos',15,0.10),('Frescos',10,0.10),('Almacén',60,0.10),('Bebidas',90,0.10),('Limpieza',120,0.10)
ON CONFLICT(nombre) DO NOTHING;

-- Se genera historia únicamente para los productos insertados en esta ejecución.
DO $$
DECLARE p RECORD; dia DATE; cantidad INTEGER; venta_id BIGINT; v_usuario_id BIGINT;
BEGIN
    SELECT id INTO v_usuario_id FROM usuarios ORDER BY CASE WHEN email='vendedor@smartstock.com' THEN 0 ELSE 1 END,id LIMIT 1;
    FOR p IN
        INSERT INTO productos(sku,nombre,categoria_id,marca,proveedor_id,precio,costo,stock_actual,stock_minimo)
        SELECT d.sku,d.nombre,c.id,d.marca,(SELECT MIN(id) FROM proveedores WHERE nombre=d.proveedor),d.precio,d.costo,d.stock,30
        FROM (VALUES
          ('LACT-001','Leche entera 1L','Lácteos','SanCor','Distribuidora del Sur',1800,1200,45),
          ('LACT-002','Yogur natural 190g','Lácteos','La Serenísima','Alimentos Frescos Norte',1100,700,20),
          ('FRES-001','Tomate redondo 1kg','Frescos','Huerta Norte','Alimentos Frescos Norte',2200,1500,18),
          ('FRES-002','Lechuga criolla unidad','Frescos','Huerta Norte','Alimentos Frescos Norte',1200,750,50)
        ) d(sku,nombre,grupo,marca,proveedor,precio,costo,stock)
        JOIN categorias c ON c.nombre=d.grupo
        ON CONFLICT(sku) DO NOTHING RETURNING id,precio
    LOOP
        IF v_usuario_id IS NOT NULL THEN
            FOR dia IN SELECT generate_series(CURRENT_DATE-90,CURRENT_DATE-1,INTERVAL '1 day')::DATE LOOP
                cantidad := 1 + ((EXTRACT(DOY FROM dia)::INTEGER + p.id::INTEGER) % 5);
                INSERT INTO ventas(usuario_id,fecha,total) VALUES(v_usuario_id,dia+TIME '12:00',p.precio*cantidad) RETURNING id INTO venta_id;
                INSERT INTO detalle_venta(venta_id,producto_id,cantidad,precio_unitario) VALUES(venta_id,p.id,cantidad,p.precio);
                INSERT INTO movimientos_stock(producto_id,tipo,cantidad,fecha,usuario_id,venta_id)
                VALUES(p.id,'VENTA',-cantidad,dia+TIME '12:00',v_usuario_id,venta_id);
            END LOOP;
            INSERT INTO movimientos_stock(producto_id,tipo,cantidad,fecha,usuario_id,observacion)
            SELECT p.id,'COMPRA',SUM(d.cantidad)::INTEGER,g.dia+INTERVAL '8 hours',v_usuario_id,'Reposición (ejemplos mayoristas)'
            FROM generate_series(CURRENT_DATE-90,CURRENT_DATE-14,INTERVAL '14 day') g(dia)
            JOIN ventas v ON v.fecha >= g.dia AND v.fecha < g.dia+INTERVAL '14 day'
            JOIN detalle_venta d ON d.venta_id=v.id AND d.producto_id=p.id GROUP BY g.dia;
        END IF;
    END LOOP;
END $$;
COMMIT;
