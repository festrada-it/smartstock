---
name: revisar-pr
description: Revisar una Pull Request de SmartStock antes de aprobarla. Usar cuando alguien pide "revisá la PR #N", "code review" o "/revisar-pr".
---

# Revisar PR

1. Si no te dieron el número, listá las abiertas: `gh pr list`.
2. Traé la PR: `gh pr view <N>` y `gh pr diff <N>`. Para correr tests, `gh pr checkout <N>`.
3. Delegá la revisión al agente `qa-revisor` con el diff y la descripción de la PR.
4. Mostrale al usuario el veredicto y los hallazgos.
5. Preguntá antes de publicar nada. Si confirma:
   - Comentario: `gh pr review <N> --comment --body "<resumen>"`
   - Aprobar: `gh pr review <N> --approve --body "<resumen>"`
   - Pedir cambios: `gh pr review <N> --request-changes --body "<resumen>"`
6. Nunca mergees sin que el usuario lo pida. Al terminar, volvé a la rama en la que estaba (`git checkout -`).
