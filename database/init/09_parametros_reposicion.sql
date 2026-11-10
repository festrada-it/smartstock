-- Parámetros de reposición por proveedor (#30). Idempotente: se puede ejecutar sobre bases existentes
-- sin borrar datos, y no pisa un valor que el Administrador ya haya modificado.
INSERT INTO parametros (clave, valor, descripcion) VALUES
    ('margen_dias_proveedor_bajo', '3',
     'Días extra de anticipación al pedirle a un proveedor poco confiable (clasificación baja o calidad MALA)'),
    ('clasificacion_proveedor_baja', '2',
     'Clasificación (1 a 5 estrellas) igual o inferior a la cual el proveedor se considera poco confiable')
ON CONFLICT (clave) DO NOTHING;
