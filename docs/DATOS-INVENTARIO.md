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

Los productos se identifican por SKU `DEMO-INV-*`, marca «Demo Inventario» y un proveedor y grupos con prefijo «Demo inventario». Solo los productos insertados por esta ejecución reciben historia. No se modifican existencias, ventas ni movimientos previos. Los ejemplos se anclan a `CURRENT_DATE` al crearse; ejecutar otra vez no rejuvenece su historia.

Se incluyen dos productos rápidos (ventas diarias), dos lentos (ventas espaciadas) y dos con existencias y sin ventas. Todos tienen grupo, marca y proveedor. Los grupos propios permiten comprobar rojo bajo, verde y amarillo/rojo alto sin depender de la semilla aleatoria de otros grupos.

- DSI = valor del stock actual a costo / (unidades vendidas × costo / días del período), para 30 y 90 días, excluyendo ventas anuladas.
- Colores iniciales: rojo si DSI < 7 o DSI ≥ 90; amarillo si 45 ≤ DSI < 90; verde en el resto. Sin ventas: DSI indefinido, rojo.
- Rotación financiera = costo de ventas / promedio diario del valor del stock reconstruido desde movimientos; mensual = rotación × 30 / días; anualizada = rotación × 365 / días. No lleva semáforo.
- Baja rotación mantiene la regla de #28, en unidades y ventana fija de 30 días, con el umbral del grupo. Se verifican ejemplos con ventas escasas respecto al stock.
- Una entrada inicial y las salidas por venta deben reconstruir exactamente el stock actual, sin existencias históricas negativas. Cada venta tiene total, detalle y movimiento por la misma cantidad y fecha.

## Compatibilidad e idempotencia

Requiere los scripts `01` a `06`; no requiere pedidos (`08`) ni lotes. Si existe `detalle_venta.cantidad_solicitada`, se registra igual a la entregada porque estas ventas no son parciales. No crea pedidos ni lotes y no depende de sus tablas. Ejecutar `10` dos veces no duplica proveedores, grupos, productos, ventas o movimientos y no altera parámetros personalizados. Las escrituras son atómicas.

La prueba SQL se ejecuta sobre una base PostgreSQL 16 descartable: primero falla sobre `01..06`, luego pasa con `10`; verifica los dos períodos, historia coherente, sin ventas e idempotencia. La integración `01..11` se verifica cuando estén disponibles los scripts de las otras tareas. No hay rutas nuevas para incorporar a `ControllersTest`.
