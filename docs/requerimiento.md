# Requerimiento — SmartStock (Grupo 7)

## El caso de negocio

SmartStock se adapta a cualquier PyME que compre y revenda mercadería. Para la demo y la presentación usamos como ejemplo un **distribuidor mayorista (trade)**:

- **Compramos** mercadería a **proveedores**.
- **Vendemos** a **supermercados chicos**: Día Express, Carrefour Express, Jumbo Express y Chino Almagro.

**Objetivo:** saber **antes** cuándo nos vamos a quedar sin stock, para no fallarles a nuestros clientes, y evitar comprar de más los productos que no rotan.

## Cómo se divide el sistema

Cada sección aporta datos al monitor predictivo para que las predicciones y alertas sean más precisas.

### 📦 Productos y stock
Todo nuestro universo de productos.

| Campo | Detalle |
|---|---|
| SKU | Código único |
| Producto | Nombre |
| Marca | |
| Categoría / grupo | Cada grupo tiene sus reglas (días de aviso de vencimiento y umbral de rotación) |
| Proveedor | Proveedor principal |
| Precio | Precio de venta |
| Costo | Costo de compra |
| Stock | Stock actual |
| Mínimo | Stock mínimo de seguridad: **30 por defecto, editable por producto** |
| Estado | OK / Bajo mínimo |
| Próximo vencimiento | El del lote que vence antes |

**Vencimiento y fecha de entrega del proveedor** se cargan en cada **ingreso de mercadería (lote)**, porque un mismo producto puede tener cajas que vencen en fechas distintas. Las ventas descuentan primero el lote que vence antes (criterio **FEFO**).

**Baja de productos:** el botón Eliminar da de baja el producto sin borrar su historial. El ADMIN puede ver los productos dados de baja y **reactivarlos** (#69, #70).

### 🚚 Proveedores
A quiénes les compramos.

| Campo | Detalle |
|---|---|
| Nombre | |
| Dirección | |
| Teléfono | |
| Productos y marcas que vende | Se ven desde sus productos |
| Calidad | BUENA / REGULAR / MALA |
| Días de entrega | Tiempo promedio que tarda en entregar (lead time) |
| Clasificación | 1 a 5 estrellas |

Si un proveedor tiene **2 estrellas o menos, calidad MALA o tarda mucho**, el sistema avisa **con más anticipación** que hay que reponer.

### 🏪 Clientes
A quiénes les vendemos.

| Campo | Detalle |
|---|---|
| Nombre | Ej. Día Express, Carrefour Express, Chino Almagro, Jumbo Express |
| Dirección | |
| Teléfono | |
| Calidad | BUENA / REGULAR / MALA |
| Clasificación | 1 a 5 estrellas: ayuda a decidir **cuánto venderle** |
| Historial | Sus pedidos, y los productos y marcas que compra (se calculan desde los pedidos) |

### 🧾 Ventas → Pedidos
Cada venta a un cliente es un pedido.

| Campo | Detalle |
|---|---|
| Número de orden de compra | Autogenerado (`OC-AAAA-NNNNNN`) |
| Cliente | |
| Productos | Producto, cantidad, precio unitario y subtotal por línea |
| Precio total | Calculado con el descuento |
| Forma de pago | EFECTIVO / TRANSFERENCIA / TARJETA / CUENTA CORRIENTE |
| Descuento | Sí / No, y el porcentaje (**máximo 20%**) |
| Fecha de envío | |
| Estado del pedido | PENDIENTE → CONFIRMADO → ENVIADO → ENTREGADO (o CANCELADO) |
| Estado de la entrega | SIN PACTAR / PACTADA / ENTREGADA |
| Número de factura | Se genera cuando el pedido se **entrega** |

- Al **confirmar** el pedido se descuenta el stock. Si falta stock de algún producto, no se confirma nada.
- Al confirmar se puede imprimir o descargar un **comprobante de la orden** para el cliente (no válido como factura).
- Cancelar un pedido confirmado devuelve el stock.

## 📈 Monitor predictivo y alertas

| Alerta | Cuándo salta | Acción sugerida |
|---|---|---|
| Quiebre de stock | El stock proyectado llega a cero | Reponer |
| Debajo del mínimo | Stock actual menor o igual al mínimo de seguridad (`stock ≤ mínimo`) | Reponer |
| Comprar ahora | Stock proyectado ≤ demanda diaria × días de entrega del proveedor + stock mínimo (con margen extra si el proveedor es malo) | Fecha límite para pedir y cantidad sugerida |
| Próximo a vencer | El lote vence en los próximos N días (**60 por defecto**, configurable por grupo) | Vender ese lote primero |
| Baja rotación | Vende menos del umbral del grupo (ej. **10% del stock en 30 días**) | Promocionar o mover el producto, comprar menos o no recomprar |

Cada alerta tiene criticidad **ALTA / MEDIA / BAJA**.

## 👥 Quién hace qué

| Integrante | Rol |
|---|---|
| Florencia Estrada | Front-end, integración, Docker y documentación |
| Nicolas Fernandez | Back-end Java (core-service) |
| Marcelo Daniel Lucero | Analytics Python (analytics-service) |

El detalle técnico está en [plan-mejoras.md](plan-mejoras.md) y las tareas en el [tablero](https://github.com/users/festrada-it/projects/1).
