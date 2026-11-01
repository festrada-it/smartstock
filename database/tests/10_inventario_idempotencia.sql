-- psql -v ON_ERROR_STOP=1 -f database/tests/10_inventario_idempotencia.sql
-- Exclusivamente en una base de pruebas, después de 01..06 (opcionalmente 07 y 08).
\ir ../init/10_parametros_y_datos_inventario.sql
\ir 10_inventario.sql
CREATE TEMP TABLE parametro_original AS SELECT * FROM parametros WHERE clave='dsi_rojo_bajo';
UPDATE parametros SET valor='11',descripcion='Valor personalizado del administrador' WHERE clave='dsi_rojo_bajo';
CREATE TEMP TABLE instantanea_inventario(tabla TEXT PRIMARY KEY, contenido JSONB);
DO $$
DECLARE t RECORD; contenido JSONB;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname=current_schema() LOOP
        EXECUTE format('SELECT COALESCE(jsonb_agg(fila ORDER BY fila::TEXT),''[]''::JSONB) FROM (SELECT to_jsonb(t) fila FROM %I t) datos',t.tablename) INTO contenido;
        INSERT INTO instantanea_inventario VALUES(t.tablename,contenido);
    END LOOP;
END $$;
\ir ../init/10_parametros_y_datos_inventario.sql
DO $$
DECLARE t RECORD; contenido_actual JSONB;
BEGIN
    FOR t IN SELECT * FROM instantanea_inventario LOOP
        EXECUTE format('SELECT COALESCE(jsonb_agg(fila ORDER BY fila::TEXT),''[]''::JSONB) FROM (SELECT to_jsonb(t) fila FROM %I t) datos',t.tabla) INTO contenido_actual;
        ASSERT contenido_actual=t.contenido, 'La segunda ejecución modificó '||t.tabla;
    END LOOP;
END $$;
UPDATE parametros p SET valor=o.valor,descripcion=o.descripcion FROM parametro_original o WHERE p.clave=o.clave;
DROP TABLE instantanea_inventario,parametro_original;
