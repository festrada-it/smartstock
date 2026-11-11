# Demanda e ingresos según el estado del pedido

Regla de analytics-service (plan §5.3). Solo lee la base; no hay rutas nuevas.

## Qué cuenta como venta

Un pedido es demanda e ingreso solo si su `estado` es `CONFIRMADO`, `ENVIADO` o `ENTREGADO`.
`PENDIENTE` (ya reservó stock, pero nadie lo aceptó) y `CANCELADO` no cuentan.

| Dato | Fuente | Dónde se usa |
|---|---|---|
| Unidades por día y producto | `detalle_venta.cantidad` de los pedidos que venden, por fecha del pedido | `/api/predicciones`, `/api/alertas` y proyecciones |
| Unidades vendidas (30 días) | Igual que arriba | `/api/resumen` → `unidades_vendidas_30_dias` |
| Ingresos (30 días) | Suma de `ventas.total`, que ya incluye el descuento | `/api/resumen` → `ingresos_30_dias` |

El ingreso no se calcula como cantidad × precio de lista: eso ignoraría el descuento del pedido.

analytics-service ya no usa la columna `ventas.anulada`; el estado la reemplaza. Falta que core-service y
el esquema dejen de mantenerla (issue de seguimiento).

## Probar

```bash
cd analytics-service
SMARTSTOCK_INTEGRACION=true pytest tests/test_db.py
```

Las pruebas de integración usan tablas temporales: un pedido `PENDIENTE` y otro `CANCELADO` no suman, y el
descuento se refleja en los ingresos.
