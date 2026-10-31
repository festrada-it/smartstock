# Plan de trabajo - SmartStock

## Roles (una persona por componente)

| Persona | Componente | Responsable de |
|---------|------------|----------------|
| Persona 1 | **Front-end** | Panel de ventas, Monitor Predictivo, login, consumo de las APIs |
| Persona 2 | **core-service (Java)** | Login + JWT + roles (RBAC), CRUD de catálogo, registro de ventas con descuento de stock |
| Persona 3 | **analytics-service (Python)** | Modelo de demanda, estimación de quiebre, métricas del modelo |
| Persona 4 | **BD + DevOps + QA** | Esquema y migraciones, Docker, CI, tests de integración, documentación |

> Completar con los nombres reales. Aunque cada persona sea responsable de un componente, todos revisan PRs de los demás.

## Paso 0 - Configuración inicial (owner del repo)

1. **Invitar colaboradores**: Settings → Collaborators → *Add people* (las 3 personas).
2. **Crear la rama `develop`** desde `main` y subirla.
3. **Proteger `main` y `develop`**: Settings → Branches → *Add branch ruleset*:
   - Require a pull request before merging (1 approval)
   - Require status checks to pass (seleccionar los jobs de CI)
4. **Tablero**: Projects → *New project* → plantilla *Kanban* (Backlog / En progreso / En revisión / Hecho) y vincularlo al repo.
5. **Labels**: `frontend`, `core`, `analytics`, `database`, `devops`, `bug`, `tarea`.

## Paso 1 - Cada integrante

1. Instalar [Git](https://git-scm.com/), [Docker Desktop](https://www.docker.com/products/docker-desktop/) y VS Code (o IntelliJ para Java).
2. Aceptar la invitación al repo (llega por mail).
3. `git clone https://github.com/festrada-it/smartstock.git`
4. `git checkout develop`
5. `docker compose up --build` y verificar que abre http://localhost:3000

## Sprint 1 - Base funcional

| Componente | Tareas (crear un issue por cada una) |
|------------|--------------------------------------|
| core | Entidades `Usuario`, `Categoria`, `Venta`, `DetalleVenta` · CRUD de productos (POST/PUT/DELETE) · Login que devuelve JWT |
| analytics | Validar el modelo con los datos de prueba · Endpoint de demanda proyectada por día (para gráfico) |
| front | Pantalla de login · Pantalla de catálogo (listar / alta / edición) · Gráfico de demanda en el Monitor (Chart.js) |
| db/devops | Tests del CI en verde · Script de datos de prueba más realistas (estacionalidad, fines de semana) |

## Sprint 2 - Ventas y roles

| Componente | Tareas |
|------------|--------|
| core | Registrar venta (transacción: crea venta + detalle + descuenta stock; si no hay stock hace rollback) · Restringir endpoints por rol |
| analytics | Comparar modelos (regresión lineal vs. Random Forest) y elegir por error (MAE) · Endpoint de sugerencia de reposición |
| front | Panel de ventas (carrito + confirmar venta) · Mostrar/ocultar menú según rol |
| db/devops | Tests de integración (Testcontainers o Postman/Newman) |

## Sprint 3 - Cierre

- Alertas visuales de quiebre en el dashboard
- Pruebas end-to-end y corrección de bugs
- Documentación final y presentación

## Contratos de API (acordar antes de programar)

El front y los microservicios se comunican por JSON. Antes de empezar una funcionalidad, acordar en el issue el formato del request/response. Ejemplo ya implementado:

`GET http://localhost:8000/api/predicciones`
```json
[
  {
    "id": 4, "sku": "BEB-001", "nombre": "Agua mineral 2L",
    "stock_actual": 25, "stock_minimo": 30,
    "demanda_diaria_estimada": 5.4,
    "dias_hasta_quiebre": 5,
    "fecha_quiebre_estimada": "2026-11-05"
  }
]
```
