CREATE TABLE IF NOT EXISTS proveedores (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    direccion VARCHAR(255),
    telefono VARCHAR(50),
    lead_time_dias INTEGER NOT NULL CHECK (lead_time_dias > 0),
    calidad VARCHAR(10) NOT NULL CHECK (calidad IN ('BUENA','REGULAR','MALA')),
    clasificacion INTEGER NOT NULL CHECK (clasificacion BETWEEN 1 AND 5),
    activo BOOLEAN NOT NULL DEFAULT TRUE
);
ALTER TABLE productos ADD COLUMN IF NOT EXISTS proveedor_id BIGINT REFERENCES proveedores(id);
ALTER TABLE productos ADD COLUMN IF NOT EXISTS marca VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_productos_proveedor ON productos(proveedor_id);

INSERT INTO proveedores (nombre,direccion,telefono,lead_time_dias,calidad,clasificacion)
SELECT s.* FROM (VALUES
 ('Distribuidora del Sur','Av. San Martín 1200','1145551000',7,'BUENA',5),
 ('Bebidas del Plata','Av. Rivadavia 5400','1145552000',3,'BUENA',4),
 ('Limpieza Central','Av. Independencia 1800','1145553000',10,'REGULAR',2),
 ('Alimentos Frescos Norte','Av. Cabildo 3200','1145554000',2,'MALA',1)
) AS s(nombre,direccion,telefono,lead_time_dias,calidad,clasificacion)
WHERE NOT EXISTS (SELECT 1 FROM proveedores p WHERE p.nombre=s.nombre);

UPDATE productos p SET proveedor_id = s.proveedor_id, marca=COALESCE(p.marca,s.marca)
FROM (
 SELECT d.sku,d.marca,MIN(pr.id) AS proveedor_id FROM (VALUES
 ('ALM-001','Playadito','Distribuidora del Sur'),
 ('ALM-002','Matarazzo','Distribuidora del Sur'),
 ('ALM-003','Gallo','Distribuidora del Sur'),
 ('ALM-004','Pureza','Distribuidora del Sur'),
 ('BEB-001','Villavicencio','Bebidas del Plata'),
 ('BEB-002','Coca-Cola','Bebidas del Plata'),
 ('LIM-001','Ayudín','Limpieza Central'),
 ('LIM-002','Magistral','Limpieza Central')
 ) AS d(sku,marca,proveedor) JOIN proveedores pr ON pr.nombre=d.proveedor
 GROUP BY d.sku,d.marca
) s WHERE p.sku=s.sku AND p.proveedor_id IS NULL;
