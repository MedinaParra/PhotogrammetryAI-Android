# ITER-008 — Productores automáticos de degeneración visual

## Identificación

- **Fecha de inicio:** 2026-07-21
- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8` (borrador)
- **Versión:** `0.18.0-alpha26`
- **Commit funcional:** `55e32b03322a306dc6a090a8b52da880e2a959c5`
- **Fase principal:** R4

## Objetivo

Producir métricas reproducibles para detectar escenas planas, reflejos, desenfoque y ambigüedad repetitiva, manteniendo valores desconocidos como evidencia faltante en vez de convertirlos en aprobación.

## Alcance ejecutado

### Incluido

- homografía de ocho parámetros mediante RANSAC determinista de cuatro puntos;
- refinamiento por mínimos cuadrados con todos los inliers;
- competencia contra soporte y RMS de la matriz fundamental;
- estados `PLANAR_DOMINANT`, `PLANAR_POSSIBLE` y `VOLUMETRIC_SUPPORTED`;
- agregación conservadora de cuadros desenfocados y saturados/reflejantes;
- agregación de pares ambiguos por ratio del segundo vecino, mutualidad y cobertura espacial;
- métricas suplementarias de sesión;
- adaptador hacia `PhotogrammetrySafetyGateAdapter.SupplementalMetrics`;
- gate v51 y alpha26.

### Excluido

- extracción de highlights directamente desde cada bitmap del pipeline Android;
- invocación automática desde `SessionOverlapAnalyzer`;
- modelos neuronales de segmentación de reflejos;
- validación con fotografías reales de poleas pulidas.

## Cambios y decisiones

- `HomographyModelCompetitionCore` usa tamaño e iteraciones acotadas.
- Una escena se marca planar dominante solo cuando la homografía supera claramente al soporte fundamental.
- Mezclas de dos transformaciones planas no se aceptan como una única escena plana coherente.
- `VisualDegradationAggregationCore` exige muestras mínimas; una muestra insuficiente conserva `NaN` y brechas explícitas.
- `PhotogrammetrySupplementalMetricsCore` agrega resultados de pares y degradación por sesión.
- `PhotogrammetrySupplementalMetricsAdapter` conserva compatibilidad con el safety gate existente.

## Evidencia y validación

| Prueba | Resultado | Evidencia |
|---|---|---|
| Plano coherente detectado | PASS | soporte homografía > 94 % |
| Mezcla de transformaciones no falsa planaridad | PASS | soporte bajo umbral planar |
| Agregación de desenfoque/reflejos/repetición | PASS | fracciones esperadas exactas |
| Sesión degradada bloqueada por safety gate | PASS | `VisualDegeneracyV51Test` |
| Muestras faltantes permanecen como brecha | PASS | `NaN` y `evidenceGaps` |
| Compilación Android alpha26 | PASS | GitHub Actions run `#500` |
| AAR STEP/cierre OCCT | APROBADO | run `#500` |
| Pruebas físicas de reflejos | NO EJECUTADA | fuera de alcance |

## Resultados

- La puerta fotogramétrica ya dispone de productores matemáticos para dominancia planar y degradación agregada.
- Los casos sintéticos planarios, mixtos, repetitivos y reflectantes están cubiertos.
- Alpha26 compila y publica APK con 25 gates Java.
- Los productores todavía deben conectarse a los frames y pares reales del analizador.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Estado |
|---|---|---|---|
| RECON-004 | alta | productor no invocado dentro de `SessionOverlapAnalyzer` | abierto |
| VISION-003 | alta | highlight/saturación no calculados desde bitmap runtime | abierto |
| VISION-004 | media | umbrales requieren banco real con superficies metálicas | abierto |
| BA-002 | alta | ruta visual aún no usa coordinador runtime | abierto |
| VALID-001 | crítica | no hay campaña física/metrológica | abierto |

## Estado del roadmap

- Avance integral anterior: **59 %**.
- Avance integral después de ITER-008: **62 %**.
- Madurez funcional alpha estimada: **87 %**.
- Preparación industrial/metrológica estimada: **11 %**.

El incremento se concentra en R4. La preparación industrial apenas cambia porque los umbrales todavía no fueron calibrados con fotografías reales.

## Siguiente iteración obligatoria

### ITER-009 — Refinamiento robusto del grafo global de poses

Implementar una optimización acotada del grafo, reducir residuos de ciclos sin alterar el gauge, rechazar aristas incompatibles, persistir métricas antes/después y publicar alpha27.

## Criterios de entrada y salida

### Entrada

- conservar los 25 gates existentes;
- fijar el nodo 0 y preservar escala local;
- no denominar BA global a un refinamiento acotado de pose graph;
- limitar nodos, aristas e iteraciones.

### Salida

- optimizador robusto del grafo implementado;
- residuos de traslación y ciclos reducidos en casos sintéticos;
- outliers rechazados;
- alpha27 compilada;
- CI e historial aprobados;
- ITER-009 archivada.
