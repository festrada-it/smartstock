# Panel unificado de alertas (#32)

## Estado

Avance parcial: `armar_panel` ensambla fuentes normalizadas en memoria, filtra
y ordena sin consultar la base ni modificar los datos de origen.
**GET /api/alertas/panel todavía no está implementado.**
Depende de completar #22, #28 y #30; este avance no cierra #32.
No modifica `/api/alertas` ni sus consumidores.

## Contrato previsto

`GET /api/alertas/panel?tipo=&criticidad=`, con JWT existente.
Sin filtros se incluirán todos los tipos. Los filtros combinados se aplican
con AND. La función pura rechaza filtros desconocidos o vacíos con `ValueError`;
la capa HTTP pendiente deberá validarlos y devolver errores explícitos.

Cada elemento contiene exactamente:

```json
{
  "tipo": "QUIEBRE",
  "producto_id": 1,
  "producto": "Yerba",
  "criticidad": "ALTA",
  "detalle": "Quiebre estimado en 2 días",
  "accion_sugerida": "Reponer 50 unidades"
}
```

Orden: ALTA, MEDIA, BAJA, conservando el orden de entrada entre alertas
de la misma criticidad. Un producto puede tener varios tipos y cada lote
puede generar su propia alerta de vencimiento.

| Tipo | Inclusión | Criticidad |
|------|-----------|------------|
| QUIEBRE | Predicciones ALTA/MEDIA, igual que las alertas actuales | Conserva la de origen |
| REPOSICION | `comprar_ahora` es true; null no dispara | MEDIA |
| STOCK_MINIMO | `stock_actual < stock_minimo`, no igualdad | MEDIA |
| VENCIMIENTO | Lotes alertados por #22 | Conserva la de origen |
| BAJA_ROTACION | Estado BAJA de #28; no SANA ni SIN_STOCK | MEDIA |

La asignación MEDIA para reposición, stock mínimo y baja rotación fue aprobada
para este avance porque #32 no especifica esos niveles. Vencimientos conserva
la acción de origen; baja rotación conserva la sugerencia de origen.
Los identificadores normalizados no fijan todavía el contrato de las
respuestas HTTP de #22/#28: la capa de integración adaptará esas fuentes.

## Pendientes

Conectar las tres fuentes reales sin duplicar cálculos ni consultas de ventas;
modelos Pydantic y endpoint autenticado; validación de query params;
tests HTTP y regresión de `/api/alertas`; suite completa, CI y despliegue.
No devolver un panel vacío como fallback si una fuente falla.

## Tests locales

Desde `analytics-service`, sin Docker ni PostgreSQL:

```powershell
python -m pytest tests\test_panel_alertas.py -q
```

La PR final debe apuntar a `develop` con `Closes #32`, una aprobación y CI verde.
