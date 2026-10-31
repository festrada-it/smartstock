# Cómo trabajamos en SmartStock

## Ramas

| Rama | Para qué |
|------|----------|
| `main` | Versión estable / entregable. **Nadie pushea directo.** |
| `develop` | Integración de lo que se va terminando. Solo entra por Pull Request. |
| `feature/<componente>-<descripcion>` | Una funcionalidad. Ej: `feature/core-registro-ventas` |
| `fix/<descripcion>` | Corrección de un bug. Ej: `fix/front-tabla-vacia` |

## Flujo de trabajo (cada tarea)

```bash
# 1. Partir siempre de develop actualizado
git checkout develop
git pull

# 2. Crear la rama de la tarea
git checkout -b feature/core-registro-ventas

# 3. Trabajar y commitear seguido
git add .
git commit -m "feat(core): endpoint para registrar ventas"

# 4. Subir la rama
git push -u origin feature/core-registro-ventas

# 5. En GitHub: abrir Pull Request hacia develop, asignar a alguien para revisar
```

Cuando el PR se aprueba y el CI está en verde, se hace **Squash and merge** y se borra la rama.

## Mensajes de commit

Formato: `tipo(componente): descripción en presente`

- Tipos: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`
- Componentes: `front`, `core`, `analytics`, `db`, `devops`

Ejemplos:
- `feat(analytics): endpoint de predicción por categoría`
- `fix(front): mostrar error si el servicio no responde`
- `test(core): tests del servicio de ventas`

## Cambios en la base de datos

El esquema vive en `database/init/`. Si necesitás cambiarlo:
1. Agregá un archivo nuevo numerado (ej. `03_tabla_proveedores.sql`), no edites los existentes una vez mergeados.
2. Avisá al equipo: cada uno tiene que recrear su base con `docker compose down -v && docker compose up --build`.

## Reglas de convivencia

- Un PR = una tarea. PRs chicos se revisan rápido.
- Todo PR necesita **1 aprobación** de otra persona del equipo.
- Si el CI falla, no se mergea.
- Nunca subir contraseñas ni el archivo `.env`.
