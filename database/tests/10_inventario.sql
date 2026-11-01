SET TIME ZONE 'America/Argentina/Buenos_Aires';
-- Ejecutar con psql -v ON_ERROR_STOP=1 después de 01..06 y 10 en una base descartable.
CREATE TEMP VIEW inventario_demo_medidas AS
WITH ventas_periodo AS (
    SELECT p.id, periodo.dias, COALESCE(SUM(d.cantidad) FILTER (
        WHERE NOT v.anulada AND v.fecha >= CURRENT_DATE-periodo.dias AND v.fecha < CURRENT_DATE),0) unidades
    FROM productos p CROSS JOIN (VALUES (30),(90)) periodo(dias)
    LEFT JOIN detalle_venta d ON d.producto_id=p.id LEFT JOIN ventas v ON v.id=d.venta_id
    WHERE p.sku LIKE 'DEMO-INV-%' GROUP BY p.id,periodo.dias
), stock_diario AS (
    -- Misma ventana y reconstrucción que app.db.stock_historico (incluye hoy).
    SELECT p.id, periodo.dias, AVG(p.stock_actual-COALESCE((
        SELECT SUM(m.cantidad) FROM movimientos_stock m
        WHERE m.producto_id=p.id AND m.fecha::DATE > dia::DATE),0)) promedio
    FROM productos p CROSS JOIN (VALUES (30),(90)) periodo(dias)
    CROSS JOIN LATERAL generate_series(CURRENT_DATE-(periodo.dias-1),CURRENT_DATE,INTERVAL '1 day') dia
    WHERE p.sku LIKE 'DEMO-INV-%' GROUP BY p.id,periodo.dias
)
SELECT p.sku,c.nombre grupo,p.stock_actual,p.costo,c.umbral_rotacion,v.dias,v.unidades,s.promedio,
    p.stock_actual*v.dias/NULLIF(v.unidades,0) dsi,
    v.unidades/NULLIF(s.promedio,0) rotacion
FROM productos p JOIN categorias c ON c.id=p.categoria_id
JOIN ventas_periodo v ON v.id=p.id JOIN stock_diario s ON s.id=p.id AND s.dias=v.dias;

