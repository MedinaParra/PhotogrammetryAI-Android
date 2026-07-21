# ITER-009 — Refinamiento robusto y acotado del grafo de poses

## Identificación

- **Fecha de inicio:** 2026-07-21
- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8` (borrador)
- **Versión:** `0.18.0-alpha27`
- **Commit funcional:** `a4bd75c3afcefdd08e63c75f911f8b1ba314d363`
- **Fase principal:** R5

## Objetivo

Reducir la deriva tras propagar poses mediante una optimización robusta, limitada y reproducible de las traslaciones del pose graph, manteniendo fijo el nodo de referencia y conservando las poses iniciales ante divergencia.

## Alcance ejecutado

### Incluido

- límite de 48 nodos, 240 aristas y 30 iteraciones;
- nodo 0 fijo como gauge;
- rotaciones e intrínsecos fijos;
- refinamiento simultáneo de traslaciones mediante objetivos relativos;
- pérdida Huber y priors hacia las posiciones iniciales;
- detección de grafo desconectado y problemas sobredimensionados;
- medición de RMS, mediana, P90, iteraciones aceptadas y aristas rechazadas;
- fallback automático a poses iniciales ante divergencia;
- gate v52 y alpha27.

### Excluido

- optimización de rotaciones;
- bundle adjustment global;
- eliminación Schur;
- conexión directa al `GlobalPoseGraphCore` runtime;
- validación con trayectorias reales del teléfono.

## Cambios y decisiones

- `BoundedPoseGraphRefinementCore` optimiza solo traslaciones para mantener memoria y complejidad acotadas.
- Las aristas se ponderan mediante residuo robusto; una medición incompatible pierde influencia.
- Los priors evitan deformaciones excesivas de escala.
- Una iteración solo se conserva si reduce el costo robusto.
- `DIVERGED` restaura el problema inicial.
- El componente se denomina refinamiento de pose graph, no BA global.

## Evidencia y validación

| Prueba | Resultado | Evidencia |
|---|---|---|
| Grafo con ciclos y ruido reduce RMS | PASS | `PoseGraphRefinementV52Test` |
| Nodo 0 permanece fijo | PASS | tolerancia 1e-12 |
| Arista gravemente incorrecta detectada | PASS | al menos una arista rechazada |
| Grafo desconectado bloqueado | PASS | `DISCONNECTED_GRAPH` |
| Límite de nodos aplicado | PASS | `NODE_LIMIT_EXCEEDED` |
| Compilación Android alpha27 | PASS | GitHub Actions run `#512` |
| AAR STEP y cierre OCCT | APROBADO | run `#512` |
| Trayectoria real Android | NO EJECUTADA | fuera de alcance |

## Resultados

- El grafo ya dispone de una etapa de refinamiento robusto posterior a la propagación inicial.
- El gate sintético reduce residuos y rechaza el outlier sin mover el gauge.
- Alpha27 compila y publica APK con 26 gates Java.
- La integración directa con el analizador real permanece pendiente.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Estado |
|---|---|---|---|
| PG-001 | alta | rotaciones permanecen fijas | abierto |
| PG-002 | alta | refinador no invocado por `GlobalPoseGraphCore` runtime | abierto |
| PG-003 | media | priors y umbrales requieren banco real | abierto |
| BA-003 | media | no existe solución Schur/global | abierto |
| VALID-001 | crítica | no hay campaña física/metrológica | abierto |

## Estado del roadmap

- Avance integral anterior: **62 %**.
- Avance integral después de ITER-009: **66 %**.
- Madurez funcional alpha estimada: **89 %**.
- Preparación industrial/metrológica estimada: **12 %**.

El aumento se concentra en R5. La preparación industrial sigue baja por ausencia de integración runtime y evidencia física.

## Siguiente iteración obligatoria

### ITER-010 — Puerta profesional de calificación y campañas

Implementar un modelo auditable de campaña de dispositivo, corpus STEP, gobierno de evidencia y metrología; bloquear cualquier declaración industrial cuando falte evidencia física y publicar alpha28.

## Criterios de entrada y salida

### Entrada

- conservar los 26 gates existentes;
- separar calificación alpha, piloto e industrial;
- tratar campañas no ejecutadas como bloqueo, no como aprobación;
- no inventar resultados Samsung/Honor.

### Salida

- puerta de calificación profesional implementada;
- campañas faltantes bloquean uso industrial;
- manifiesto reproducible de evidencia;
- alpha28 compilada;
- CI e historial aprobados;
- ITER-010 archivada.
