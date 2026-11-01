-- Ejecutar exclusivamente sobre una base de pruebas con 01..06 aplicados:
-- psql -v ON_ERROR_STOP=1 -f database/tests/07_lotes_test.sql
\set ON_ERROR_STOP on
CREATE TEMP TABLE stock_antes AS SELECT id,stock_actual FROM productos;
\ir ../init/07_lotes.sql
CREATE TEMP TABLE lotes_antes AS SELECT * FROM lotes;
\ir ../init/07_lotes.sql
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM productos p JOIN stock_antes a USING(id) WHERE p.stock_actual<>a.stock_actual) THEN
        RAISE EXCEPTION '07 modificó el stock';
    END IF;
    IF EXISTS (SELECT * FROM lotes EXCEPT SELECT * FROM lotes_antes)
       OR EXISTS (SELECT * FROM lotes_antes EXCEPT SELECT * FROM lotes) THEN
        RAISE EXCEPTION '07 no es idempotente';
    END IF;
    IF EXISTS (SELECT p.id FROM productos p LEFT JOIN lotes l ON l.producto_id=p.id
               GROUP BY p.id HAVING p.stock_actual<>COALESCE(SUM(l.cantidad_disponible),0)) THEN
        RAISE EXCEPTION 'Stock distinto de la suma de lotes';
    END IF;
    IF EXISTS (SELECT p.id FROM productos p LEFT JOIN lotes l ON l.producto_id=p.id AND l.es_apertura
               GROUP BY p.id HAVING COUNT(l.id)<>1) THEN
        RAISE EXCEPTION 'Debe existir exactamente una apertura por producto';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM lotes l JOIN productos p ON p.id=l.producto_id JOIN categorias c ON c.id=p.categoria_id
                   WHERE l.fecha_vencimiento <= (CURRENT_TIMESTAMP AT TIME ZONE 'America/Argentina/Buenos_Aires')::DATE+c.dias_alerta_vencimiento)
       OR NOT EXISTS (SELECT 1 FROM lotes l JOIN productos p ON p.id=l.producto_id JOIN categorias c ON c.id=p.categoria_id
                      WHERE l.fecha_vencimiento > (CURRENT_TIMESTAMP AT TIME ZONE 'America/Argentina/Buenos_Aires')::DATE+c.dias_alerta_vencimiento) THEN
        RAISE EXCEPTION 'Faltan ejemplos escalonados dentro/fuera de alerta';
    END IF;
    BEGIN
        UPDATE lotes SET cantidad_disponible=-1 WHERE id=(SELECT MIN(id) FROM lotes);
        RAISE EXCEPTION 'Se permitió un disponible negativo';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO lotes(producto_id,cantidad_inicial,cantidad_disponible,fecha_entrega,costo_unitario,es_apertura)
        SELECT producto_id,0,0,CURRENT_DATE,0,TRUE FROM lotes WHERE es_apertura LIMIT 1;
        RAISE EXCEPTION 'Se permitió duplicar apertura';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
END $$;
