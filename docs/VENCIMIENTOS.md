# Vencimientos por lote (#22)

`GET /api/vencimientos` lista los lotes que vencen dentro de la ventana de su grupo, o que ya vencieron,
para venderlos primero o retirarlos. Requiere el JWT de core-service (cualquier rol), como el resto de `/api`.

## Respuesta

Lista de lotes, ordenada por criticidad (ALTA, MEDIA, BAJA), luego por menos días restantes y por `lote_id`.
Si no hay ninguno, devuelve `[]`.

```json
[
  {
    "lote_id": 12,
    "producto_id": 4,
    "sku": "HEL-001",
    "producto": "Helado 1L",
    "marca": "Frigor",
    "grupo": "Congelados",
    "cantidad_disponible": 40,
    "fecha_vencimiento": "2026-11-12",
    "dias_restantes": 3,
    "valor_en_riesgo": 1200.0,
    "criticidad": "ALTA",
    "accion_sugerida": "Vender primero",
    "dias_alerta_grupo": 30
  }
]
```

`marca` y `grupo` pueden ser `null` si el producto no los tiene.

## Qué lotes entran

- `cantidad_disponible > 0` y `fecha_vencimiento` cargada. Los lotes sin fecha no se alertan.
- Vencen dentro de `categorias.dias_alerta_vencimiento` de su grupo (60 si el producto no tiene grupo),
  contando hasta `hoy + N` inclusive. Todos los lotes ya vencidos entran.
- Se incluyen los productos dados de baja: sus lotes siguen ocupando depósito.

## Regla

Es la función pura `analizar_vencimiento` de `analytics-service/app/vencimientos.py`.

| Campo | Regla |
|---|---|
| `dias_restantes` | `fecha_vencimiento - hoy`; negativo si ya venció |
| `criticidad` | ALTA si ya venció o faltan ≤ 7 días; MEDIA si faltan ≤ N/2; BAJA en el resto |
| `accion_sugerida` | `Vender primero`; `Retirar de la venta` si la fecha es anterior a hoy. Un lote que vence hoy aún se vende |
| `valor_en_riesgo` | `cantidad_disponible` × `costo_unitario` **del lote**, en pesos con 2 decimales |

## Probar

```bash
cd analytics-service
pytest tests/test_vencimientos.py tests/test_api.py                       # sin base
SMARTSTOCK_INTEGRACION=true pytest tests/test_db.py                       # con PostgreSQL
```
