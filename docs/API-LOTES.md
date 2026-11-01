# Contrato API y datos — Lotes (#20 / #21)

Contrato compartido con analytics #22 y frontend #23.
Requiere clientes/grupos y el historial paginado #8. Los lotes corresponden al
core: analytics solamente lee estas tablas y no crea ni migra el esquema.

## Ingreso de mercadería

`POST /api/compras`, ADMIN y VENDEDOR, mantiene los campos anteriores y acepta:

```json
{"productoId":1,"cantidad":20,"costoUnitario":1200.00,"proveedorId":1,"fechaVencimiento":"2026-12-15","fechaEntrega":"2026-10-31","observacion":"Ingreso de mercadería"}
```

Cantidad entera positiva; costo no negativo, opcional (se usa el costo actual
del producto si se omite). Si se envía costo, actualiza también el costo del
catálogo como antes. Proveedor opcional, por defecto el principal del producto;
si se selecciona uno, debe existir y estar activo. Sin proveedor principal ni
selección explícita se admite null. Fecha de vencimiento opcional/null (sin
vencimiento), no anterior a hoy. Fecha de entrega opcional, por defecto hoy.
El costo se redondea a dos decimales con HALF_UP y admite hasta 9999999999,99.
Fechas ISO `yyyy-MM-dd`; hoy se interpreta en America/Argentina/Buenos_Aires.
Un incremento que exceda el máximo entero de stock devuelve 400 y revierte todo.

Resultado 201: `ProductoResponse` con el stock actualizado y el nuevo campo
`proximoVencimiento` (fecha mínima entre lotes con disponible positivo, null
si ninguno tiene vencimiento). Crear lote, incrementar stock y registrar
COMPRA con loteId ocurre dentro de la misma transacción. Se conserva la
compatibilidad de compras que solo envían productoId/cantidad.

## Consulta de lotes

`GET /api/productos/{id}/lotes?todos=false`, ambos roles.
200 con lista por fecha de vencimiento ascendente (null al final), luego id:

```json
[{"id":1,"productoId":1,"proveedorId":1,"proveedor":"Distribuidora del Sur","cantidadInicial":120,"cantidadDisponible":120,"fechaVencimiento":"2026-11-30","fechaEntrega":"2026-10-31","costoUnitario":3100.00,"esApertura":true}]
```

Por defecto solo disponible > 0; `todos=true` incluye agotados. Producto
inexistente: 404. La baja lógica de un producto no borra sus lotes ni historial.
Historial paginado de movimientos agrega `loteId`, null para el histórico sin
asignación. Los campos previos se conservan. Sin JWT:401; errores de entrada:
400 JSON español; proveedor/producto inexistente o proveedor inactivo:404.

## Esquema para analytics

`database/init/07_lotes.sql` es incremental/idempotente y contiene:

| Tabla | Columnas |
|---|---|
| `lotes` | `id` bigint PK, `producto_id` FK, `proveedor_id` FK nullable, `cantidad_inicial` int >=0, `cantidad_disponible` int >=0, `fecha_vencimiento` date nullable, `fecha_entrega` date, `costo_unitario` numeric >=0, `creado_en` timestamp, `es_apertura` boolean |
| `consumos_lote` | `id` bigint PK, `detalle_venta_id` FK, `lote_id` FK, `cantidad` int >0 |
| `movimientos_stock` | agrega `lote_id` FK nullable |

Para vencimientos: unir lotes → productos → categorias para obtener nombre,
marca, grupo y `dias_alerta_vencimiento`. No se copia la regla del grupo al
lote. Para proveedor/lead time usar `lotes.proveedor_id` en cada ingreso;
`productos.proveedor_id` sigue siendo el principal del catálogo.

## Apertura, integridad y despliegue

La migración crea un lote de apertura por producto con su stock actual,
proveedor/costo y fecha estimada según el grupo. Es una estimación explícita
para los datos existentes, no un vencimiento histórico conocido. Las fechas
de ejemplo se escalonan para mostrar casos dentro y fuera de la alerta.
Reejecutar no duplica lotes ni modifica el stock guardado.

Invariante: `productos.stock_actual = SUM(lotes.cantidad_disponible)`.
Ninguna operación permite cantidades disponibles negativas. Los ajustes de
stock del catálogo mantienen esta igualdad y registran su diferencia como
AJUSTE. Los productos creados desde el catálogo también reciben apertura; los
fixtures/importaciones sin lotes la reciben al primer cambio de stock, usando
el stock anterior a la operación y sin inventar una fecha de vencimiento.
Las ediciones y bajas de catálogo bloquean primero la fila del producto;
las operaciones de stock bloquean después los lotes por id.

## Consumo FEFO y devolución (#21)

`POST /api/ventas` y `POST /api/pedidos/{id}/confirmar` mantienen sus rutas,
roles y JSON. Toda cantidad entregada positiva consume lotes con disponible
mayor que cero, por `fecha_vencimiento ASC NULLS LAST, id ASC`. Se incluyen
vencimientos pasados: no existe una política aprobada que los excluya. Los
renglones entregados con cantidad cero no consumen ni generan movimientos.
La confirmación informa una advertencia de stock mínimo por producto, aunque
la entrega tome mercadería de varios lotes.

Primero se bloquean todos los productos por id ascendente; luego sus lotes en
ese mismo orden de productos y orden FEFO. Cada fracción se guarda en
`consumos_lote` con detalle de venta, lote y cantidad positiva, y genera un
movimiento `VENTA` con cantidad negativa y `loteId`. Los detalles se persisten
antes de sus consumos por la clave foránea; falta de stock o inconsistencia de
lotes revierte venta, consumos, movimientos y stock en la misma transacción.

La anulación de ventas y cancelación de pedidos confirmados/parciales devuelve
cada fracción al lote original y genera `AJUSTE` positivo con `loteId`. Se
conservan los consumos para auditoría. El bloqueo de venta impide una segunda
devolución. Pedidos pendientes no restituyen stock. Los detalles históricos
sin consumos (ventas anteriores a 07 o al despliegue FEFO) devuelven su cantidad
al lote de apertura: no se puede reconstruir su origen. La apertura admite
disponible mayor al inicial por esas restituciones.

Los ajustes del catálogo siguen distribuyéndose por id, sin consumos de venta;
compras y catálogo conservan `SUM(lotes.cantidad_disponible)=stock_actual`.
No hay rutas nuevas ni cambios de campos JSON; `loteId` ya forma parte del
historial paginado de movimientos.

Dependencias de integración: PR #52 (pedidos y confirmación parcial), #54
(lotes/compras) y #65 (cancelación de pedidos). El contrato de pedidos define
`cantidadSolicitada` y `cantidad` entregada; FEFO descuenta esta última.

En una base existente aplicar 07, sin borrar volúmenes. Si 08 de pedidos ya
está aplicado, no se vuelve a ejecutar para instalar lotes. En bases nuevas los
scripts mantienen su numeración: 07 se ejecuta antes de 08.


## Verificación reproducible

En una base exclusiva de pruebas inicializada con 01..06, ejecutar
`psql -v ON_ERROR_STOP=1 -f database/tests/07_lotes_test.sql`. Aplica 07 dos veces
y verifica que el stock y las aperturas no cambien, que haya ejemplos dentro y
fuera de alerta, y que la base rechace disponible negativo y apertura duplicada.
Las pruebas HTTP usan PostgreSQL 16 con `SMARTSTOCK_INTEGRACION=true` y
`cd core-service && ./mvnw -B clean verify`.
