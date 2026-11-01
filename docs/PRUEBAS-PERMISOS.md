# Contrato de pruebas de permisos

La matriz `rutas()` de `ControllersTest` ejecuta cada ruta protegida con ADMIN,
VENDEDOR y sin token, usando la cadena de seguridad y JWT reales y simulando
solamente los servicios de negocio.

| Ruta | ADMIN | VENDEDOR | Sin token |
| --- | --- | --- | --- |
| `POST /api/ventas/{id}/anular` | 200 | 403 | 401 |

`ControllersTest` y `AnulacionControllerTest` compartirán un único secreto y
helper JWT en `src/test`, con un email explícito para conservar la verificación
de identidad de cada suite. El secreto es exclusivo de pruebas.

Esta tarea (#9) cubre comportamiento existente y no modifica producción ni
contratos HTTP. Los issues #36, #37 y #38 siguen abiertos; se trabaja sobre la
base estable `f8c2143`, sin depender de cambios aún pendientes de esas tareas.

Validación: ejecutar `sh mvnw -B clean verify` desde `core-service` con Java 21.
Las pruebas MockMvc no requieren PostgreSQL; las suites condicionadas por
`SMARTSTOCK_INTEGRACION` deben informarse como omitidas si no se habilitan.
