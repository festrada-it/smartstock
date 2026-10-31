# Historial de movimientos por producto

`GET /api/productos/{id}/movimientos` requiere JWT de ADMIN o VENDEDOR.
Devuelve 200 con una lista ordenada por fecha e identificador descendentes.
Un producto existente sin movimientos devuelve `[]`; uno inexistente devuelve
404. La baja lógica no oculta su historial.

Cada elemento contiene `id`, `productoId`, `tipo` (VENTA, COMPRA o AJUSTE),
`cantidad` (positiva entrada, negativa salida), `fecha`, `usuarioId`, `usuario`,
`ventaId` y `observacion`. Usuario y venta pueden ser nulos para movimientos
históricos sin esa asociación. No se exponen contraseñas ni entidades JPA.

El frontend puede consumir este contrato para la pantalla de historial del
Sprint 2. Este cambio incorpora la API; la pantalla corresponde a otra tarea.
