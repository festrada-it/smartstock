---
name: checklist-entrega
description: Revisar qué falta para la entrega de SmartStock según la consigna y la rúbrica de 100 puntos. Usar cuando alguien pregunta "¿qué nos falta?", "¿estamos para entregar?" o "/checklist-entrega".
---

# Checklist de entrega

Revisá el repo (y `gh` si está disponible) y armá una tabla con estado ✅ / ⚠️ / ❌ y qué falta en cada punto. Verificá con evidencia; no marques ✅ sin haberlo comprobado.

## Código (MVP)
- `docker compose up --build` levanta los 4 servicios sin errores.
- Login con roles ADMIN y VENDEDOR; cada rol ve solo lo suyo.
- CRUD de productos funcionando desde el front.
- Registro de ventas que descuenta stock.
- Monitor predictivo que consume analytics-service y muestra gráficos interactivos.
- Tests pasando: `./mvnw test` (core) y `pytest` (analytics).

## Gestión (15 pts)
- Ramas `main` y `develop` protegidas; trabajo en ramas `feature/` y `fix/`.
- PRs con revisión de otra persona (`gh pr list --state all`).
- Commits de los tres integrantes (`git shortlog -sn --all`).
- Tablero con historias de usuario asignadas por integrante.
- Minutas en `docs/minutas/`.

## Documentación (PDF)
- Carátula e integrantes.
- Problema y su valor.
- Diagrama de arquitectura (front ↔ core ↔ analytics ↔ BD).
- Justificación de tecnologías y de PostgreSQL.
- Mockups finales (capturas de las pantallas reales).
- Plan de pruebas (unitarias, integración, manuales).
- Plataforma y propuesta de despliegue.
- Evidencias de gestión (minutas y capturas del tablero).

## Presentación
- Resumen ejecutivo, arquitectura y guion de la demo en vivo.

Fechas: entrega intermedia el miércoles 4-11, entrega final el 12-11. Preguntá para cuál es el chequeo si no está claro.

Al final, priorizá lo que falta según puntaje de la rúbrica y lo que se puede resolver antes de esa fecha.
