# Contrato API — Error 403 (#13)

Cambio compatible para todas las rutas protegidas del core. Un usuario
autenticado que intenta una operación reservada a otro rol recibe:

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json;charset=UTF-8
```

```json
{"error":"No tenés permiso para realizar esta acción"}
```

No se redirige a `/error`, no se pierde la sesión y no se ejecuta la operación
de negocio. El frontend puede mostrar `error` con el helper existente de API.
Sin JWT o con JWT inválido/vencido se mantiene **401**, con el comportamiento
de cierre de sesión actual del frontend. Este cambio no amplía permisos.

No requiere migración SQL. Se verifica con servidor HTTP real, además de la
matriz de permisos MVC. Se publica después de #14 → #16 → #18 según lo pedido.
