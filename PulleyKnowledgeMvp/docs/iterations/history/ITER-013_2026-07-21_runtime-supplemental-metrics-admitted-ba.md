# ITER-013 — Métricas suplementarias runtime y ejecución BA admitida

## Identificación

- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8` (borrador)
- **Versión:** `0.18.0-alpha31`
- **Commit funcional validado:** `f823f95ccb9e3c92bd22665fd8f100e364729dd7`
- **Fases:** R4, R5, R6 y R8

## Objetivo

Eliminar las métricas suplementarias desconocidas del flujo runtime cuando existe evidencia suficiente, calculando competencia homografía/fundamental, desenfoque, reflejos y ambigüedad repetitiva desde frames y correspondencias reales; permitir la ejecución del BA solo cuando el safety gate completo alcance `READY`.

## Alcance ejecutado

### Incluido

- `RuntimeSupplementalMetricsCore` independiente de Android;
- `RuntimeSupplementalMetricsBuilder` conectado a frames reales;
- nueva decodificación acotada a 640 px para análisis;
- reutilización del detector determinista de 420 features;
- competencia homografía/fundamental por par candidato;
- fracción de cuadros desenfocados usando el umbral real de captura;
- fracción de altas luces y canales recortados calculada desde píxeles;
- ambigüedad repetitiva mediante distribución de primera/segunda coincidencia;
- fracción de coincidencias mutuas respecto de candidatos ratio-test;
- cobertura espacial de correspondencias;
- mínimo de 12 frames, 8 pares y 4 modelos resueltos para evidencia completa;
- archivo `runtime_supplemental_metrics.json`;
- entrega de métricas reales al `PhotogrammetrySafetyGateAdapter`;
- BA admitido únicamente cuando safety gate y ventana están en `READY`;
- auditoría runtime actualizada a esquema 2;
- paquete de sesión actualizado a `skm-polea-capture/3`;
- gate v56 integrado a GitHub Actions;
- publicación de alpha31.

### Excluido

- campaña física Samsung A15;
- campaña física Honor X5C;
- verificación de un BA real ejecutado en teléfono;
- lectura automática de temperatura;
- captura automática de fallos JNI/logcat;
- optimización de rotaciones e intrínsecos;
- BA global;
- calificación metrológica;
- `armeabi-v7a`.

## Cambios y decisiones

El builder vuelve a seleccionar los mismos frames utilizados por el reporte y regenera features deterministas para obtener las correspondencias necesarias. Para cada par candidato calcula homografía acotada y la compara con los inliers y RMS de la matriz fundamental ya producida por el pipeline.

La degradación visual no se infiere desde una etiqueta global. El desenfoque usa el `blurScore` guardado durante la captura; las altas luces y canales recortados se calculan desde los píxeles; la repetición se estima desde la distribución de distancias de descriptor, coincidencia mutua y cobertura espacial.

Una sesión con menos de 12 frames, 8 pares o 4 modelos resueltos conserva brechas explícitas. No se reemplazan valores faltantes por ceros favorables. Cuando la evidencia es completa, los valores llegan al safety gate; el coordinador solo invoca `LocalBundleAdjustmentCore` si además la ventana BA y los recursos son admisibles.

El gate sintético demuestra que una escena volumétrica segura puede alcanzar `READY` y que una escena planar, reflectante o repetitiva produce `BLOCKED`. No se presenta esta prueba como campaña física.

## Evidencia y validación

| Prueba o revisión | Entorno | Resultado | Evidencia |
|---|---|---|---|
| escena volumétrica completa | JDK | PASS | métricas completas y safety gate `READY` |
| escena planar | JDK | PASS | dominancia homográfica detectada |
| escena reflectante | JDK | PASS | fracción de reflejos supera puerta segura |
| patrón repetitivo | JDK | PASS | ratio, mutualidad y cobertura producen bloqueo |
| evidencia vacía | JDK | PASS | estado `INCOMPLETE`, valores no inventados |
| serialización | JDK | PASS | JSON incluye métricas y brechas |
| integración Android | GitHub Actions run `#605` | PASS | builder runtime y actividad compilan |
| gate producto completo | GitHub Actions run `#605` | PASS | 30 gates, Gradle, APK y cierre OCCT |
| AAR STEP | GitHub Actions run `#605` | PASS | SHA-256 esperado verificado |
| campaña Samsung A15 | hardware | NO EJECUTADA | no existe dispositivo conectado |
| campaña Honor X5C | hardware | NO EJECUTADA | no existe dispositivo conectado |
| BA runtime físico | hardware | NO EJECUTADO | requiere sesión segura real |
| metrología | instrumentos trazables | NO EJECUTADA | fuera de alcance |

