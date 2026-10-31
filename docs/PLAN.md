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

### core-service (Java 21 + Spring Boot)
- [x] Login con JWT (`POST /api/auth/login`) y permisos por rol (ADMIN / VENDEDOR)
- [x] CRUD de productos con baja lógica (`/api/productos`) y categorías (`/api/categorias`)
- [x] Registro de ventas transaccional con bloqueo de fila y rollback si falta stock (`POST /api/ventas`)
- [x] Ingreso de mercadería (`POST /api/compras`)
- [x] Gestión de usuarios (`/api/usuarios`) y parámetros (`/api/parametros`)
- [x] Tests unitarios (venta, JWT)

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
| core | Tests de integración de los controllers (`@WebMvcTest`) y de reglas de acceso por rol |
| analytics | Medir el error del modelo (MAE) con validación temporal (entrenar con 60 días, evaluar 30) |
| front | Revisar diseño responsive (tablet para la pantalla de ventas, como en el mockup) |

### Sprint 2 - Mejoras del modelo y del negocio
| Componente | Tarea |
|------------|-------|
| analytics | Comparar regresión lineal vs. Random Forest / estacionalidad semanal y elegir por MAE |
| analytics | Endpoint con las métricas del modelo para mostrarlas en el dashboard |
| core | Historial de movimientos por producto (`GET /api/productos/{id}/movimientos`) |
| core | Anulación de ventas (devuelve stock) |
| front | Pantalla de historial de movimientos y de ventas por rango de fechas |

### Sprint 3 - Cierre
- Pruebas end-to-end y corrección de bugs
- Documentación final (arquitectura, endpoints, manual de usuario) y presentación

## Paso 0 - Configuración de GitHub (owner del repo)

1. **Invitar colaboradores**: Settings → Collaborators → *Add people*.
2. **Crear la rama `develop`** desde `main`.
3. **Proteger `main` y `develop`**: Settings → Rules → Rulesets → *New branch ruleset*:
   - Require a pull request before merging (1 approval)
   - Require status checks to pass (jobs de CI)
4. **Tablero**: Projects → *New project* → plantilla *Kanban*, vinculado al repo.
5. **Labels**: `frontend`, `core`, `analytics`, `database`, `devops`, `bug`, `tarea`.

## Paso 1 - Cada integrante

1. Instalar [Git](https://git-scm.com/), [Docker Desktop](https://www.docker.com/products/docker-desktop/) y VS Code.
2. Aceptar la invitación al repo (llega por mail).
3. `git clone https://github.com/festrada-it/smartstock.git`
4. `docker compose up --build` y entrar a http://localhost:3000
