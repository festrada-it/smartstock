# Fill Rate por cadena (#59)

`GET /api/dashboard/fill-rate` mide qué porcentaje de lo que pidieron los clientes pudimos entregar y cuánto
dejamos de vender por falta de stock. Requiere el JWT de core-service (cualquier rol), como el resto de `/api`.
Analytics solo lee la base.

## Parámetros

| Parámetro | Valores | Por defecto |
|---|---|---|
| `periodo` | `30` o `90` (días). Otro valor responde 422 | `30` |
| `grupo_id` | Id del grupo (`categorias.id`). Filtra por el producto de cada línea | todos |
| `cliente_id` | Id del cliente. Filtra ventas y ventas perdidas | todos |

El período son los últimos `periodo` días, contando hoy. El período anterior son los `periodo` días previos a ese.
Un `grupo_id` o `cliente_id` que no existe no da error: responde sin líneas (`fill_rate: null`).

## Regla

```
Fill Rate = vendidas / (vendidas + perdidas) × 100
```

- **Vendidas:** `detalle_venta.cantidad` de los pedidos `CONFIRMADO`, `ENVIADO` y `ENTREGADO`, por fecha del pedido.
  Los `PENDIENTE` y `CANCELADO` no cuentan.
- **Perdidas:** el faltante (`cantidad_pedida - cantidad_disponible`) de `ventas_perdidas`, por fecha del rechazo.
- **Ventas perdidas en $:** faltante × `precio_unitario` guardado en la venta perdida.
- **Sin líneas** (ni vendidas ni perdidas): `fill_rate` y `semaforo` son `null`. Nunca 100 % ni 0 %.
- El Fill Rate se redondea a 2 decimales y el semáforo se decide con ese valor redondeado.

### Semáforo

Fijo, no depende del objetivo:

| Semáforo | Fill Rate |
|---|---|
| `VERDE` | ≥ 98 |
| `AMARILLO` | ≥ 95 y < 98 |
| `ROJO` | < 95 |

`objetivo` (parámetro `fill_rate_objetivo`, 95 por defecto) solo sirve para dibujar la línea del gráfico.

## Respuesta

```json
{
  "periodo": 30,
  "objetivo": 95.0,
  "total": {
    "vendidas": 960, "perdidas": 40, "fill_rate": 96.0,
    "ventas_perdidas_monto": 52000.0, "semaforo": "AMARILLO"
  },
  "anterior": {
    "vendidas": 800, "perdidas": 0, "fill_rate": 100.0,
    "ventas_perdidas_monto": 0.0, "semaforo": "VERDE"
  },
  "por_cliente": [
    {
      "cliente_id": 3, "cliente": "Carrefour Express", "vendidas": 90, "perdidas": 10,
      "fill_rate": 90.0, "ventas_perdidas_monto": 13000.0, "semaforo": "ROJO"
    }
  ],
  "por_producto": [
    {"producto_id": 7, "producto": "Helado 1L", "perdidas": 25, "ventas_perdidas_monto": 32500.0}
  ]
}
```

- `total` y `anterior` tienen los mismos campos.
- `por_cliente`: solo clientes con vendidas o perdidas en el período. Van de peor a mejor (ROJO, AMARILLO, VERDE y,
  a igual semáforo, menor Fill Rate primero). Una venta sin cliente figura como `cliente_id: null`, `cliente: "Sin cliente"`.
- `por_producto`: solo productos con ventas perdidas, de mayor a menor `ventas_perdidas_monto`.
- Con `cliente_id` o `grupo_id`, el filtro vale para `total`, `anterior`, `por_cliente` y `por_producto`.
- Sin ninguna línea, `total` y `anterior` traen ceros, `fill_rate: null` y `semaforo: null`, y las listas van vacías.

## Probar

```bash
cd analytics-service
pytest tests/test_fill_rate.py                       # función pura y endpoint, sin base
SMARTSTOCK_INTEGRACION=true pytest tests/test_fill_rate.py   # suma la consulta contra PostgreSQL
```

Datos de prueba: `database/init/11_datos_fill_rate.sql` crea tres cadenas demo con Fill Rate verde, amarillo y rojo.