## Resultados

- Las métricas suplementarias ya no dependen de valores manuales ni favorables.
- El safety gate puede quedar completo con evidencia runtime suficiente.
- Una escena volumétrica segura puede admitir el BA local.
- Planaridad, reflejos, desenfoque o repetición mantienen bloqueo explícito.
- La aplicación persiste las métricas suplementarias y las incluye en el ZIP.
- Alpha31 compila con STEP/OCCT para `arm64-v8a`.
- El artefacto `SKM-Polea-AI-CAD-STEP-v0.18.0-alpha31` fue publicado.
- No se declara una optimización física, precisión metrológica ni uso industrial.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Mitigación | Estado |
|---|---|---|---|---|
| PERF-001 | alta | frames y features se decodifican dos veces para ventana y métricas | caché compartida en ITER-014 | abierto |
| RUNTIME-001 | alta | no existe cancelación ni presupuesto temporal estricto | ITER-014 | abierto |
| DEVICE-001 | crítica | campaña Samsung A15 no ejecutada | prueba física | abierto |
| DEVICE-002 | crítica | campaña Honor X5C no ejecutada | prueba física | abierto |
| DEVICE-004 | alta | temperatura y fallos JNI no se capturan automáticamente | ITER-014 | abierto |
| BA-001 | alta | rotaciones e intrínsecos permanecen fijos | iteración posterior | abierto |
| BA-003 | media | no existe BA global | iteración posterior | abierto |
| VALID-001 | crítica | no existe metrología trazable | campaña R8 | abierto |
| DATA-007 | media | hashes reales de todos los PDF pendientes | auditoría documental | abierto |
| ABI-001 | media | OCCT solo `arm64-v8a` | evaluar nuevo AAR | abierto |

## Estado del roadmap

- Avance integral anterior: **78 %**.
- Avance integral después de ITER-013: **82 %**.
- Madurez funcional alpha estimada: **96 %**.
- Preparación industrial/metrológica estimada: **18 %**.

El incremento corresponde al cierre de las brechas visuales runtime y a una ruta completa de admisión del BA. La preparación industrial aumenta solo un punto porque no hubo hardware real, control térmico ni metrología.

## Siguiente iteración obligatoria

### ITER-014 — Caché runtime, cancelación y diagnóstico automático de dispositivo

- compartir decodificación, features e intrínsecos entre métricas y ventana BA;
- imponer presupuesto temporal y cancelación cooperativa;
- registrar estado térmico Android cuando esté disponible;
- registrar memoria periódica y eventos de error nativo/JNI;
- incorporar diagnósticos a la evidencia de campaña;
- probar timeout, cancelación y recuperación;
- publicar alpha32.

## Criterios de entrada y salida

### Entrada

- conservar los 30 gates;
- no relajar puertas visuales;
- mantener límites 8/120/1500 y cámara 0 fija;
- no declarar hardware no probado;
- mantener PR en borrador.

### Salida

- una sola preparación de frames alimenta métricas y BA;
- timeout y cancelación producen fallback seguro;
- diagnóstico térmico/memoria/JNI persistido automáticamente;
- evidencia de campaña consume los diagnósticos;
- alpha32 compilada;
- CI producto e historial exitosos;
- ITER-014 archivada;
- `CURRENT.md` actualizado con ITER-015.
