# SmartStock

**Sistema Inteligente de Gestión de Inventario y Predicción de Quiebres de Stock**

Plataforma orientada a PyMEs y comercios minoristas locales para mitigar las pérdidas operativas por quiebres de inventario y optimizar la reposición de mercadería mediante seguimiento en tiempo real y proyecciones automatizadas.

## Arquitectura

```
                 ┌──────────────────────────┐
                 │  Front-end (puerto 3000)  │
                 │  Bootstrap + Tailwind CSS │
                 └─────────────┬────────────┘
                     REST/JSON │
          ┌────────────────────┴────────────────────┐
          ▼                                         ▼
┌──────────────────────────┐          ┌──────────────────────────┐
│ core-service (8080)      │          │ analytics-service (8000) │
│ Java 21 · Spring Boot    │          │ Python · FastAPI         │
│ Catálogo, ventas, RBAC   │          │ Scikit-learn: demanda y  │
│                          │          │ quiebre de stock         │
└────────────┬─────────────┘          └────────────┬─────────────┘
             └──────────────┬──────────────────────┘
                            ▼
                ┌──────────────────────┐
                │ PostgreSQL 16 (5432) │
                └──────────────────────┘
```

| Carpeta | Componente |
|---------|------------|
| [`frontend/`](frontend/) | Panel de ventas y Dashboard / Monitor Predictivo |
| [`core-service/`](core-service/) | Microservicio 1: núcleo de negocio, autenticación por roles, catálogo y ventas |
| [`analytics-service/`](analytics-service/) | Microservicio 2: proyección de demanda y estimación de quiebre |
| [`database/`](database/) | Esquema y datos de prueba de PostgreSQL |
| [`docs/`](docs/) | Plan de trabajo y documentación |

## Cómo levantar el proyecto

Requisito: [Docker Desktop](https://www.docker.com/products/docker-desktop/) y [Git](https://git-scm.com/).

```bash
git clone https://github.com/festrada-it/smartstock.git
cd smartstock
docker compose up --build
```

| Servicio | URL |
|----------|-----|
| Front-end (Monitor predictivo) | http://localhost:3000 |
| core-service | http://localhost:8080/api/health · http://localhost:8080/api/productos |
| analytics-service (docs Swagger) | http://localhost:8000/docs |
| PostgreSQL | `localhost:5432` · base/usuario/clave: `smartstock` |

Usuario de prueba: `admin@smartstock.com` / `admin123`

Para borrar la base y recrearla desde cero: `docker compose down -v && docker compose up --build`

### Desarrollo sin Docker (opcional)

- **core-service**: Java 21 + Maven → `cd core-service && mvn spring-boot:run`
- **analytics-service**: Python 3.12 → `cd analytics-service && pip install -r requirements.txt && uvicorn app.main:app --reload`
- **frontend**: abrir `frontend/index.html` con Live Server de VS Code

(La base se puede seguir levantando sola con `docker compose up db`.)

## Equipo

Ver [docs/PLAN.md](docs/PLAN.md) para los roles y el plan de trabajo, y [CONTRIBUTING.md](CONTRIBUTING.md) para el flujo de Git.
