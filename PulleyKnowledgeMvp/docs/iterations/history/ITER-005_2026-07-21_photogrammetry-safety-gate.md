# ITER-005 — Puerta de seguridad fotogramétrica y degeneraciones

## Identificación

- **Fecha de inicio:** 2026-07-21
- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8`
- **Commit base:** `28a1274e9fd140ade4488548444ff4c1da8be289`
- **Commit funcional de cierre:** `5e56b746266228c4ebeae6f6c0a1ec924132a0bc`
- **Versión de aplicación:** `0.18.0-alpha23`
- **Fase principal:** `R4`
- **Responsable técnico:** MedinaParra / asistencia de ingeniería

## Objetivo

Crear una puerta fail-closed que impida promover a reconstrucción automática sesiones con cobertura insuficiente, bajo paralaje, dominancia planar, grafo desconectado, ciclos inconsistentes, reproyección excesiva o degradación visual significativa.

## Alcance ejecutado

### Incluido

- núcleo puro `PhotogrammetrySafetyGateCore`;
- estados `READY`, `REVIEW` y `BLOCKED`;
- evaluación separada de cobertura, pares, intrínsecos, paralaje, competencia de modelos, grafo, ciclos, reproyección y degradación;
- métricas faltantes tratadas como brechas de evidencia;
- adaptador desde `SessionOverlapAnalyzer.Report`;
- métricas suplementarias para homografía, desenfoque, reflejos y ambigüedad repetitiva;
- pruebas de sesión fuerte, bajo paralaje, planaridad, desconexión, reflejos, reproyección y cobertura;
- integración en GitHub Actions.

### Excluido

- estimador homográfico integrado al pipeline de pares;
- detector real de reflejos por cuadro;
- activación obligatoria del gate dentro de la interfaz;
- banco de fotografías reales;
- bundle adjustment;
- validación física.

## Cambios y decisiones

### Código y arquitectura

Se agregaron:

- `PhotogrammetrySafetyGateCore.java`;
- `PhotogrammetrySafetyGateAdapter.java`;
- `PhotogrammetrySafetyGateV48Test.java`;
- `run_photogrammetry_safety_v48_test.sh`.

La puerta no utiliza una media para ocultar una falla crítica. Cualquier bloqueo duro domina la puntuación global.

Reglas principales:

- menos de 24 cuadros: `BLOCKED`;
- menos de 8/12 sectores en un anillo: `BLOCKED`;
- pares utilizables insuficientes: `BLOCKED`;
- intrínsecos faltantes: `BLOCKED`;
- mediana de paralaje inferior a 1° o P10 inferior a 0,20°: `BLOCKED`;
- homografía >= 0,85 y soporte fundamental < 0,65: `BLOCKED`;
- grafo desconectado: `BLOCKED`;
- residuo de ciclo rotacional >14° o direccional >65°: `BLOCKED`;
- reproyección mediana >4 px o P90 >8 px: `BLOCKED`;
- degradaciones recuperables: `REVIEW`;
- métricas no calculadas: `REVIEW`, nunca `READY` silencioso.

El adaptador calcula desde el reporte existente cobertura, soporte fundamental, paralaje, reproyección y estado del grafo. La homografía, reflejos y ambigüedad repetitiva continúan como métricas suplementarias pendientes de conexión automática.

## Evidencia y validación

| Caso | Resultado |
|---|---|
| sesión completa y coherente | PASS / READY |
| paralaje 0,8° y P10 0,15° | PASS / BLOCKED |
| homografía 0,91 y fundamental 0,48 | PASS / BLOCKED |
| grafo desconectado | PASS / BLOCKED |
| reflejos 31 % | PASS / REVIEW |
| reproyección 4,2/8,5 px | PASS / BLOCKED |
| métricas suplementarias desconocidas | PASS / REVIEW |
| cobertura 7/12 en un anillo | PASS / BLOCKED |
| GitHub Actions producto run `#444` | PASS |
| 22 gates, APK y cierre OCCT | PASS |
| prueba fotográfica real | NO EJECUTADA |
| prueba térmica/dispositivo | NO EJECUTADA |

## Resultados

- El pipeline dispone de una política única para no confundir muchos matches con geometría válida.
- Las degeneraciones críticas producen bloqueo explícito.
- La falta de una métrica importante queda visible como brecha.
- El reporte de reconstrucción puede convertirse al nuevo gate sin duplicar cálculos existentes.
- Alpha23 compila para `arm64-v8a` y conserva STEP/OCCT.
- Homografía y reflejos todavía requieren productores automáticos en el pipeline real.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Mitigación | Estado |
|---|---|---|---|---|
| RECON-003 | alta | homografía no calculada automáticamente | integrar estimador de modelo | abierto |
| VISION-001 | media | reflejos no medidos automáticamente | detector de saturación/especularidad | abierto |
| VISION-002 | media | ambigüedad repetitiva no agregada por sesión | agregar estadísticas de pares | abierto |
| UI-001 | alta | gate no bloquea aún el botón de reconstrucción | integración Android posterior | abierto |
| RECON-002 | alta | sin bundle adjustment | ITER-006 | abierto |
| VALID-001 | crítica | sin evidencia en poleas reales | campaña posterior | abierto |

## Estado del roadmap

| Fase | Antes | Después |
|---|---|---|
| R4 — Fotogrametría robusta | 45 % | 65 % |
| R5–R8 | sin cambio | sin cambio |

Avance ponderado estimado después de ITER-005: **50 %**.

## Siguiente iteración obligatoria

### ITER-006 — Bundle adjustment local acotado y perfiles de cámara

Objetivos:

1. implementar optimización local de puntos y traslaciones con cámara 0 fija;
2. usar pérdida robusta y prior de escala/traslación;
3. limitar cámaras, puntos, observaciones e iteraciones para Android;
4. exigir admisión de la puerta fotogramétrica;
5. medir RMS antes y después;
6. crear perfiles versionados de intrínsecos y distorsión;
7. probar mejora sintética, outliers, degeneración y distorsión;
8. publicar `0.18.0-alpha24`.

## Criterios de entrada y salida

### Entrada

- iniciar desde la cabeza de ITER-005;
- no denominar bundle adjustment global a una ventana local;
- fijar el gauge y preservar escala con priors;
- bloquear problemas insuficientes;
- no afirmar calibración real sin campaña de tablero/patrón.

### Salida

- optimizador local determinista y acotado;
- reducción cuantificada del error sintético;
- perfiles de cámara versionados y prueba de distorsión;
- gate de seguridad respetado;
- alpha24 y CI exitosos;
- ITER-006 archivada;
- `CURRENT.md` y roadmap actualizados con ITER-007.
