# Sistema de historial de iteraciones

Este directorio conserva la continuidad técnica de SKM Polea AI. Su objetivo es evitar iteraciones aisladas, pérdida de decisiones y reinicios sin contexto.

## Archivos

- `CURRENT.md`: estado operativo actual y siguiente iteración obligatoria.
- `ITERATION_TEMPLATE.md`: estructura mínima que debe completar cada iteración.
- `history/`: registros cerrados e inmutables.

## Identificación

Formato recomendado:

```text
ITER-NNN_YYYY-MM-DD_descripcion-corta.md
```

Ejemplo:

```text
ITER-002_2026-07-21_drive-plan-database-audit.md
```

Los números no deben reutilizarse. Una corrección posterior se agrega como nueva iteración o como apéndice claramente fechado; no se reescribe silenciosamente el historial.

## Ciclo de una iteración

1. Leer `CURRENT.md` y el último archivo de `history/`.
2. Confirmar el objetivo, los criterios de entrada y los bloqueos heredados.
3. Ejecutar cambios en una rama y PR identificables.
4. Registrar pruebas, artefactos, resultados y fallos reales.
5. Crear el archivo histórico de la iteración.
6. Actualizar `CURRENT.md` con:
   - estado acumulado del producto;
   - riesgos abiertos;
   - siguiente iteración;
   - criterios de entrada y salida.
7. Ejecutar `python3 PulleyKnowledgeMvp/tools/validate_iteration_history.py`.
8. Mantener el PR en borrador cuando las puertas físicas o técnicas no estén satisfechas.

## Campos obligatorios

Cada registro histórico debe contener exactamente estas secciones como mínimo:

- `## Identificación`
- `## Objetivo`
- `## Alcance ejecutado`
- `## Cambios y decisiones`
- `## Evidencia y validación`
- `## Resultados`
- `## Fallos, riesgos y deuda`
- `## Estado del roadmap`
- `## Siguiente iteración obligatoria`
- `## Criterios de entrada y salida`

`CURRENT.md` debe contener:

- `## Estado acumulado`
- `## Iteración actual o última cerrada`
- `## Bloqueos activos`
- `## Siguiente iteración obligatoria`
- `## Criterios de entrada`
- `## Criterios de salida`

## Reglas de evidencia

- No declarar una prueba como aprobada sin indicar dónde se ejecutó.
- Distinguir siempre entre código inspeccionado, prueba sintética, compilación, ejecución en teléfono y medición física.
- Registrar commit, workflow/run, APK/hash y dispositivo cuando correspondan.
- No afirmar precisión industrial a partir de una compilación o una simulación.
- Los documentos de Drive se registran mediante ID/URI, OT, plano, revisión y cota; no se copian secretos ni información innecesaria.
- Un hallazgo contradictorio no se elimina: se registra como conflicto y se define su resolución pendiente.

## Definición de cierre

Una iteración se considera cerrada solo cuando:

- el registro histórico existe;
- los resultados son verificables;
- los fallos no se ocultan;
- `CURRENT.md` apunta a una siguiente iteración concreta;
- los criterios de entrada y salida están definidos;
- la validación automática del historial termina correctamente.

## Relación con el roadmap

El roadmap principal está en `../ROADMAP_ENGINEERING.md`. Cada iteración debe indicar qué fase modifica (`R0` a `R8`) y si la puerta de salida permanece abierta o cerrada.