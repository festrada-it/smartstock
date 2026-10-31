---
name: nueva-tarea
description: Empezar una tarea nueva en SmartStock: crea el issue en GitHub con su label y responsable, y la rama desde develop siguiendo CONTRIBUTING.md. Usar cuando alguien dice "empiezo con...", "nueva tarea" o "/nueva-tarea".
---

# Nueva tarea

1. Preguntá (si no lo dijo) qué tarea es, el componente (`front`, `core`, `analytics`, `db`, `devops`) y quién la hace (Florencia, Nicolas o Daniel).
2. Redactá el issue como historia de usuario:
   - Título corto en español.
   - Cuerpo: `Como <rol> quiero <acción> para <beneficio>.` + criterios de aceptación en checklist.
3. Crealo con GitHub CLI (requiere `gh auth login`):
   ```bash
   gh issue create --title "<título>" --body "<cuerpo>" --label "<componente>,tarea" --assignee "<usuario-github>"
   ```
   Si no sabés el usuario de GitHub, crealo sin `--assignee` y avisá.
   Si el repo tiene un tablero de Projects, agregalo: `gh issue edit <N> --add-project "<nombre del tablero>"`.
4. Creá la rama desde `develop` actualizado:
   ```bash
   git checkout develop && git pull
   git checkout -b feature/<componente>-<descripcion-corta>
   ```
   Para bugs usá `fix/<descripcion-corta>`.
5. Mostrá el número del issue y el nombre de la rama, y recordá que la PR tiene que llevar `Closes #<N>`.
