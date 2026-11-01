# Plan de trabajo - SmartStock (Grupo 7)

Requerimiento: documento *Fase 1 - Grupo 7* (Programación de Vanguardia - UCBA).

## Roles

| Integrante | Componente principal | Responsable de |
|------------|---------------------|----------------|
| Florencia Estrada | **Front-end + integración** | Pantallas, consumo de APIs, Docker, CI, documentación |
| Nicolas Fernandez | **core-service (Java)** | Seguridad JWT/RBAC, catálogo, ventas, compras, tests |
| Daniel Marcelo Lucero | **analytics-service (Python)** | Modelo predictivo, criticidad, lote sugerido, métricas del modelo |

> La asignación es una propuesta: ajustarla entre los tres. Todos revisan PRs de los demás.

## Estado actual (lo que ya está hecho)

### Base de datos (`database/init/`)
- [x] Tablas: usuarios, categorias, productos (con costo y umbral mínimo), ventas, detalle_venta, movimientos_stock, parametros
- [x] Datos de prueba: 2 usuarios, 8 productos, 90 días de ventas y compras simuladas
- [x] `03_anulacion_ventas.sql`: columna `ventas.anulada` (#7)

### core-service (Java 21 + Spring Boot)
- [x] Login con JWT (`POST /api/auth/login`) y permisos por rol (ADMIN / VENDEDOR)
- [x] CRUD de productos con baja lógica (`/api/productos`) y categorías (`/api/categorias`)
- [x] Registro de ventas transaccional con bloqueo de fila y rollback si falta stock (`POST /api/ventas`)
- [x] Ingreso de mercadería (`POST /api/compras`)
- [x] Gestión de usuarios (`/api/usuarios`) y parámetros (`/api/parametros`)
- [x] Tests unitarios (venta, JWT)
- [x] Tests de controllers con `@WebMvcTest` y matriz de permisos por rol (#5)
- [x] Historial de movimientos por producto `GET /api/productos/{id}/movimientos` (#6, [contrato](HISTORIAL-MOVIMIENTOS.md))
- [x] Anulación de ventas con devolución de stock `POST /api/ventas/{id}/anular` (#7, [contrato](ANULACION-VENTAS.md))
- [x] CI con PostgreSQL para tests de integración (#7)

### analytics-service (Python + FastAPI + scikit-learn + pandas)
- [x] Regresión lineal sobre 90 días de ventas para proyectar demanda
- [x] Fecha estimada de quiebre, criticidad ALTA / MEDIA / BAJA y lote de compra sugerido
- [x] Endpoints para el dashboard: `/api/predicciones`, `/api/alertas`, `/api/resumen`,
      `/api/predicciones/{id}/proyeccion`, `/api/predicciones/{id}/tendencia`
- [x] Valida el mismo JWT que emite core-service
- [x] Tests unitarios y de API

### Front-end (Bootstrap + Tailwind + Chart.js)
- [x] Pantalla 1 - Login con derivación según rol
- [x] Pantalla 2 - Catálogo: filtros, estado de stock, modales de alta/edición, ingreso de mercadería
- [x] Pantalla 3 - Ventas: búsqueda rápida, carrito, finalizar venta, ventas del día
- [x] Pantalla 4 - Monitor predictivo: métricas, proyección de stock, alertas, tendencia vs. pronóstico, sugerencias de compra
- [x] Usuarios y parámetros (Admin)

## Pendiente (crear un issue por cada tarea)

### Sprint 1 - Probar e integrar
| Componente | Tarea |
|------------|-------|
| todos | Levantar con `docker compose up --build`, recorrer las 4 pantallas con ambos roles y cargar bugs |
| core | ✅ Tests de integración de los controllers (`@WebMvcTest`) y de reglas de acceso por rol (#5) |
| analytics | Medir el error del modelo (MAE) con validación temporal (entrenar con 60 días, evaluar 30) |
| front | Revisar diseño responsive (tablet para la pantalla de ventas, como en el mockup) |

### Sprint 2 - Mejoras del modelo y del negocio
| Componente | Tarea |
|------------|-------|
| analytics | Comparar regresión lineal vs. Random Forest / estacionalidad semanal y elegir por MAE |
| analytics | Endpoint con las métricas del modelo para mostrarlas en el dashboard |
| core | ✅ Historial de movimientos por producto (`GET /api/productos/{id}/movimientos`) (#6) · paginación pendiente (#8) |
| core | ✅ Anulación de ventas (devuelve stock) (#7) |
| front | Pantalla de historial de movimientos y de ventas por rango de fechas |

### Plan de mejoras: distribuidor mayorista

Detalle, modelo de datos y decisiones en [plan-mejoras.md](plan-mejoras.md). Tablero: [SmartStock](https://github.com/users/festrada-it/projects/1). Milestone: [Entrega 4-11](https://github.com/festrada-it/smartstock/milestone/1). Se marca cada tarea al mergear su PR.

> **Objetivo (cambio del 31-10):** **todos los pasos 1 a 8 tienen que estar para el miércoles 4-11.** La entrega final (12-11) queda para ajustes según la devolución del profe.
>
> **Ninguna tarea se frena por fechas:** si una tarea depende de otra, se arranca con el contrato publicado y datos de ejemplo, y se conecta cuando se mergea. Cada PR publica su contrato en `docs/` en el primer commit.
>
> **Corte del lunes 2-11 a la noche:** lo que no llegue cede lugar a la demo de punta a punta: login → producto → proveedor → cliente → pedido → alertas. Orden y camino crítico en [plan-mejoras.md §9](plan-mejoras.md#9-objetivo-4-11-y-camino-crítico).

**Entrega intermedia (4-11): pasos 0 a 8**

- [ ] **0. Preparación**: escenario en README y docs (#12, hecho) · 403 con cuerpo JSON (#13)
- [ ] **1. Proveedores**: core (#14) · front (#15)
- [ ] **2. Producto y stock mínimo de seguridad**: core (#16) · front (#17)
- [ ] **3. Clientes**: core (#18) · front (#19)
- [ ] **4. Lotes con vencimiento y FEFO**: lotes (#20) · FEFO (#21) · vencimientos en analytics (#22) · front (#23)
- [ ] **5. Pedidos con estados y factura**: cabecera y confirmación (#24) · envío, factura y cancelación (#25) · analytics (#26) · front (#27)
- [ ] **6. Reglas por grupo (rotación)**: analytics (#28) · front (#29)
- [ ] **7. Lead time en la predicción**: analytics (#30) · front (#31)
- [ ] **8. Panel unificado de alertas**: analytics (#32) · front (#33)

Seguimiento de lo ya hecho: paginación del historial (#8), anulación en la matriz de `ControllersTest` (#9), y grupo y stock en los productos de un proveedor (#40).

### Sprint 3 - Cierre (4-11 al 12-11)
- Ajustes según la devolución del profe en la entrega intermedia
- Pruebas end-to-end y corrección de bugs
- Documentación final (arquitectura, endpoints, manual de usuario) y presentación

## Paso 0 - Configuración de GitHub (owner del repo)

1. ✅ **Invitar colaboradores**: Settings → Collaborators → *Add people*.
2. ✅ **Crear la rama `develop`** desde `main`.
3. ⏳ **Proteger `main` y `develop`** (pendiente): Settings → Rules → Rulesets → *New branch ruleset*:
   - Require a pull request before merging (1 approval)
   - Require status checks to pass (jobs de CI)
4. ✅ **Tablero**: [SmartStock](https://github.com/users/festrada-it/projects/1), vista Board, vinculado al repo.
5. ✅ **Labels**: `frontend`, `core`, `analytics`, `database`, `devops`, `bug`, `tarea`.

## Paso 1 - Cada integrante

1. Instalar [Git](https://git-scm.com/), [Docker Desktop](https://www.docker.com/products/docker-desktop/) y VS Code.
2. Aceptar la invitación al repo (llega por mail).
3. `git clone https://github.com/festrada-it/smartstock.git`
4. `docker compose up --build` y entrar a http://localhost:3000
