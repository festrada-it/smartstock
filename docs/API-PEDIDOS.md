# Contrato API — Pedidos (#24 y #25)

Publicado antes de implementar para habilitar el frontend #27. Depende de los
cambios core de PR #36, #37 y #38. JWT Bearer; JSON camelCase. ADMIN y VENDEDOR
pueden crear, consultar, confirmar, enviar, entregar y pactar fecha. Cancelar
requiere ADMIN. Sin token: 401; rol no autorizado:
403. Errores: `{"error":"mensaje en español"}`.

| Método | Ruta | Resultado |
|---|---|---|
| POST | `/api/pedidos` | 201, pedido PENDIENTE con el stock reservado; 400 y venta perdida si falta stock |
| GET | `/api/pedidos?estado=&clienteId=&desde=&hasta=&numeroOrden=&page=0&size=50` | 200, página de pedidos |
| GET | `/api/pedidos/{id}` | 200, cabecera y detalle completos |
| POST | `/api/pedidos/{id}/confirmar` | 200, pedido CONFIRMADO; no mueve stock |
| POST | `/api/pedidos/{id}/enviar` | 200, pedido ENVIADO |
| POST | `/api/pedidos/{id}/entregar` | 200, pedido ENTREGADO con factura |
| POST | `/api/pedidos/{id}/cancelar` | 200, pedido CANCELADO; solo ADMIN |
| PUT | `/api/pedidos/{id}/fecha-envio` | 200, fecha pactada/cambiada |

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
  "items":[{"productoId":1,"producto":"Arroz","marca":"Ejemplo","cantidad":2,"precioUnitario":100.00,"subtotal":200.00}],
  "advertencias":[]
}
```

POST, GET individual y confirmar devuelven esta cabecera y detalle. `fecha` usa
ISO local `yyyy-MM-ddTHH:mm:ss[.fracción]`, sin offset. `advertencias` siempre se
incluye: lista vacía fuera de crear o si no hay avisos. Crear informa los productos
cuyo stock final sea menor o igual al mínimo. El precio de catálogo se guarda
al crear y no cambia al confirmar aunque cambie el catálogo. Nombre y marca son
los actuales. Moneda con dos decimales: total = subtotal × (1 − descuento / 100),
redondeado HALF_UP; importeDescuento = subtotal − total para conservar la
identidad monetaria. conDescuento se calcula como descuentoPct > 0.

Estado: PENDIENTE, CONFIRMADO, ENVIADO, ENTREGADO o CANCELADO. Transiciones:
PENDIENTE → CONFIRMADO → ENVIADO → ENTREGADO; PENDIENTE/CONFIRMADO → CANCELADO. La entrega se calcula: ENTREGADO →
ENTREGADA; con fecha → PACTADA; sin fecha → SIN_PACTAR. No se aceptan estados,
precios, totales ni números como entrada editable. Factura null hasta entrega;
formato histórico `FAC-0001-NNNNNNNN`. Orden `OC-AAAA-NNNNNN`; secuencias de
PostgreSQL, ancho mínimo (puede crecer). Los huecos de secuencia son normales.

### Reserva y stock insuficiente (regla del 01-11-2026)

Crear bloquea los productos ordenados por id y valida **todas** las líneas antes
de escribir. Solo cuenta como disponible el stock de lotes **no vencidos**; un lote
que vence hoy todavía se puede vender. Con stock suficiente, el pedido queda
PENDIENTE, descuenta el stock con FEFO y registra un movimiento `VENTA` por lote
consumido. Ver `docs/API-LOTES.md`.

Si alguna línea no alcanza, responde 400, **no crea el pedido ni consume
numeración** y guarda una venta perdida por cada línea sin stock suficiente:

```json
{"error":"No hay stock suficiente","faltantes":[{"productoId":1,"producto":"Helado 1L","cantidadPedida":80,"disponible":10,"faltante":70}]}
```

Confirmar significa que el cliente aceptó la orden: bloquea el pedido, pasa a
CONFIRMADO y no mueve stock. Confirmar nuevamente, incluso concurrentemente,
devuelve 400. Recurso ausente: 404; entradas o transiciones inválidas: 400.

### Ventas perdidas

`POST /api/pedidos` y `POST /api/ventas` registran en `ventas_perdidas`: `fecha`,
`cliente_id`, `usuario_id`, `producto_id`, `cantidad_pedida`, `cantidad_disponible`
y `precio_unitario` (el del catálogo al rechazar). El registro persiste aunque se
rechace la operación. No hay rutas nuevas: analytics lee la tabla directamente.

- **Faltante** = `cantidad_pedida − cantidad_disponible`.
- **Ventas perdidas en $** = faltante × `precio_unitario`.
- **Fill Rate** = vendidas / (vendidas + perdidas). Vendidas son las líneas de
  pedidos CONFIRMADO, ENVIADO y ENTREGADO; perdidas, el faltante por fecha del rechazo.

## Listado

Envelope `{contenido:[pedido],pagina,tamanio,totalElementos,totalPaginas}` con las
mismas cabeceras y detalle. Página desde 0, tamaño 1..100 (50 por defecto), orden
fecha DESC, id DESC. Filtros opcionales combinados: estado exacto, clienteId
exacto, desde/hasta ISO inclusive por día, numeroOrden exacto (sin substring), recortado; vacío o solo espacios no filtra.
Rango de fechas invertido, estado desconocido o paginado inválido: 400. Página
fuera del total: contenido vacío conservando totales. Consulta paginada en DB,
sin paginar una colección en memoria.

## Compatibilidad y migración

`POST /api/ventas` conserva request y response existentes y crea directamente un
pedido CONFIRMADO, con descuento cero y EFECTIVO. Si no llega clienteId, usa el
cliente activo Consumidor final (se recupera o recrea si dejó de estar disponible).
Las respuestas legacy conservan detalle y datos del cliente; sigue la misma regla
de stock: rechaza con `faltantes` y registra la venta perdida, usando el mismo
cliente que usaría la venta. Desde #25, la anulación legacy delega en la misma
cancelación: acepta PENDIENTE y CONFIRMADO, devuelve el stock y rechaza
ENVIADO/ENTREGADO/CANCELADO. Conserva su DTO VentaResponse y requiere ADMIN; se
quitará cuando migre el frontend.

`08_pedidos.sql` se aplica después de 01..06, independientemente de 07. Es
idempotente: ventas históricas no anuladas → ENTREGADO con factura única;
anuladas → CANCELADO; conserva fechas y numeración al reejecutar. `anulada`
permanece sincronizada con CANCELADO por compatibilidad. Crea la tabla
`ventas_perdidas`. Las ventas históricas ENTREGADAS no pueden anularse con el
alias legacy; devuelve 400 por la transición.

`11_datos_fill_rate.sql` agrega tres escenarios demo por cliente, con ventas y
ventas perdidas que dan tasas de 99%, 96% y 90%, sin asumir IDs ni modificar
historia ajena. Es idempotente y corre después de 08 (y del 10 si está presente);
identifica sus clientes/productos por nombre/SKU y conserva numeración al reejecutar.

Analytics (#26) debe filtrar estados: un pedido PENDIENTE ya tiene cantidad e
importes, pero no es una venta aceptada por el cliente. Se conserva `anulada`,
pero por sí sola ya no distingue reservas de ventas.

## Seguimiento y entrega (#25)

Publicado antes de implementar #25; depende de PR #52 (y de su base #36/#37/#38).
Todos estos endpoints retornan la misma cabecera completa y detalle PedidoResponse
publicada arriba, con advertencias vacías. No requieren cuerpo salvo fecha-envio.
No generan PDF ni documentos adicionales; el número y DTO permiten imprimir desde
el frontend.

- **Enviar**: solo CONFIRMADO. Cambia a ENVIADO y conserva la fecha ya pactada;
  si no había fecha, asigna hoy en America/Argentina/Buenos_Aires. Entrega PACTADA.
  No modifica stock, precios ni importes.
- **Entregar**: solo ENVIADO. Cambia a ENTREGADO/ENTREGADA y asigna una sola factura
  de la secuencia PostgreSQL ya creada por SQL08: `FAC-0001-NNNNNNNN` (ancho mínimo8).
  Número único incluso entre pedidos concurrentes. Repetir o competir por entregar
  el mismo pedido da una respuesta200 y otra400, conserva su factura y no consume
  una segunda factura. La factura conserva las líneas e importes del pedido.
  Los huecos de secuencia por rollback son normales.
- **Cancelar**: solo ADMIN, desde PENDIENTE o CONFIRMADO. Devuelve el stock reservado
  a los lotes de origen, incluso con productos dados de baja. Registra AJUSTE por
  fracción de lote y observación exacta
  `Cancelación de pedido OC-...`, con el usuario del JWT. Cambia a CANCELADO y
  anulada=true; conserva precios, importes, detalle y fecha. ENVIADO, ENTREGADO y
  CANCELADO dan400. Cancelaciones concurrentes/alias legacy devuelven una sola vez.
- **Fecha de envío**: `PUT` con `{"fechaEnvio":"2026-11-04"}` obligatorio, ISO
  `yyyy-MM-dd`, desde PENDIENTE o CONFIRMADO. Fecha anterior a hoy en Argentina,
  nula, ausente o inválida da400. Conserva el estado y pasa entrega a PACTADA.
  ENVIADO, ENTREGADO y CANCELADO dan400; no se acepta borrar la fecha con null.

Todas las mutaciones bloquean la fila del pedido. Cancelar bloquea luego productos
por id ascendente y después sus lotes, y restaura en una sola transacción; stock fuera del rango entero
revierten todo con400. Recurso inexistente404. Sin JWT401; vendedor cancelando403
JSON español. EstadoEntrega sigue calculado y persistido automáticamente (D7).

El seguimiento usa columnas, trigger y secuencias de08 sin migración adicional.
FEFO requiere07 y conserva `consumos_lote` al cancelar para auditoría. Los
detalles históricos sin consumos restituyen al lote de apertura; ver el límite
y el orden de consumo en `docs/API-LOTES.md`.
