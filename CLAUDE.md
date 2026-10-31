# SmartStock — Grupo 7 (Programación de Vanguardia, UCBA)

Sistema de gestión de inventario y predicción de quiebres de stock para PyMEs y comercios minoristas.

**Equipo:** Florencia Estrada (front + integración, Docker, CI, docs) · Nicolas Fernandez (core-service Java) · Daniel Marcelo Lucero (analytics-service Python). Todos revisan PRs de los demás.

**Idioma:** todo en español — código de dominio, comentarios, commits, PRs, issues y documentación.

## Arquitectura

| Carpeta | Qué es | Stack | Puerto |
|---|---|---|---|
| `frontend/` | Interfaz web (login, catálogo, ventas, monitor predictivo, usuarios/parámetros) | Bootstrap + Tailwind + Chart.js, servido por nginx | 3000 |
| `core-service/` | Núcleo de negocio: auth JWT, roles ADMIN/VENDEDOR, CRUD, ventas, compras | Java 21, Spring Boot, Spring Security, Spring Data JPA, Maven Wrapper (`mvnw`) | 8080 |
| `analytics-service/` | Predicción de demanda, fecha de quiebre, criticidad ALTA/MEDIA/BAJA, lote sugerido | Python 3.12, FastAPI, pandas, NumPy, scikit-learn | 8000 |
| `database/init/` | Esquema y datos de prueba (scripts SQL numerados) | PostgreSQL 16 | 5432 |
| `docs/` | Plan (`docs/PLAN.md`), minutas (`docs/minutas/`), documentación técnica | Markdown | — |

Comunicación: HTTP/REST + JSON. El front llama a core-service y a analytics-service. **analytics-service valida el mismo JWT que emite core-service** (comparten `JWT_SECRET`). analytics lee la base directamente (solo lectura); las escrituras de negocio pasan por core-service.

### Endpoints existentes
- core: `POST /api/auth/login`, `/api/productos` (baja lógica), `/api/categorias`, `POST /api/ventas` (transaccional, rollback si falta stock), `POST /api/compras`, `/api/usuarios`, `/api/parametros`
- analytics: `/api/predicciones`, `/api/alertas`, `/api/resumen`, `/api/predicciones/{id}/proyeccion`, `/api/predicciones/{id}/tendencia`

Si cambiás un contrato (ruta, campos del JSON), actualizá también quien lo consume y avisalo en la PR.

## Comandos

```bash
docker compose up --build          # levantar todo → http://localhost:3000
docker compose down -v && docker compose up --build   # recrear la base (tras cambios en database/init)
docker compose logs -f core-service                   # ver logs de un servicio

cd core-service && ./mvnw test     # tests Java  (en Windows: .\mvnw.cmd test)
cd analytics-service && pytest     # tests Python
```

Usuarios de prueba: `admin@smartstock.com` / `admin123` (ADMIN) · `vendedor@smartstock.com` / `vendedor123` (VENDEDOR).

## Reglas de Git (ver `CONTRIBUTING.md`)
- Nunca commitear ni pushear directo a `main` ni a `develop`. Todo entra por Pull Request hacia `develop` con 1 aprobación y CI en verde.
- Ramas: `feature/<componente>-<descripcion>` o `fix/<descripcion>`, creadas desde `develop` actualizado.
- Commits: `tipo(componente): descripción en presente`. Tipos: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`. Componentes: `front`, `core`, `analytics`, `db`, `devops`.
- Commits chicos y frecuentes: el uso continuo de Git se evalúa.
- Un PR = una tarea, vinculado a su issue (`Closes #N`).
- Base de datos: no editar scripts ya mergeados de `database/init/`; agregar uno nuevo numerado (`03_...sql`).
- Nunca subir `.env`, contraseñas ni tokens.

## Cómo trabajar en este repo
- Antes de tocar código, ubicá la tarea en el tablero / issue y trabajá en su rama.
- Cambios chicos y enfocados en un solo componente. Si una tarea toca dos servicios, separala en dos PRs o explicalo en la descripción.
- Toda lógica nueva lleva test (JUnit en core, pytest en analytics).
- Corré los tests del componente antes de commitear.
- No agregues dependencias nuevas sin mencionarlo en la PR.

## Agentes y skills del proyecto
- Agentes (`.claude/agents/`): `core-java`, `analytics-python`, `frontend`, `qa-revisor`.
- Skills (`.claude/skills/`): `/nueva-tarea`, `/minuta`, `/revisar-pr`, `/checklist-entrega`.

## Entrega y rúbrica (100 pts)
- **Entrega intermedia: miércoles 4-11.**
- **Entrega final: 12-11** (consigna). Entregables: PDF de documentación, presentación y repositorio funcional.

Al priorizar tareas, tené en cuenta qué se puede mostrar en la entrega intermedia.

| Criterio | Pts | Qué cuidar |
|---|---|---|
| Propuesta e innovación | 15 | Problema y valor claros en el README y el PDF |
| Arquitectura de microservicios | 20 | Responsabilidades separadas Java / Python / front; comunicación fluida |
| MVP y predictor | 25 | CRUD + login por roles + gráficos de predicción en el front funcionando de punta a punta |
| Tecnologías y BD | 10 | Justificación del stack y de PostgreSQL |
| Gestión ágil y Git | 15 | Ramas, PRs, tablero con historias por integrante, minutas en `docs/minutas/` |
| Documentación, pruebas, despliegue | 10 | Diagrama de arquitectura, mockups, plan de pruebas, propuesta de despliegue |
| Exposición | 5 | Demo en vivo; los tres deben conocer todo el sistema |
