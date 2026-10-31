# Contrato API — Clientes (#18)

Publicado antes de implementar. Depende de proveedores #14 y productos/grupos
#16. JWT Bearer; JSON camelCase; lecturas ADMIN y VENDEDOR, escrituras ADMIN.

| Método | Ruta | Resultado |
|---|---|---|
| GET | `/api/clientes?q=` | 200, clientes activos por nombre/id, búsqueda opcional por nombre |
| GET | `/api/clientes/{id}` | 200, datos de un cliente activo |
| POST | `/api/clientes` | 201, cliente creado |
| PUT | `/api/clientes/{id}` | 200, reemplaza datos editables |
| DELETE | `/api/clientes/{id}` | 204, baja lógica, conserva ventas |
| GET | `/api/clientes/{id}/pedidos?page=0&size=50` | 200, historial paginado, incluye anuladas |
| GET | `/api/clientes/{id}/productos` | 200, compras agregadas sin ventas anuladas |

POST/PUT:

```json
{"nombre":"Día Express","direccion":"Av. Corrientes 1800","telefono":"1145555000","calidad":"BUENA","clasificacion":5}
```

Respuesta: los mismos campos más `id` y `activo`. Nombre obligatorio, hasta
150 caracteres; dirección opcional hasta 255; teléfono opcional hasta 50.
Calidad obligatoria: BUENA, REGULAR o MALA (cadena, no número). Clasificación
entera de 1 a 5. Textos recortados. Datos inválidos: 400 JSON español.

Historial: `{contenido:[{id,fecha,total,anulada}],pagina,tamanio,totalElementos,totalPaginas}`.
Página desde 0; tamaño de 1 a 100, por defecto 50; valores fuera de rango: 400.
Orden `fecha DESC, id DESC`. Fecha ISO local `yyyy-MM-ddTHH:mm:ss[.fracción]`,
sin offset, como las ventas actuales. Una página fuera del total devuelve
contenido vacío y conserva los totales. No se confunden estos registros con
el futuro recurso `/api/pedidos`: todavía son ventas.

Productos: `[{productoId,producto,marca,unidadesCompradas,ultimaCompra}]`,
ordenados por unidades descendentes y productoId. Se calcula desde detalle de
ventas no anuladas; incluye productos históricos dados de baja. Marca/nombre
son los actuales del catálogo. Un cliente sin historial devuelve listas vacías.
Cliente inexistente o inactivo: 404 en todos sus endpoints. La baja no borra
ventas ni impide que se vea su nombre en una venta conservada.

## Compatibilidad de ventas

`POST /api/ventas` acepta `clienteId` opcional, de un cliente activo existente.
Si se omite o es null, la venta conserva cliente null para mantener la
compatibilidad del frontend actual. El selector del frontend puede elegir
el registro `Consumidor final` consultando `/api/clientes`, sin asumir su id.

`VentaResponse` conserva los campos existentes y agrega `clienteId`, `cliente`
(nombre), `clienteCalidad` y `clienteClasificacion`; son null si no se asoció
cliente. También aparecen al anular y en `/api/ventas/hoy`.

Sin token: 401; escritura por VENDEDOR: 403; id ausente/inactivo: 404. Salvo
el 403 hasta #13, errores con `{"error":"mensaje en español"}`.

## Migración

Aplicar `06_clientes.sql` después de 04/05 y antes del nuevo core. Es
idempotente. Agrega los cinco clientes de ejemplo Día Express, Carrefour
Express, Chino Almagro, Jumbo Express y Consumidor final. Solo al crear
`ventas.cliente_id` reparte el historial existente entre esos clientes;
reejecutarlo no reasigna ventas cargadas después ni duplica los ejemplos.
