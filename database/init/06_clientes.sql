BEGIN;
CREATE TABLE IF NOT EXISTS clientes (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    direccion VARCHAR(255),
    telefono VARCHAR(50),
    calidad VARCHAR(10) NOT NULL CHECK (calidad IN ('BUENA','REGULAR','MALA')),
    clasificacion INTEGER NOT NULL CHECK (clasificacion BETWEEN 1 AND 5),
    activo BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO clientes(nombre,direccion,telefono,calidad,clasificacion)
SELECT s.* FROM (VALUES
 ('Día Express','Av. Corrientes 1800','1145555000','BUENA',5),
 ('Carrefour Express','Av. Santa Fe 2500','1145556000','BUENA',4),
 ('Chino Almagro','Av. Medrano 400','1145557000','REGULAR',3),
 ('Jumbo Express','Av. Acoyte 600','1145558000','BUENA',4),
 ('Consumidor final',NULL,NULL,'REGULAR',3)
) s(nombre,direccion,telefono,calidad,clasificacion)
WHERE NOT EXISTS (SELECT 1 FROM clientes c WHERE c.nombre=s.nombre);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='ventas' AND column_name='cliente_id') THEN
        ALTER TABLE ventas ADD COLUMN cliente_id BIGINT REFERENCES clientes(id);
        UPDATE ventas v SET cliente_id=c.ids[1+((v.id-1)%array_length(c.ids,1))::INTEGER]
        FROM (SELECT array_agg(id ORDER BY id) ids FROM clientes WHERE nombre IN ('Día Express','Carrefour Express','Chino Almagro','Jumbo Express','Consumidor final')) c
        WHERE v.cliente_id IS NULL;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_ventas_cliente_fecha ON ventas(cliente_id,fecha DESC,id DESC);
COMMIT;