DO $$
BEGIN
    ASSERT (SELECT COUNT(*) FROM parametros WHERE clave IN ('dsi_rojo_bajo','dsi_amarillo_alto','dsi_rojo_alto','dias_sin_ventas','fill_rate_objetivo','riesgo_amarillo_pct','riesgo_rojo_pct') AND descripcion IS NOT NULL)=7,
        'Faltan los siete parámetros del dashboard';
    ASSERT (SELECT COUNT(*) FROM parametros p JOIN (VALUES
        ('dsi_rojo_bajo','7'),('dsi_amarillo_alto','45'),('dsi_rojo_alto','90'),('dias_sin_ventas','90'),
        ('fill_rate_objetivo','95'),('riesgo_amarillo_pct','5'),('riesgo_rojo_pct','10')) d(clave,valor)
        ON p.clave=d.clave AND p.valor=d.valor)=7, 'Valores iniciales incorrectos (prueba sobre base nueva)';
    ASSERT (SELECT COUNT(*) FROM productos WHERE sku LIKE 'DEMO-INV-%')=8, 'Faltan productos demo';
    ASSERT NOT EXISTS (SELECT 1 FROM productos WHERE sku LIKE 'DEMO-INV-%' AND (marca IS NULL OR proveedor_id IS NULL OR categoria_id IS NULL)), 'Falta marca, proveedor o grupo';
    ASSERT (SELECT COUNT(*) FROM inventario_demo_medidas WHERE dias=90 AND unidades=0 AND stock_actual>0)=2, 'Deben existir dos productos sin ventas';
    ASSERT (SELECT COUNT(*) FROM inventario_demo_medidas WHERE dias=30 AND unidades>0 AND rotacion<umbral_rotacion)=2, 'Deben existir dos productos vendidos con baja rotación';
    ASSERT (SELECT COUNT(*) FROM inventario_demo_medidas WHERE sku LIKE 'DEMO-INV-RAP-%' AND dsi=2)=4, 'DSI rápido esperado 2 en 30 y 90 días';
    ASSERT (SELECT COUNT(*) FROM inventario_demo_medidas WHERE sku='DEMO-INV-EQU-01' AND dsi=20)=2, 'DSI equilibrado esperado 20 en 30 y 90 días';
    ASSERT (SELECT COUNT(*) FROM inventario_demo_medidas WHERE sku='DEMO-INV-EQU-02' AND dsi=60)=2, 'DSI excedente esperado amarillo en 30 y 90 días';
    ASSERT (SELECT COUNT(*) FROM inventario_demo_medidas WHERE sku LIKE 'DEMO-INV-LEN-%' AND dsi>=90)=4, 'DSI lento esperado rojo alto';
    IF to_regclass('lotes') IS NOT NULL THEN
        ASSERT NOT EXISTS (SELECT 1 FROM productos p LEFT JOIN lotes l ON l.producto_id=p.id
            WHERE p.sku LIKE 'DEMO-INV-%' GROUP BY p.id,p.stock_actual
            HAVING COALESCE(SUM(l.cantidad_disponible),0)<>p.stock_actual), 'Lotes no reflejan stock';
        ASSERT NOT EXISTS (SELECT 1 FROM lotes l JOIN productos p ON p.id=l.producto_id
            WHERE p.sku LIKE 'DEMO-INV-%' AND l.cantidad_inicial-l.cantidad_disponible<>
            COALESCE((SELECT SUM(c.cantidad) FROM consumos_lote c WHERE c.lote_id=l.id),0)), 'Consumos de lote incoherentes';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='ventas' AND column_name='estado') THEN
        ASSERT NOT EXISTS (SELECT 1 FROM ventas v JOIN detalle_venta d ON d.venta_id=v.id JOIN productos p ON p.id=d.producto_id
            WHERE p.sku LIKE 'DEMO-INV-%' AND (v.estado<>'ENTREGADO' OR v.estado_entrega<>'ENTREGADA' OR v.subtotal<>v.total OR v.numero_factura IS NULL)), 'Pedido demo incoherente';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='detalle_venta' AND column_name='cantidad_solicitada') THEN
        ASSERT NOT EXISTS (SELECT 1 FROM detalle_venta d JOIN productos p ON p.id=d.producto_id
            WHERE p.sku LIKE 'DEMO-INV-%' AND d.cantidad_solicitada<>d.cantidad), 'Solicitada demo debe coincidir con entregada';
    END IF;
    ASSERT NOT EXISTS (
        SELECT 1 FROM productos p LEFT JOIN movimientos_stock m ON m.producto_id=p.id
        WHERE p.sku LIKE 'DEMO-INV-%' GROUP BY p.id,p.stock_actual HAVING COALESCE(SUM(m.cantidad),0)<>p.stock_actual
    ), 'Movimientos no reconstruyen el stock actual';
    ASSERT NOT EXISTS (
        SELECT 1 FROM (SELECT SUM(m.cantidad) OVER(PARTITION BY m.producto_id ORDER BY m.fecha,m.id) stock
        FROM movimientos_stock m JOIN productos p ON p.id=m.producto_id WHERE p.sku LIKE 'DEMO-INV-%') saldos WHERE stock<0
    ), 'Hay stock histórico negativo';
    ASSERT NOT EXISTS (
        SELECT 1 FROM detalle_venta d JOIN productos p ON p.id=d.producto_id JOIN ventas v ON v.id=d.venta_id
        WHERE p.sku LIKE 'DEMO-INV-%' AND (v.total<>d.cantidad*d.precio_unitario OR
        (SELECT COUNT(*) FROM movimientos_stock m WHERE m.venta_id=v.id AND m.producto_id=p.id AND m.tipo='VENTA' AND m.cantidad=-d.cantidad AND m.fecha=v.fecha)<>1)
    ), 'Venta, importe y movimiento deben ser coherentes';
END $$;

SELECT grupo,dias,SUM(stock_actual*costo) valor_stock,SUM(unidades*costo) costo_ventas,
    ROUND(SUM(stock_actual*costo)*dias/NULLIF(SUM(unidades*costo),0),2) dsi,
    ROUND(SUM(unidades*costo)/NULLIF(SUM(promedio*costo),0)*30/dias,4) rotacion_mensual,
    ROUND(SUM(unidades*costo)/NULLIF(SUM(promedio*costo),0)*365/dias,4) rotacion_anual
FROM inventario_demo_medidas GROUP BY grupo,dias ORDER BY grupo,dias;
DROP VIEW inventario_demo_medidas;
