# Contrato de reposición por proveedor (#30)

Contrato publicado antes de implementar para que el frontend (#31) avance en paralelo.
**Estado: implementado** en `GET /api/predicciones` (y en las respuestas que reutilizan la predicción:
`/api/predicciones/{id}`, `/api/predicciones/{id}/proyeccion` y `/api/alertas`).
No depende de #26: lee las ventas con `ventas_diarias` de `app/db.py`.

## Respuesta de predicciones

Conserva **todos** los campos existentes y agrega:

| Campo | Tipo JSON | Significado |
|-------|-----------|-------------|
| `lead_time_dias` | integer | Plazo base del proveedor; sin proveedor, parámetro global `dias_reposicion`. No incluye el margen. |
| `margen_dias` | integer | Días extra de anticipación por proveedor poco confiable (0 si no aplica). |
| `plazo_efectivo_dias` | integer | `lead_time_dias + margen_dias`: el plazo con el que se calcula el punto y el lote. |
| `proveedor_id` | integer o null | Proveedor activo del producto. Null si no tiene, está dado de baja o sus datos son inválidos. |
| `proveedor` | string o null | Nombre del proveedor; null en los mismos casos. |
| `punto_reposicion` | number o null | Demanda diaria proyectada de hoy por `plazo_efectivo_dias`, más stock mínimo, redondeado a 2 decimales. |
| `fecha_reposicion` | string ISO `YYYY-MM-DD` o null | Primera fecha en que el stock proyectado queda menor o igual al punto. Puede ser hoy. |
| `comprar_ahora` | boolean o null | Si el stock actual ya es menor o igual al punto. Null significa que falta historial y no se puede afirmar nada. |

Ejemplo con demanda constante de 5 unidades/día, stock actual 80, mínimo 30 y un proveedor
de 7 días con calidad MALA (margen 3):

```json
{
  "lead_time_dias": 7,
  "margen_dias": 3,
  "plazo_efectivo_dias": 10,
  "proveedor_id": 4,
  "proveedor": "Alimentos Frescos Norte",
  "punto_reposicion": 80.0,
  "fecha_reposicion": "2026-11-03",
  "comprar_ahora": true
}
```

Ejemplo sin historial suficiente (menos de dos observaciones) y stock sobre el mínimo:

```json
{
  "lead_time_dias": 7,
  "margen_dias": 0,
  "plazo_efectivo_dias": 7,
  "proveedor_id": null,
  "proveedor": null,
  "punto_reposicion": null,
  "fecha_reposicion": null,
  "comprar_ahora": null
}
```

El frontend debe distinguir `null` de `false`: `null` es falta de historial, no que no haga falta
comprar. Si hay proyección pero no se cruza el punto dentro del horizonte, `fecha_reposicion` es
null y `comprar_ahora` es false. **Excepción:** sin historial pero con el stock en el mínimo o por
debajo, `comprar_ahora` es `true` y `fecha_reposicion` es hoy (con `punto_reposicion` null), porque
la compra es segura aunque la demanda sea desconocida.

## Cálculo

- Margen: `margen_dias_proveedor_bajo` (default 3) cuando la clasificación es menor o igual a
  `clasificacion_proveedor_baja` (default 2) **o** la calidad es `MALA`. Se aplica una sola vez
  aunque se cumplan ambas condiciones.
- Sin proveedor: `dias_reposicion`, sin margen.
- Un proveedor **dado de baja** se trata como sin proveedor: ya no se le puede pedir mercadería.
- Un proveedor con datos inválidos (por ejemplo, calidad desconocida) **no rompe** la respuesta:
  ese producto usa el plazo global y se registra una advertencia en el log. Lo mismo ocurre si los
  parámetros globales son inválidos.
- El punto se calcula con la primera demanda diaria proyectada y se redondea a 2 decimales
  **antes** de comparar, para que el valor mostrado coincida con la decisión; permanece fijo
  durante la búsqueda de fecha.
- Stock proyectado del día futuro: stock actual menos demanda acumulada, limitado a cero.
  El día 1 es mañana.
- Horizonte: el de la predicción (al menos 60 días; ampliado si plazo efectivo más cobertura lo requiere).
- `lote_sugerido` cubre la demanda durante `plazo_efectivo_dias` más `dias_cobertura`,
  preservando el stock mínimo. Sin proveedor no cambia respecto de la versión anterior.
- JWT y rutas existentes no cambian.

## Parámetros y migración

`database/init/09_parametros_reposicion.sql` agrega `margen_dias_proveedor_bajo` (3) y
`clasificacion_proveedor_baja` (2) a `parametros`. Es idempotente y no pisa valores editados.
Si la fila no existe, analytics usa esos mismos valores por defecto, así que la API funciona
aunque la migración todavía no se haya aplicado (el Administrador solo podrá editarlos desde
`/api/parametros` una vez aplicada). En una base existente:

```bash
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U smartstock -d smartstock < database/init/09_parametros_reposicion.sql
```

## Verificación

`pytest` en `analytics-service`: casos de proveedor bueno frente a malo (el malo dispara antes),
límites de clasificación y calidad, sin proveedor, compra inmediata, cruce futuro, horizonte sin
cruce, falta de historial, lote sugerido, proveedor defectuoso y conservación del contrato anterior.
Con `SMARTSTOCK_INTEGRACION=true` y `DATABASE_URL` también corre la consulta con el proveedor
contra PostgreSQL (con tablas temporales, sin modificar datos).
