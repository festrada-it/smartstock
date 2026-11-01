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
| POST | `/api/pedidos/{id}/confirmar` | 200, pedido CONFIRMADO y advertencias |

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
  "conDescuento":true,"subtotal":200.00,"importeDescuento":20.00,"total":180.00,
  "fechaEnvio":"2026-11-04","estado":"PENDIENTE","estadoEntrega":"PACTADA",
  "numeroFactura":null,
  "items":[{"productoId":1,"producto":"Arroz","marca":"Ejemplo","cantidad":2,"precioUnitario":100.00,"subtotal":200.00}]
}
```

POST, GET individual y confirmar devuelven esta cabecera y detalle. Confirmar
agrega `advertencias`, una lista de textos por producto cuyo stock final sea
menor o igual al mínimo (lista vacía si no hay). El precio de catálogo se guarda
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

Confirmar bloquea el pedido y luego los productos ordenados por id. Descuenta
stock y registra movimientos en la misma transacción; stock insuficiente revierte
todo. Confirmar nuevamente, incluso concurrentemente, devuelve 400 y descuenta
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
Las respuestas legacy conservan detalle y datos del cliente. La anulación legacy
solo permite ventas CONFIRMADAS; no devuelve stock de PENDIENTES ni de pedidos
ENVIADOS/ENTREGADOS. Los nuevos endpoints de cancelación, envío, entrega y cambio
de fecha corresponden a #25.

`08_pedidos.sql` se aplica después de 01..06, independientemente de 07. Es
idempotente: ventas históricas no anuladas → ENTREGADO con factura única;
anuladas → CANCELADO; conserva fechas y numeración al reejecutar. `anulada`
permanece sincronizada con CANCELADO por compatibilidad.

Analytics (#26) debe filtrar estados: un pedido PENDIENTE no representa demanda.
Se conserva `anulada`, pero por sí sola ya no distingue reservas de pedidos.
