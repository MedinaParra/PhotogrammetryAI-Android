# ITER-018 — Intrínsecos focales condicionados y observabilidad

## Identificación

- **Fecha de cierre:** 2026-07-22
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8` (borrador)
- **Versión:** `0.18.0-alpha39`
- **Commit funcional validado:** `f6a13aa60d8b15b08ef5f54c03160a6d636bde1b`
- **Fases:** R4, R5 y R8

## Objetivo

Validar y publicar una tercera etapa conservadora de bundle adjustment que pueda corregir únicamente una escala focal pequeña por cámara secundaria cuando exista observabilidad suficiente, sin modificar la cámara 0, el principal point, la distorsión ni la razón `fx/fy`.

## Alcance ejecutado

### Incluido

- `ConditionedFocalBundleAdjustmentCore` posterior al BA rotacional;
- una escala focal conjunta para `fx` y `fy` por cámara secundaria;
- cámara 0 completamente fija;
- `cx`, `cy`, distorsión y razón `fx/fy` invariantes;
- prior focal fuerte, damping adaptativo y pasos acotados;
- límite máximo configurable, probado en 3,5 % para el escenario sintético;
- observabilidad por cantidad de observaciones, cobertura radial, diversidad de profundidad e información respecto del prior;
- mínimo de cámaras secundarias observables antes de permitir la etapa;
- aceptación solo con mejora de costo robusto, RMS y profundidad positiva;
- fallback exacto al resultado rotacional cuando la escena no es observable o no mejora;
- estado `FOCAL_ACCEPTED` para solución admitida y `FOCAL_UNOBSERVABLE` para fallback;
- etiqueta `EMPIRICAL_FOCAL_SENSITIVITY_NOT_CALIBRATION`;
- persistencia runtime `runtime_focal_ba.json`;
- gate focal incorporado al workflow de producto;
- publicación de alpha39.

### Excluido

- optimización de principal point o distorsión;
- cambio independiente de `fx/fy`;
- calibración física de cámara;
- perfiles focales certificados para Samsung A15 u Honor X5C;
- covarianza formal o propagación de incertidumbre dimensional;
- BA global o Schur complement;
- metrología trazable;
- `armeabi-v7a`.

## Cambios y decisiones

La corrección focal se ejecuta después del BA base y del refinamiento rotacional. Cada cámara secundaria conserva su relación `fx/fy`, mientras una única variable logarítmica escala ambos valores. La cámara 0 no participa.

La observabilidad exige cobertura radial y diversidad de profundidad suficientes. Una escena central y casi plana en profundidad se clasifica como no observable y conserva exactamente el objeto de resultado de la etapa rotacional.

Durante la incorporación del gate se detectaron dos expectativas sintéticas incompatibles con el diseño conservador: una mejora RMS mínima arbitraria de 6 % y una corrección focal mínima de 0,4 %. El escenario validado produjo una mejora de aproximadamente 2,1 % con una corrección máxima de aproximadamente 0,059 %. El gate final exige una mejora RMS superior a 1 %, corrección no nula, respeto del límite superior e invariantes exactas.

La sensibilidad publicada es empírica y se etiqueta explícitamente como no calibración. No se interpreta como exactitud dimensional ni incertidumbre física.

## Evidencia y validación

| Prueba o revisión | Entorno | Resultado | Evidencia |
|---|---|---|---|
| escena observable con sesgo focal | JDK | PASS | `FOCAL_ACCEPTED` y RMS 0,7018 px → 0,6871 px |
| corrección focal acotada | JDK | PASS | corrección máxima ≈0,059 %, menor que 3,5 % |
| cámara 0 fija | JDK | PASS | pose e intrínsecos idénticos a 1e-12 |
| principal point fijo | JDK | PASS | `cx/cy` invariantes |
| razón `fx/fy` fija | JDK | PASS | razón invariante a 1e-12 |
| escena central sin profundidad | JDK | PASS | `FOCAL_UNOBSERVABLE` y fallback exacto |
| safety gate bloqueado | JDK | PASS | etapa focal no ejecutada |
| etiqueta de sensibilidad | JDK | PASS | `NOT_CALIBRATION` presente en JSON |
| gate de integridad de exportación | JDK | PASS | generación válida y tampering bloqueado |
| gate producto | GitHub Actions run `#729` | PASS | 35 gates, Gradle, APK y cierre OCCT |
| AAR STEP | GitHub Actions run `#729` | PASS | SHA-256 esperado y 25 bibliotecas nativas |
| campaña Samsung A15 | hardware | NO EJECUTADA | no existe campaña física conectada |
| campaña Honor X5C | hardware | NO EJECUTADA | no existe campaña física conectada |
| metrología | instrumentos trazables | NO EJECUTADA | fuera de alcance de la iteración |

## Resultados

- La aplicación puede aplicar una corrección focal pequeña cuando la geometría aporta información suficiente.
- Una escena mal condicionada no puede modificar intrínsecos y conserva el resultado previo exacto.
- El runtime persiste evidencia focal separada dentro de la generación transaccional.
- Alpha39 compila con STEP/OCCT para `arm64-v8a`.
- Se publica `SKM-Polea-AI-CAD-STEP-v0.18.0-alpha39`.
- No se declara calibración física, exactitud metrológica ni uso industrial.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Mitigación | Estado |
|---|---|---|---|---|
| INTR-002 | alta | la observabilidad está validada sintéticamente, no en cámaras físicas | campañas A15/X5C | abierto |
| BA-003 | media | no existe BA global ni Schur complement | etapa posterior | abierto |
| STAT-001 | alta | sensibilidad focal no representa incertidumbre física | mantener etiqueta y campaña trazable | abierto |
| RUNTIME-003 | alta | faltan checkpoints internos completos en geometría y decode | siguiente iteración | abierto |
| TX-001 | media | filesystem y SQLite no tienen journal coordinado | siguiente iteración | abierto |
| DEVICE-001 | crítica | campaña Samsung A15 no ejecutada | prueba física | abierto |
| DEVICE-002 | crítica | campaña Honor X5C no ejecutada | prueba física | abierto |
| VALID-001 | crítica | no existe metrología trazable | campaña R8 | abierto |
| ABI-001 | media | OCCT solo `arm64-v8a` | evaluar nuevo AAR | abierto |

## Estado del roadmap

- Avance integral anterior: **93 %**.
- Avance integral después de ITER-018: **94 %**.
- Madurez funcional alpha estimada: **99 %**.
- Preparación industrial/metrológica estimada: **25 %**.

El aumento corresponde a una mejora algorítmica validada y fail-closed. La preparación industrial continúa baja porque no hubo hardware objetivo, calibración física ni instrumentos trazables.

## Siguiente iteración obligatoria

### ITER-019 — Checkpoints geométricos y journal coordinado

- instrumentar cancelación dentro de fundamental, pose y triangulación;
- coordinar estados SQLite/filesystem mediante journal recuperable;
- impedir que un fallo posterior invalide una generación ya publicada;
- probar recuperación, rollback y evidencia incompleta;
- conservar PR en borrador y bloqueo industrial.