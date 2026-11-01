# Contrato API — Pedidos (#24)

Publicado antes de implementar para habilitar el frontend #27. Depende de los
cambios core de PR #36, #37 y #38. JWT Bearer; JSON camelCase. ADMIN y VENDEDOR
pueden crear, consultar y confirmar pedidos. Sin token: 401; rol no autorizado:
403. Errores: `{"error":"mensaje en español"}`.

| Método | Ruta | Resultado |
|---|---|---|
| POST | `/api/pedidos` | 201, pedido PENDIENTE sin modificar stock |
| GET | `/api/pedidos?estado=&clienteId=&desde=&hasta=&numeroOrden=&page=0&size=50` | 200, página de pedidos |
| GET | `/api/pedidos/{id}` | 200, cabecera y detalle completos |
| POST | `/api/pedidos/{id}/confirmar` | 200, pedido CONFIRMADO completo/parcial y advertencias |

## Creación y respuesta

```json
{"clienteId":1,"formaPago":"TRANSFERENCIA","descuentoPct":10,"fechaEnvio":"2026-11-04","items":[{"productoId":1,"cantidad":2}]}
```

Cliente obligatorio, existente y activo. Forma de pago obligatoria: EFECTIVO,
TRANSFERENCIA, TARJETA o CUENTA_CORRIENTE. Descuento opcional (0), entre 0 y 20
inclusive. Al menos un ítem; IDs y cantidades son enteros positivos. Un producto
repetido se suma en una línea; un producto inexistente o inactivo devuelve 404.
La fecha de envío es opcional, ISO `yyyy-MM-dd`, y no anterior al día del pedido.
Fechas nuevas: America/Argentina/Buenos_Aires; fechas históricas se conservan.

```json
{
  "id":123,"numeroOrden":"OC-2026-000123","fecha":"2026-10-31T15:30:00",
  "clienteId":1,"cliente":"Día Express","clienteCalidad":"BUENA",
  "clienteClasificacion":5,"formaPago":"TRANSFERENCIA","descuentoPct":10,
  "conDescuento":true,"subtotal":0.00,"importeDescuento":0.00,"total":0.00,
  "fechaEnvio":"2026-11-04","estado":"PENDIENTE","estadoEntrega":"PACTADA",
  "numeroFactura":null,
  "items":[{"productoId":1,"producto":"Arroz","marca":"Ejemplo","cantidad":0,"cantidadSolicitada":2,"cantidadEntregada":0,"faltante":2,"precioUnitario":100.00,"subtotal":0.00}],
  "advertencias":[]
}
```

POST, GET individual y confirmar devuelven esta cabecera y detalle. `fecha` usa
ISO local `yyyy-MM-ddTHH:mm:ss[.fracción]`, sin offset. `advertencias` siempre se
incluye: lista vacía fuera de confirmar o si no hay avisos. Confirmar informa el
faltante por línea y los productos cuyo stock final sea menor o igual al mínimo. El precio de catálogo se guarda
al crear y no cambia al confirmar aunque cambie el catálogo. Nombre y marca son
los actuales. Moneda con dos decimales: total = subtotal × (1 − descuento / 100),
redondeado HALF_UP; importeDescuento = subtotal − total para conservar la
identidad monetaria. conDescuento se calcula como descuentoPct > 0.

Estado: PENDIENTE, CONFIRMADO, ENVIADO, ENTREGADO o CANCELADO. Solo se implementa
PENDIENTE → CONFIRMADO en esta tarea. La entrega se calcula: ENTREGADO →
ENTREGADA; con fecha → PACTADA; sin fecha → SIN_PACTAR. No se aceptan estados,
precios, totales ni números como entrada editable. Factura null hasta entrega;
formato histórico `FAC-0001-NNNNNNNN`. Orden `OC-AAAA-NNNNNN`; secuencias de
PostgreSQL, ancho mínimo (puede crecer). Los huecos de secuencia son normales.

Confirmar bloquea el pedido y luego los productos ordenados por id. Cada línea
entrega `min(cantidadSolicitada, stock disponible)` y descuenta solo esa cantidad.
`cantidad` se conserva como alias de `cantidadEntregada`. Al crear se guarda lo
solicitado, con cantidadEntregada=0 y subtotal/total=0. Al confirmar se recalculan
subtotal, descuento y total sobre lo entregado, usando precios pactados. El
faltante = cantidadSolicitada − cantidadEntregada queda registrado sin backorder.
Una confirmación parcial cambia a CONFIRMADO igual que una completa. Líneas sin
entrega no generan movimientos. Si ninguna línea puede entregar, devuelve400 y
mantiene PENDIENTE sin cambios. Un error en otra línea revierte toda la operación. Confirmar nuevamente, incluso concurrentemente, devuelve 400 y descuenta
una sola vez. FEFO se agrega en #21; esta tarea no depende de lotes/SQL 07.
Recurso ausente: 404; entradas o transiciones inválidas: 400.

## Listado

Envelope `{contenido:[pedido],pagina,tamanio,totalElementos,totalPaginas}` con las
mismas cabeceras y detalle. Página desde 0, tamaño 1..100 (50 por defecto), orden
fecha DESC, id DESC. Filtros opcionales combinados: estado exacto, clienteId
exacto, desde/hasta ISO inclusive por día, numeroOrden exacto (sin substring).
Rango de fechas invertido, estado desconocido o paginado inválido: 400. Página
fuera del total: contenido vacío conservando totales. Consulta paginada en DB,
sin paginar una colección en memoria.

## Compatibilidad y migración

`POST /api/ventas` conserva request y response existentes y crea directamente un
pedido CONFIRMADO, con descuento cero y EFECTIVO. Si no llega clienteId, usa el
cliente activo Consumidor final (se recupera o recrea si dejó de estar disponible).
Las respuestas legacy conservan detalle y datos del cliente; las ventas legacy
mantienen su validación de stock completo, con solicitada=entregada. La anulación legacy
solo permite ventas CONFIRMADAS; no devuelve stock de PENDIENTES ni de pedidos
ENVIADOS/ENTREGADOS. Los nuevos endpoints de cancelación, envío, entrega y cambio
de fecha corresponden a #25.

`08_pedidos.sql` se aplica después de 01..06, independientemente de 07. Es
idempotente: ventas históricas no anuladas → ENTREGADO con factura única;
anuladas → CANCELADO; conserva fechas y numeración al reejecutar. `anulada`
permanece sincronizada con CANCELADO por compatibilidad. Agrega
`detalle_venta.cantidad_solicitada` (>0), migra historia con solicitada=entregada
y reemplaza el CHECK de cantidad por >=0 sin editar01. Los PENDIENTES anteriores
conservan la solicitada y pasan a entregada0/importe0. Reruns preservan solicitudes
y entregas ya confirmadas. Las ventas históricas ENTREGADAS no pueden anularse
con el alias legacy; devuelve400 por la transición.

`11_datos_fill_rate.sql` agrega tres escenarios demo por cliente, con tasas99%,
96% y90%, sin asumir IDs ni modificar historia ajena. Es idempotente y corre
después de08 (y del10 si está presente); identifica sus clientes/productos por
nombre/SKU y conserva numeración al reejecutar.

Analytics (#26) debe filtrar estados: un pedido PENDIENTE no representa demanda.
Se conserva `anulada`, pero por sí sola ya no distingue reservas de pedidos.
