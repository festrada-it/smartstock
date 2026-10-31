-- SmartStock - Datos de prueba (solo para desarrollo)

-- Usuario admin de prueba -> email: admin@smartstock.com / password: admin123
INSERT INTO usuarios (nombre, email, password_hash, rol) VALUES
    ('Administrador', 'admin@smartstock.com', '$2a$10$wkZr0EtOCJQdQHv.F8gX3u5348.yhuqGL531NcZoa/TyL6FoLYUy2', 'ADMIN');

INSERT INTO categorias (nombre) VALUES ('Almacén'), ('Bebidas'), ('Limpieza');

INSERT INTO productos (sku, nombre, categoria_id, precio, stock_actual, stock_minimo) VALUES
    ('ALM-001', 'Yerba mate 1kg',         1, 4500.00, 120, 20),
    ('ALM-002', 'Fideos tirabuzón 500g',  1, 1200.00,  35, 15),
    ('ALM-003', 'Arroz largo fino 1kg',   1, 1800.00,  60, 15),
    ('BEB-001', 'Agua mineral 2L',        2,  900.00,  25, 30),
    ('BEB-002', 'Gaseosa cola 1.5L',      2, 2300.00,  80, 25),
    ('LIM-001', 'Lavandina 1L',           3,  950.00,  40, 10),
    ('LIM-002', 'Detergente 750ml',       3, 1600.00,  12, 10);

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
                VALUES (1, dia + TIME '12:00', p.precio * cantidad)
                RETURNING id INTO nueva_id;

                INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario)
                VALUES (nueva_id, p.id, cantidad, p.precio);
            END IF;
        END LOOP;
    END LOOP;
END $$;
