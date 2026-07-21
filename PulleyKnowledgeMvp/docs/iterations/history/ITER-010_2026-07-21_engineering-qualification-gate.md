# ITER-010 — Puerta profesional de calificación y campañas

## Identificación

- **Fecha de inicio:** 2026-07-21
- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8` (borrador)
- **Versión:** `0.18.0-alpha28`
- **Commit funcional:** `036c974948e71e9b208163774821cd040504ab23`
- **Fases principales:** R6 y R8

## Objetivo

Implementar una puerta profesional, reproducible y fail-closed que separe la madurez alpha, el permiso para piloto de campo y una eventual calificación industrial, sin convertir campañas no ejecutadas en evidencia favorable.

## Alcance ejecutado

### Incluido

- modelo de campañas físicas por dispositivo y ejecución;
- control de instalación, lanzamiento, captura, tiempo, temperatura, memoria y fallos JNI;
- control de importaciones STEP, reconstrucciones y recuperación tras interrupción;
- estados de campaña `PASS`, `REVIEW`, `BLOCKED` y `NOT_EXECUTED`;
- puerta de liberación `ALPHA_BLOCKED`, `ALPHA_READY`, `FIELD_PILOT_REVIEW` e `INDUSTRIAL_QUALIFIED`;
- requisitos de corpus STEP, dispositivos, metrología trazable y aprobación formal;
- manifiesto canónico de evidencia con SHA-256 independiente del orden;
- gate v53 y alpha28.

### Excluido

- ejecución real en Samsung A15;
- ejecución real en Honor X5C;
- ensayo de repetibilidad con instrumentos trazables;
- aprobación formal de ingeniería;
- declaración de calificación industrial del producto actual.

## Cambios y decisiones

- `DeviceQualificationCore` bloquea campañas con fallos JNI, temperatura >= 48 °C, memoria excedida o tasas de éxito insuficientes.
- Menos de tres ejecuciones, corpus pequeño o métricas marginales producen `REVIEW`.
- `EngineeringReleaseGateCore` permite estado alpha con CI e historial, pero exige dispositivos y corpus para piloto.
- La calificación industrial exige metrología trazable, cinco especímenes, cinco repeticiones, RMSE de radio <= 10 mm, error máximo de radio <= 30 mm y aprobación formal.
- `QualificationEvidenceManifestCore` genera texto canónico y huella SHA-256 reproducible.
- Las campañas reales del proyecto permanecen declaradas como no ejecutadas.

## Evidencia y validación

| Prueba | Resultado | Evidencia |
|---|---|---|
| Sin evidencia física solo queda alpha | PASS | `EngineeringQualificationV53Test` |
| Dos campañas sintéticas sin metrología permiten solo revisión piloto | PASS | gate v53 |
| Fallo de dispositivo bloquea piloto | PASS | gate v53 |
| Evidencia sintética completa satisface la lógica | PASS | no representa estado real |
| Manifiesto independiente del orden | PASS | SHA-256 estable |
| Compilación Android alpha28 | PASS | GitHub Actions run `#528` |
| Samsung A15 físico | NO EJECUTADA | pendiente ITER-011 |
| Honor X5C físico | NO EJECUTADA | pendiente ITER-011 |
| Metrología trazable | NO EJECUTADA | pendiente R8 |

## Resultados

- El proyecto ya tiene una definición ejecutable de qué evidencia falta para avanzar de alpha a piloto e industrial.
- La ausencia de campañas físicas bloquea correctamente la afirmación industrial.
- La regla de error máximo de radio de 30 mm se preserva también en la puerta metrológica.
- Alpha28 compila con 27 gates Java y STEP/OCCT.
- El estado real permanece alpha, no calificado industrialmente.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Estado |
|---|---|---|---|
| DEV-001 | crítica | campaña Samsung A15 no ejecutada | abierto |
| DEV-002 | crítica | campaña Honor X5C no ejecutada | abierto |
| MET-001 | crítica | no existe banco metrológico trazable | abierto |
| STEP-001 | alta | corpus real de al menos 20 STEP no ejecutado | abierto |
| APP-001 | crítica | aprobación formal de ingeniería inexistente | abierto |
| ABI-001 | media | OCCT solo arm64-v8a | abierto |

## Estado del roadmap

- Avance integral anterior: **66 %**.
- Avance integral después de ITER-010: **70 %**.
- Madurez funcional alpha estimada: **91 %**.
- Preparación industrial/metrológica estimada: **15 %**.

La preparación industrial sube poco porque la puerta existe, pero la evidencia física que debe alimentarla todavía no existe.

## Siguiente iteración obligatoria

### ITER-011 — Integración runtime completa y campaña física inicial

Conectar los productores y refinadores al pipeline real, instrumentar duración/memoria/temperatura, ejecutar una primera campaña Samsung A15 y Honor X5C, y conservar calificación industrial bloqueada hasta disponer de evidencia trazable.

## Criterios de entrada y salida

### Entrada

- usar alpha28 y conservar los 27 gates;
- no reutilizar resultados sintéticos como evidencia física;
- registrar hash de APK y dispositivo por ejecución;
- mantener PR en borrador;
- no declarar uso metrológico.

### Salida

- pipeline runtime invoca safety gate y productores reales;
- campaña inicial documentada en ambos dispositivos o estado explícito de fallo;
- métricas térmicas, memoria, tiempo y JNI registradas;
- corpus STEP real iniciado;
- CI e historial exitosos;
- siguiente iteración definida según resultados físicos.
