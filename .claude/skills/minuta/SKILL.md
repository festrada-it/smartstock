---
name: minuta
description: Registrar una minuta de reunión del equipo en docs/minutas/ con el formato del proyecto. Usar cuando alguien pide "armar la minuta", "registrar la reunión" o "/minuta".
---

# Minuta de reunión

Las minutas son evidencia de gestión que se evalúa en la rúbrica (15 pts). Tienen que ser reales: no inventes contenido.

1. Pedí lo que falte: fecha, asistentes, temas tratados, decisiones y tareas con responsable. Si te pasan notas sueltas, ordenalas.
2. Creá `docs/minutas/AAAA-MM-DD.md` (si ya existe una ese día, agregá `-2`) con esta plantilla:

```markdown
# Minuta — AAAA-MM-DD

**Asistentes:** Florencia Estrada, Nicolas Fernandez, Daniel Marcelo Lucero
**Duración:** —

## Temas tratados
- …

## Decisiones
- …

## Tareas
| Tarea | Responsable | Issue | Fecha |
|---|---|---|---|
| … | … | #… | … |

## Próxima reunión
- …
```

3. Si hay tareas sin issue, ofrecé crearlas con `/nueva-tarea`.
4. Commiteá en una rama `docs/minuta-AAAA-MM-DD` con el mensaje `docs: minuta del AAAA-MM-DD` y ofrecé abrir la PR hacia `develop`.
