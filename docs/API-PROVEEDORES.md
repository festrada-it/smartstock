# Contrato API — Proveedores (#14)

Contrato publicado antes de implementar para conectar el frontend. Base `/api`;
JWT Bearer. JSON en camelCase. Fechas y moneda no se agregan a este recurso.

| Método | Ruta | Acceso | Resultado |
|---|---|---|---|
| GET | `/proveedores?q=` | ADMIN, VENDEDOR | 200, lista de activos ordenada por nombre/id; búsqueda opcional por nombre |
| GET | `/proveedores/{id}` | ambos | 200, proveedor activo |
| POST | `/proveedores` | ADMIN | 201, proveedor creado |
| PUT | `/proveedores/{id}` | ADMIN | 200, reemplaza datos editables |
| DELETE | `/proveedores/{id}` | ADMIN | 204, baja lógica |
| GET | `/proveedores/{id}/productos` | ambos | 200, productos activos ordenados por nombre/id |

POST/PUT reciben:

```json
{"nombre":"Distribuidora del Sur","direccion":"Av. San Martín 1200","telefono":"1145551234","leadTimeDias":7,"calidad":"BUENA","clasificacion":4}
```

Respuesta: los mismos campos más `id` y `activo`. Nombre obligatorio de hasta
150 caracteres, dirección opcional hasta 255 y teléfono opcional hasta 50.
`leadTimeDias` obligatorio y mayor a cero; `clasificacion` obligatoria de 1 a 5;
`calidad` obligatoria, exactamente BUENA/REGULAR/MALA. Los textos se recortan.

Productos del proveedor: lista de `{id, sku, nombre, marca}`. No incluye
productos inactivos. Proveedor inexistente o inactivo: 404 incluso en su lista
de productos. La baja conserva las asociaciones históricas con los productos.

Sin token: 401; permisos insuficientes: 403; datos inválidos (incluida calidad
desconocida): 400 con `{"error":"mensaje en español"}`; recurso ausente: 404
con el mismo formato. Hasta #13 el 403 sigue sin cuerpo.

## Base de datos

Aplicar `database/init/04_proveedores.sql` antes de desplegar esta versión de core.
Es idempotente: en bases nuevas se ejecuta automáticamente; en existentes:

```bash
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U smartstock -d smartstock < database/init/04_proveedores.sql
```

Agrega proveedores, `productos.proveedor_id` y `productos.marca`. Incluye cuatro
proveedores de ejemplo y asigna marca/proveedor a los ocho SKU originales.
Reejecutarlo no reactiva proveedores ni pisa productos que ya fueron asignados.
