# Contrato de parámetros y datos de inventario — #56

## Alcance

El script nuevo `database/init/10_parametros_y_datos_inventario.sql` agrega datos de desarrollo para los dashboards de inventario (§10 del plan). No agrega endpoints ni modifica Java, Python, rutas o scripts anteriores. La pantalla existente «Usuarios y parámetros» ya lista y permite editar todas las claves.

| Clave | Valor inicial | Descripción |
|---|---:|---|
| dsi_rojo_bajo | 7 | Días de inventario por debajo de los cuales hay riesgo de quiebre |
| dsi_amarillo_alto | 45 | Días de inventario desde los cuales hay dinero atrapado, nivel amarillo |
| dsi_rojo_alto | 90 | Días de inventario desde los cuales hay dinero atrapado, nivel rojo |
| dias_sin_ventas | 90 | Ventana en días para detectar productos con stock y sin ventas |
| fill_rate_objetivo | 95 | Objetivo porcentual de unidades entregadas sobre solicitadas |
| riesgo_amarillo_pct | 5 | Porcentaje del valor del stock en riesgo desde el cual se muestra amarillo |
| riesgo_rojo_pct | 10 | Porcentaje del valor del stock en riesgo por encima del cual se muestra rojo |

Las claves existentes se conservan, incluidos sus valores y descripciones personalizados.

## Datos y medidas esperadas

Los productos se identifican por SKU `DEMO-INV-*`, marca «Demo Inventario» y un proveedor y grupos con prefijo «Demo inventario». Solo los productos insertados por esta ejecución reciben historia. No se modifican existencias, ventas ni movimientos previos. Los ejemplos se anclan a la fecha de Argentina (`America/Argentina/Buenos_Aires`) al crearse; ejecutar otra vez no rejuvenece su historia.

Se incluyen dos productos rápidos (ventas diarias), dos lentos (ventas espaciadas), dos equilibrados/con excedente y dos con existencias y sin ventas. Todos tienen grupo, marca y proveedor. Los grupos propios permiten comprobar rojo bajo, verde, amarillo y rojo alto sin depender de la semilla aleatoria de otros grupos.

- DSI = valor del stock actual a costo / (unidades vendidas × costo / días del período), para 30 y 90 días, excluyendo ventas anuladas.
- Colores iniciales: rojo si DSI < 7 o DSI ≥ 90; amarillo si 45 ≤ DSI < 90; verde en el resto. Sin ventas: DSI indefinido, rojo.
- Rotación financiera = costo de ventas / promedio diario del valor del stock reconstruido desde movimientos; mensual = rotación × 30 / días; anualizada = rotación × 365 / días. No lleva semáforo.
- Baja rotación mantiene la regla de #28, en unidades y ventana fija de 30 días, con el umbral del grupo. Se verifican ejemplos con ventas escasas respecto al stock.
- Una entrada inicial y las salidas por venta deben reconstruir exactamente el stock actual, sin existencias históricas negativas. Cada venta tiene total, detalle y movimiento por la misma cantidad y fecha.

## Compatibilidad e idempotencia

Requiere los scripts `01` a `06`; no requiere pedidos (`08`) ni lotes. Si existe `detalle_venta.cantidad_solicitada`, se registra igual a la entregada porque estas ventas no son parciales. Si existen las columnas de pedidos, las ventas se crean ENTREGADO, con subtotal/total iguales, descuento cero y números únicos obtenidos de las secuencias existentes. Si existe el esquema de lotes (`07`), cada producto nuevo recibe un lote con existencia inicial, consumos históricos y disponible igual al stock actual; sus movimientos referencian ese lote. La fecha de entrega es el comienzo de la historia y el vencimiento queda desconocido. Sin `07`/`08`, no depende de sus tablas o columnas. Ejecutar `10` dos veces no duplica proveedores, grupos, productos, ventas o movimientos y no altera parámetros personalizados. Las escrituras son atómicas.

La prueba SQL se ejecuta sobre una base PostgreSQL 16 descartable: primero falla sobre `01..06`, luego pasa con `10`; verifica los dos períodos, historia coherente, sin ventas e idempotencia. La integración `01..11` se verifica cuando estén disponibles los scripts de las otras tareas. No hay rutas nuevas para incorporar a `ControllersTest`.

## Resultados de referencia al crear la demo

| Grupo | Productos | Stock (unidades) | Ventas 30 / 90 días | DSI 30 y 90 | Color |
|---|---:|---:|---:|---:|---|
| Demo inventario - rápida | 2 | 40 | 600 / 1800 | 2 | Rojo bajo |
| Demo inventario - equilibrada | 1 | 40 | 60 / 180 | 20 | Verde |
| Demo inventario - excedente | 1 | 120 | 60 / 180 | 60 | Amarillo |
| Demo inventario - lenta | 2 | 220 | 4 / 12 | 1650 | Rojo alto |
| Demo inventario - sin ventas | 2 | 120 | 0 / 0 | Indefinido | Rojo |

Todos usan costo 100 y precio 150. Los dos lentos venden una unidad cada 15 días y su rotación de 30 días es inferior al umbral 0,10. La historia contiene 372 ventas con detalle y movimiento de salida, más ocho entradas iniciales. Estos valores son los de la carga inicial; las operaciones posteriores y el paso del tiempo modifican los indicadores.

Verificación reproducible en una base descartable inicializada: `psql -v ON_ERROR_STOP=1 -f database/tests/10_inventario_idempotencia.sql`. La prueba comprueba ambas ventanas, los tres colores, baja rotación, sin ventas, saldos, lotes/pedidos opcionales y el contenido completo de todas las tablas antes/después de repetir el script con un parámetro personalizado. No ejecutar el verificador en una base de usuario: es una prueba con datos fijos de la demo.
