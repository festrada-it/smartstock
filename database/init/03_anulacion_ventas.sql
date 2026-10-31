-- En bases existentes, ejecutar este script antes de desplegar el nuevo core/analytics.
-- No es necesario borrar el volumen ni recrear los datos.
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS anulada BOOLEAN NOT NULL DEFAULT FALSE;
