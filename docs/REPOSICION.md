# Contrato de reposición por proveedor (#30)

Contrato acordado para que el frontend (#31) pueda avanzar en paralelo.
**Estado: contrato inicial; los campos todavía no están implementados en HTTP.**
La integración depende de #14 y #16 (PRs #35 y #36), no de #26.

## Respuesta de predicciones

`GET /api/predicciones` conservará todos los campos existentes y agregará:

| Campo | Tipo JSON | Significado |
|-------|-----------|-------------|
| `lead_time_dias` | integer | Plazo base del proveedor; sin proveedor, parámetro global `dias_reposicion`. No incluye el margen. |
| `punto_reposicion` | number o null | Demanda diaria proyectada de hoy por (plazo base + margen), más stock mínimo. |
| `fecha_reposicion` | string ISO `YYYY-MM-DD` o null | Primera fecha en que el stock proyectado queda menor o igual al punto de reposición. Puede ser hoy. |
| `comprar_ahora` | boolean o null | Si el stock actual ya es menor o igual al punto. Null significa que falta historial para proyectar. |

Ejemplo de los campos nuevos para demanda constante de 5 unidades/día,
stock actual 80, mínimo 30 y proveedor de 7 días con margen 3:

```json
{
  "lead_time_dias": 7,
  "punto_reposicion": 80.0,
  "fecha_reposicion": "2026-10-31",
  "comprar_ahora": true
}
```

Ejemplo sin historial suficiente (menos de dos observaciones):

```json
{
  "lead_time_dias": 7,
  "punto_reposicion": null,
  "fecha_reposicion": null,
  "comprar_ahora": null
}
```

El frontend debe distinguir `null` de `false`: mostrar falta de historial,
no afirmar que no hace falta comprar. Si hay proyección pero no se cruza el
punto dentro del horizonte, `fecha_reposicion` será null y `comprar_ahora`
será false.

## Cálculo

- Margen: `margen_dias_proveedor_bajo` (default 3) cuando la clasificación
  es menor o igual a `clasificacion_proveedor_baja` (default 2) **o** la
  calidad es `MALA`. Se aplica una sola vez aunque se cumplan ambas condiciones.
- Sin proveedor: usar `dias_reposicion`, sin margen de proveedor.
- El punto se calcula con la primera demanda diaria proyectada, sin redondear
  antes de comparar; permanece fijo durante la búsqueda de fecha.
- Stock proyectado del día futuro: stock actual menos demanda acumulada,
  limitado a cero. Día 1 corresponde a mañana.
- Horizonte: el usado por la predicción (al menos 60 días; ampliado si plazo
  efectivo más cobertura lo requiere).
- El lote sugerido cubrirá demanda durante plazo **más margen** y
  `dias_cobertura`, preservando stock mínimo.
- JWT y rutas existentes no cambian. Los modelos de respuesta que reutilicen
  la predicción deberán conservar los campos existentes.

## Integración y verificación pendientes

La demanda se leerá mediante `ventas_diarias` de `app/db.py`, sin duplicar
filtros por estados. El proveedor se leerá con un JOIN de solo lectura.
Se agregará `09_parametros_reposicion.sql` idempotente sin editar migraciones
existentes.

Tests: proveedor bueno frente a malo, límites de clasificación/calidad,
sin proveedor, compra inmediata, cruce futuro, horizonte sin cruce, falta
de historial, lote sugerido y conservación del contrato anterior.
Antes de cerrar #30: consulta, endpoints, suite de integración, CI verde y
verificación de despliegue por el equipo. PR hacia `develop` con `Closes #30`
y una aprobación, nunca push directo a `main` o `develop`.
