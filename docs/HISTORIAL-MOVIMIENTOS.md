# Historial de movimientos por producto

`GET /api/productos/{id}/movimientos?page=0&size=50` requiere JWT de ADMIN o
VENDEDOR. La página empieza en cero; `page` es opcional (0 por defecto) y `size`
es opcional (50 por defecto), con valores entre 1 y 100. El historial se consulta
paginado en la base y ordenado por `fecha DESC, id DESC`, por lo que los empates
de fecha mantienen un orden estable.

Una respuesta 200 tiene este contrato (ejemplo de un producto con un movimiento):

```json
{
  "contenido": [
    {
      "id": 12,
      "productoId": 3,
      "tipo": "VENTA",
      "cantidad": -2,
      "fecha": "2026-10-31T14:30:00",
      "usuarioId": 1,
      "usuario": "Administrador",
      "ventaId": 8,
      "observacion": null
    }
  ],
  "pagina": 0,
  "tamanio": 50,
  "totalElementos": 1,
  "totalPaginas": 1
}
```

`fecha` usa ISO 8601 sin zona horaria (`LocalDateTime`). Cada elemento conserva
`id`, `productoId`, `tipo` (VENTA, COMPRA o AJUSTE), `cantidad` (positiva entrada,
negativa salida), `fecha`, `usuarioId`, `usuario`, `ventaId` y `observacion`.
Usuario y venta pueden ser nulos para movimientos históricos sin esa asociación.
No se exponen contraseñas ni entidades JPA.

Un producto existente sin movimientos devuelve `contenido: []`, `totalElementos: 0`
y `totalPaginas: 0`. Una página posterior a la última devuelve `contenido: []`
y conserva los totales. La baja lógica del producto no oculta su historial.

Errores con cuerpo JSON `{"error": "mensaje en español"}`:

- **400:** `page` negativo, `size` fuera de 1..100, valores no enteros o fuera del
  rango entero, o un desplazamiento `page * size` mayor que 2147483647.
- **404:** el producto no existe.
- **401:** falta un JWT válido o está vencido.
- **403:** el JWT no tiene uno de los roles admitidos. ADMIN y VENDEDOR tienen
  acceso; el endpoint no necesita permisos de escritura.

El frontend puede consumir este contrato para la pantalla de historial del
Sprint 2. Este cambio incorpora la API; la pantalla corresponde a otra tarea.
