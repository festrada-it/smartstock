-- SmartStock - Datos de prueba (solo para desarrollo)

-- Usuarios de prueba:
--   admin@smartstock.com    / admin123     (ADMIN)
--   vendedor@smartstock.com / vendedor123  (VENDEDOR)
INSERT INTO usuarios (nombre, email, password_hash, rol) VALUES
    ('Administrador', 'admin@smartstock.com',    '$2a$10$wkZr0EtOCJQdQHv.F8gX3u5348.yhuqGL531NcZoa/TyL6FoLYUy2', 'ADMIN'),
    ('Vendedor',      'vendedor@smartstock.com', '$2a$10$NWpEbwJHBbOoVryrCPA7xevlJMsYWrq5HuBqE9ThfMITRlBIBwHsK', 'VENDEDOR');

INSERT INTO parametros (clave, valor, descripcion) VALUES
    ('dias_reposicion',        '7',  'Días que tarda el proveedor en entregar la mercadería'),
    ('dias_cobertura',         '30', 'Días de demanda que debe cubrir una compra sugerida'),
    ('umbral_criticidad_alta', '7',  'Días hasta el quiebre por debajo de los cuales la alerta es ALTA'),
    ('umbral_criticidad_media','15', 'Días hasta el quiebre por debajo de los cuales la alerta es MEDIA');

INSERT INTO categorias (nombre) VALUES ('Almacén'), ('Bebidas'), ('Limpieza');

INSERT INTO productos (sku, nombre, categoria_id, precio, costo, stock_actual, stock_minimo) VALUES
    ('ALM-001', 'Yerba mate 1kg',         1, 4500.00, 3100.00, 120, 20),
    ('ALM-002', 'Fideos tirabuzón 500g',  1, 1200.00,  750.00,  35, 15),
    ('ALM-003', 'Arroz largo fino 1kg',   1, 1800.00, 1150.00,  60, 15),
    ('ALM-004', 'Harina 000 1kg',         1,  950.00,  600.00,  18, 20),
    ('BEB-001', 'Agua mineral 2L',        2,  900.00,  520.00,  25, 30),
    ('BEB-002', 'Gaseosa cola 1.5L',      2, 2300.00, 1500.00,  80, 25),
    ('LIM-001', 'Lavandina 1L',           3,  950.00,  580.00,  40, 10),
    ('LIM-002', 'Detergente 750ml',       3, 1600.00, 1000.00,  12, 10);

-- Historial de ventas simulado de los últimos 90 días
-- (lo necesita el microservicio de analítica para entrenar el modelo)
DO $$
DECLARE
    dia       DATE;
    p         RECORD;
    cantidad  INTEGER;
    nueva_id  BIGINT;
BEGIN
    FOR dia IN SELECT generate_series(CURRENT_DATE - 90, CURRENT_DATE - 1, INTERVAL '1 day')::DATE LOOP
        FOR p IN SELECT id, precio FROM productos LOOP
            -- demanda base distinta por producto + ruido + leve tendencia creciente
            cantidad := GREATEST(0, ROUND((p.id % 4) + 1 + random() * 3 + (dia - (CURRENT_DATE - 90)) * 0.02)::INTEGER);
            IF cantidad > 0 THEN
                INSERT INTO ventas (usuario_id, fecha, total)
                VALUES (2, dia + TIME '12:00', p.precio * cantidad)
                RETURNING id INTO nueva_id;

                INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario)
                VALUES (nueva_id, p.id, cantidad, p.precio);
            END IF;
        END LOOP;
    END LOOP;
END $$;

-- Cada venta genera su movimiento de stock (salida)
INSERT INTO movimientos_stock (producto_id, tipo, cantidad, fecha, usuario_id, venta_id)
SELECT d.producto_id, 'VENTA', -d.cantidad, v.fecha, v.usuario_id, v.id
FROM detalle_venta d
JOIN ventas v ON v.id = d.venta_id;

-- Compras a proveedor cada 14 días: reponen lo que se vende en los 14 días siguientes
INSERT INTO movimientos_stock (producto_id, tipo, cantidad, fecha, usuario_id, observacion)
SELECT d.producto_id, 'COMPRA', SUM(d.cantidad), g.dia + INTERVAL '8 hours', 1, 'Reposición (datos de prueba)'
FROM generate_series(CURRENT_DATE - 90, CURRENT_DATE - 14, INTERVAL '14 day') AS g(dia)
JOIN ventas v ON v.fecha >= g.dia AND v.fecha < g.dia + INTERVAL '14 day'
JOIN detalle_venta d ON d.venta_id = v.id
GROUP BY d.producto_id, g.dia;
