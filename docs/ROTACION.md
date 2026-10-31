# Rotación por producto y grupo (#28)

## Estado del avance

Implementada únicamente la función pura `analizar_rotacion` y sus tests.
Los endpoints `/api/rotacion` y `/api/rotacion/grupos` aún no existen.
Este avance no completa ni cierra #28.

Según el issue actualizado, #28 depende de #14 y #16, **no de #26**.
La integración usará `ventas_diarias` y `stock_historico` de `app/db.py`;
#26 actualizará esas consultas a estados del pedido sin cambiar esta regla.

## Regla

- Entradas: unidades vendidas, 30 valores diarios de stock histórico y umbral
  configurable del grupo.
- Ventas y stock deben corresponder al mismo período de 30 días.
- Stock promedio: media de todos los días, incluidos los días con stock cero.
- Rotación: unidades vendidas divididas por stock promedio.
- Stock promedio cero: rotación `None`, estado `SIN_STOCK`, sugerencia
  `Sin stock`, incluso si hubo ventas.
- Rotación mayor o igual al umbral: `SANA`,
  `Seguir reponiendo según la predicción`.
- Rotación menor que la mitad del umbral: `BAJA`,
  `No recomprar / promocionar`.
- Entre la mitad inclusive y el umbral exclusivo: `BAJA`, `Comprar menos`.
- Las comparaciones usan el valor sin redondear.
- Entradas negativas o no finitas y períodos distintos de 30 días producen
  `ValueError`. La función no consulta la base, no lee el reloj ni modifica
  el historial recibido.

El resultado contiene `unidades_vendidas`, `stock_promedio`, `rotacion`,
`umbral_rotacion`, `estado` y `sugerencia`.

## Pendiente de integración

Leer el umbral de `categorias.umbral_rotacion` una vez integrado #16.
Para cada grupo se sumarán las ventas de sus productos y sus stocks por día
antes de aplicar la misma regla; no se promediarán las rotaciones individuales.
Agregar los endpoints autenticados, identificadores, contrato JSON y tests de
API/consulta. Verificar que ambas series temporales usen las mismas fechas.

## Tests locales sin Docker

Desde `analytics-service`:

```powershell
python -m pytest tests\test_rotacion.py -q
```

La PR final irá hacia `develop` con `Closes #28`, una aprobación y CI en verde.
No cerrar el issue con este avance parcial.
