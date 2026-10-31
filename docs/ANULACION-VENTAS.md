# Anulación de ventas

`POST /api/ventas/{id}/anular` requiere JWT de **ADMIN** y no lleva cuerpo.
Devuelve 200 con los datos originales de la venta y el nuevo campo
`anulada: true`. El campo `anulada` también aparece en las respuestas de
registro y de ventas del día, inicialmente en `false`.

- Sin autenticación: 401; VENDEDOR: 403.
- Venta inexistente: 404.
- Venta ya anulada o devolución que supera el máximo INTEGER de stock: 400
  con mensaje en español. Ninguno de esos casos cambia el inventario.

La operación conserva venta y detalles, devuelve todas las cantidades
(incluso si el producto fue dado de baja) y registra movimientos AJUSTE
positivos con el usuario, la venta y la observación `Anulación de venta {id}`.
Una transacción bloquea la venta y los productos; una solicitud repetida o
simultánea no puede devolver dos veces el stock. Ventas y anulaciones toman
los bloqueos de productos en orden de identificador.

`GET /api/ventas/hoy` devuelve solo ventas vigentes para que la pantalla actual
no sume ventas anuladas. Analytics también las excluye de ingresos y demanda.
La reconstrucción del stock histórico mantiene los movimientos originales y
compensatorios, respetando cuándo ocurrió cada variación.

## Actualizar una base existente sin borrar datos

Detener temporalmente core y analytics, aplicar la migración y reconstruirlos:

```bash
docker compose stop core-service analytics-service
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U smartstock -d smartstock < database/init/03_anulacion_ventas.sql
docker compose up -d --build
```

Si se configuró otro usuario/base, ajustar esos argumentos. La migración es
idempotente y se ejecuta automáticamente en bases nuevas. No hace falta borrar
el volumen. Si falla la migración, resolver el error antes de arrancar la nueva
versión: Hibernate valida la columna y analytics la necesita para sus consultas.

## Pruebas

`cd core-service && sh mvnw -B verify` ejecuta tests unitarios y MVC.
Para incluir `AnulacionIntegracionTest`, habilitar `SMARTSTOCK_INTEGRACION=true`
con una base PostgreSQL de pruebas inicializada y `DB_URL`, `DB_USER`,
`DB_PASSWORD` configurados. Verifica concurrencia, rollback, productos inactivos
y productos repetidos en una venta. No ejecutar contra una base de producción.

`cd analytics-service && pytest -v` incluye las consultas reales cuando
`SMARTSTOCK_INTEGRACION=true` y `DATABASE_URL` apunta a PostgreSQL de pruebas.
Las tablas temporales de estos tests aíslan las ventas de prueba.
Ambas suites de integración se ejecutan en GitHub Actions con PostgreSQL 16.

La nueva acción visual de anular y el historial de ventas por fechas corresponden
al frontend y no están incluidos en este cambio.
