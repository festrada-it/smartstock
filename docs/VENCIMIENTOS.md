# Vencimientos por lote (#22)

## Estado

Implementada la regla pura `analizar_vencimiento` en
`analytics-service/app/vencimientos.py`, con tests sin base de datos.
**No existe todavia `GET /api/vencimientos`**: la consulta, el contrato HTTP
definitivo y los tests de integracion quedan pendientes de #20 (lotes).
Esta etapa no completa ni cierra #22.

## Regla

Entradas: fecha de vencimiento (o `None`), cantidad disponible, costo unitario
como `Decimal`, ventana de alerta del grupo en dias y fecha de referencia
`hoy` explicita. No lee el reloj ni consulta la base.

- Sin disponible o sin fecha de vencimiento: no hay alerta (`None`).
- Se incluyen fechas hasta `hoy + N` inclusive, y todos los lotes ya vencidos.
- ALTA: dias restantes <= 7, incluidos los vencidos.
- MEDIA: dias restantes <= N/2, si no corresponde ALTA.
- BAJA: el resto de los lotes incluidos.
- Accion: `Vender primero`; si la fecha es anterior a hoy,
  `Retirar de la venta`. Un lote que vence hoy no se considera ya vencido.
- Valor en riesgo: disponible por **costo unitario del lote**, calculado con
  `Decimal` sin conversion a flotante.
- Cantidad, costo o ventana negativos, y costos no finitos, producen `ValueError`.

El resultado contiene `cantidad_disponible`, `fecha_vencimiento`,
`dias_restantes`, `valor_en_riesgo`, `criticidad` y `accion_sugerida`.
La integracion agregara identificadores de lote/producto, producto, marca y grupo
a partir del esquema definitivo de #20.

## Verificacion sin Docker

Desde `analytics-service`, con las dependencias del proyecto disponibles:

```powershell
python -m pytest tests\test_vencimientos.py
```

Antes de completar #22: agregar consulta real e integracion PostgreSQL,
modelo de respuesta y tests del endpoint autenticado; documentar el JSON final
y verificar las suites existentes. La PR final debe ir hacia `develop` con
`Closes #22`, nunca directamente a `main` o `develop`.
