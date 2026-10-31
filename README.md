# SmartStock

**Sistema Inteligente de Gestión de Inventario y Predicción de Quiebres de Stock**
Programación de Vanguardia · UCBA · Grupo 7 — Florencia Estrada, Nicolas Fernandez, Daniel Marcelo Lucero

Plataforma para PyMEs y comercios minoristas que reemplaza las planillas de cálculo: registra ventas e ingresos de mercadería en tiempo real y anticipa cuándo se va a agotar cada producto, con alertas por criticidad y una sugerencia de compra.

## Arquitectura

```
                 ┌───────────────────────────────────┐
                 │      Front-end (puerto 3000)      │
                 │ Bootstrap + Tailwind CSS + Chart.js│
                 └─────────────────┬─────────────────┘
                     REST/JSON + JWT│
          ┌────────────────────────┴────────────────────┐
          ▼                                             ▼
┌──────────────────────────┐              ┌──────────────────────────┐
│ core-service (8080)      │   mismo JWT  │ analytics-service (8000) │
│ Java 21 · Spring Boot    │ ───────────▶ │ Python · FastAPI         │
│ Auth/RBAC, catálogo,     │              │ pandas + scikit-learn:   │
│ ventas, compras          │              │ demanda, quiebre, alertas│
└────────────┬─────────────┘              └────────────┬─────────────┘
             └──────────────────┬──────────────────────┘
                                ▼
                    ┌──────────────────────┐
                    │ PostgreSQL 16 (5432) │
                    └──────────────────────┘
```

| Carpeta | Componente |
|---------|------------|
| [`frontend/`](frontend/) | Login, catálogo, ventas, monitor predictivo, usuarios y parámetros |
| [`core-service/`](core-service/) | Microservicio 1: autenticación JWT + roles, catálogo, ventas y compras |
| [`analytics-service/`](analytics-service/) | Microservicio 2: proyección de demanda, quiebre, criticidad y lote sugerido |
| [`database/`](database/) | Esquema y datos de prueba de PostgreSQL |
| [`docs/`](docs/) | Plan de trabajo |

## Cómo levantar el proyecto

Requisito: [Docker Desktop](https://www.docker.com/products/docker-desktop/) y [Git](https://git-scm.com/).

```bash
git clone https://github.com/festrada-it/smartstock.git
cd smartstock
docker compose up --build
```

| Servicio | URL |
|----------|-----|
| **Aplicación** | http://localhost:3000 |
| core-service | http://localhost:8080/api/health |
| analytics-service (Swagger) | http://localhost:8000/docs |
| PostgreSQL | `localhost:5432` · base/usuario/clave: `smartstock` |

| Usuario de prueba | Contraseña | Rol |
|-------------------|------------|-----|
| `admin@smartstock.com` | `admin123` | Administrador |
| `vendedor@smartstock.com` | `vendedor123` | Vendedor |

Para borrar la base y recrearla desde cero: `docker compose down -v && docker compose up --build`

## Endpoints principales

**core-service** (`http://localhost:8080/api`)

| Método | Ruta | Rol |
|--------|------|-----|
| POST | `/auth/login` | público |
| GET | `/productos?q=&categoriaId=` · `/productos/{id}` · `/categorias` | Admin, Vendedor |
| POST/PUT/DELETE | `/productos` · `/productos/{id}` · POST `/categorias` | Admin |
| POST | `/ventas` · GET `/ventas/hoy` | Admin, Vendedor |
| POST | `/compras` (ingreso de mercadería) | Admin, Vendedor |
| GET/POST | `/usuarios` · PUT `/usuarios/{id}/estado?activo=` | Admin |
| GET · PUT | `/parametros` · `/parametros/{clave}` | todos · Admin |

**analytics-service** (`http://localhost:8000/api`, requiere el JWT de core-service)

| Ruta | Devuelve |
|------|----------|
| `/predicciones` | Demanda estimada, días y fecha de quiebre, criticidad y lote sugerido por producto |
| `/alertas` | Solo productos con riesgo ALTO o MEDIO |
| `/resumen` | Métricas globales (valor de stock, alertas, ventas 30 días) |
| `/predicciones/{id}/proyeccion` | Stock histórico + proyectado (gráfico) |
| `/predicciones/{id}/tendencia` | Ventas diarias + pronóstico (gráfico) |

## Desarrollo sin Docker (opcional)

Levantar solo la base: `docker compose up db`

- **core-service** (Java 21): `cd core-service && ./mvnw spring-boot:run` (en Windows: `mvnw.cmd spring-boot:run`)
- **analytics-service** (Python 3.11+):
  ```bash
  cd analytics-service
  python -m venv .venv
  .venv\Scripts\activate          # Windows  (Linux/Mac: source .venv/bin/activate)
  pip install -r requirements.txt
  uvicorn app.main:app --reload
  ```
- **frontend**: abrir `frontend/` con la extensión Live Server de VS Code (puerto 5500)

## Tests

```bash
cd core-service && ./mvnw verify
cd analytics-service && pytest
```

Los mismos tests corren automáticamente en GitHub Actions en cada Pull Request.

## Equipo

Ver [docs/PLAN.md](docs/PLAN.md) para roles, avance y próximas tareas, y [CONTRIBUTING.md](CONTRIBUTING.md) para el flujo de Git.
