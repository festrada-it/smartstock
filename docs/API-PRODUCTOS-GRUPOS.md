# Contrato API — Productos y grupos (#16)

Depende de proveedores #14. Se publica antes de implementar. JWT y permisos
existentes: lectura ADMIN/VENDEDOR; escritura ADMIN. Categorías pasa a llamarse
grupos en la interfaz; las rutas y `categoriaId` se conservan.

## Productos

`GET /api/productos?q=&categoriaId=&proveedorId=` suma el filtro opcional por
proveedor. Las respuestas de listar, obtener, crear, editar y comprar conservan
todos sus campos y agregan `marca`, `proveedorId`, `proveedor` (nombre) y `grupo`
(nombre del grupo, alias de `categoriaNombre`). Sin asociación, son null.

POST y PUT `/api/productos` reciben los campos anteriores más `marca` (opcional,
hasta 100 caracteres) y `proveedorId` (opcional, proveedor activo existente).
`stockMinimo` omitido o null se convierte en **30**; debe ser >= 0 si se envía.
`categoriaId` sigue seleccionando el grupo. No se envían `proveedor` ni `grupo`
como objetos de escritura.

Compatibilidad del PUT: si `marca` o `proveedorId` se omiten o son null, se
conserva el valor actual. Enviar `marca: ""` borra la marca intencionalmente.
Un `proveedorId` no nulo reemplaza el proveedor por uno activo. El contrato no
incluye todavía una operación para quitar una asociación de proveedor.

```json
{"sku":"LACT-900","nombre":"Leche 1L","categoriaId":4,"marca":"SanCor","proveedorId":4,"precio":1800,"costo":1200,"stockActual":45,"stockMinimo":30}
```

Estado de stock: SIN_STOCK si es cero; BAJO_MINIMO si es menor o igual al mínimo;
OK si es mayor. Es la misma regla de analytics y del panel de alertas.
Proveedor o grupo inexistente: 404; negativos/textos
demasiado largos: 400 con `{"error":"mensaje en español"}`.

## Grupos

GET `/api/categorias`: lista ordenada de
`{id,nombre,diasAlertaVencimiento,umbralRotacion}`.
POST `/api/categorias` (201) y PUT `/api/categorias/{id}` (200, ADMIN) reciben:

```json
{"nombre":"Lácteos","diasAlertaVencimiento":15,"umbralRotacion":0.10}
```

Nombre obligatorio hasta 100 caracteres; días > 0; umbral entre 0 y 1
(0.10 significa 10%). Días omitidos/null: 60; umbral omitido/null: 0.10.
Nombre duplicado: 400; id inexistente: 404; sin token: 401; sin permiso: 403.
La creación anterior que solo enviaba nombre sigue funcionando.

## Migración y compatibilidad

Aplicar `database/init/05_grupos_stock_minimo.sql` después de 04 y antes del
nuevo core. Configura los grupos Lácteos (15), Frescos (10), Almacén (60),
Bebidas (90), Limpieza (120), pasa el mínimo inicial de los ocho SKU originales
a 30 y agrega productos de ejemplo con marca/proveedor y 90 días de ventas.
El script es idempotente; reejecutarlo no duplica ventas ni repone bajas ni
sobrescribe configuraciones editadas después de la primera aplicación.

Analytics conserva su contrato de `/api/predicciones`; los productos nuevos
tienen historial para entrenar. No se agrega ninguna mejora al predictor.
