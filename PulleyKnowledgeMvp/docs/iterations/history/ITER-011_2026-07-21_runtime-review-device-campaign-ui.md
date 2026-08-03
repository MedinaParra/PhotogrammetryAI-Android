# ITER-011 — Integración runtime visible y campaña física inicial

## Identificación

- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8` (borrador)
- **Versión:** `0.18.0-alpha29`
- **Commit funcional de cierre:** `57738959da69a8a6b79ccbff92b73d9113d3df0e`
- **Fases:** R4, R5, R6 y R8

## Objetivo

Exponer dentro de la aplicación la decisión runtime fail-closed y crear una vía local para registrar evidencia obtenida en campañas físicas de Samsung A15 u Honor X5C, sin convertir pruebas sintéticas en evidencia real.

## Alcance ejecutado

### Incluido

- pantalla `RuntimeReviewActivity`;
- ejecución de `SessionOverlapAnalyzer` sobre una sesión real almacenada;
- paso del reporte al `RuntimeReconstructionCoordinator`;
- auditoría append-only de la decisión;
- fallback explícito cuando no existe una ventana BA serializada;
- pantalla `DeviceCampaignActivity`;
- formulario de duración, temperatura, RSS, fallos JNI, STEP, reconstrucciones y recuperación;
- validación fail-closed mediante `InitialDeviceCampaignCore`;
- persistencia local en JSON;
- acceso a ambas pantallas desde `LauncherActivity`;
- gate v54 integrado a GitHub Actions.

### Excluido

- campaña física ejecutada en un teléfono;
- lectura automática de temperatura o RSS;
- captura automática de logcat/JNI;
- ventana BA construida desde observaciones runtime;
- calificación metrológica;
- compatibilidad `armeabi-v7a`.

## Cambios y decisiones

La validación runtime ahora es accesible desde el producto. Si la sesión no existe, el análisis falla cerrado. Si existe, el reporte real se evalúa con el safety gate y la política de publicación. Como las observaciones BA todavía no están serializadas por `SessionOverlapAnalyzer.Report`, el flujo conserva geometría no optimizada como fallback y lo declara explícitamente.

La campaña inicial exige como mínimo una duración, medición de temperatura y memoria, ausencia de fallos JNI, importaciones STEP, reconstrucciones, recuperación tras interrupción y verificación del hash de APK. Los umbrales no representan metrología; son una puerta de estabilidad para piloto técnico.

## Evidencia y validación

| Prueba o revisión | Entorno | Resultado | Evidencia |
|---|---|---|---|
| campaña READY | JDK | PASS | 35 min, 41,5 °C, 980 MB, 0 JNI, 6 STEP, 4 reconstrucciones |
| campaña REVIEW | JDK | PASS | métricas marginales producen advertencias |
| campaña BLOCKED | JDK | PASS | hash, temperatura, memoria, JNI y recuperación bloquean |
| JSON canónico | JDK | PASS | comillas escapadas y estado serializado |
| pantallas Android | GitHub Actions | PASS | compilación alpha29 y manifest válidos |
| gate producto completo | GitHub Actions run `#557` | PASS | 28 gates, Gradle, APK, cierre OCCT |
| historial | GitHub Actions run `#127` | PASS | registro y siguiente iteración válidos |
| campaña Samsung A15 | hardware | NO EJECUTADA | no hay dispositivo conectado |
| campaña Honor X5C | hardware | NO EJECUTADA | no hay dispositivo conectado |
| metrología | instrumentos trazables | NO EJECUTADA | fuera de alcance |

## Resultados

- El operador puede ejecutar una revisión runtime desde la aplicación.
- El producto muestra por qué una sesión usa fallback o queda bloqueada.
- Existe un formulario estructurado para iniciar campañas físicas reales.
- La evidencia se guarda localmente como JSON y no se mezcla con resultados sintéticos.
- Alpha29 compila con STEP/OCCT para `arm64-v8a`.
- Se publicó el artefacto `SKM-Polea-AI-CAD-STEP-v0.18.0-alpha29`.
- No se declara que exista una campaña física completada.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Mitigación | Estado |
|---|---|---|---|---|
| BA-002 | alta | falta serializar observaciones para BA runtime | ITER-012 | abierto |
| DEVICE-001 | crítica | no se ejecutó campaña Samsung A15 | prueba física | abierto |
| DEVICE-002 | crítica | no se ejecutó campaña Honor X5C | prueba física | abierto |
| DEVICE-003 | alta | temperatura/RSS se ingresan manualmente | instrumentación automática | abierto |
| VALID-001 | crítica | no existe metrología trazable | campaña R8 | abierto |
| ABI-001 | media | OCCT solo arm64-v8a | evaluar nuevo AAR | abierto |

## Estado del roadmap

Avance integral después de ITER-011: **74 %**. La madurez alpha es **93 %** y la preparación industrial/metrológica permanece en **16 %** porque no hubo hardware ni instrumentos trazables.

## Siguiente iteración obligatoria

### ITER-012 — Ventana BA runtime y telemetría automática

- serializar cámaras, puntos y observaciones desde el reporte;
- construir problemas BA acotados desde sesiones reales;
- medir duración, RSS y errores automáticamente;
- exportar auditoría runtime y campaña en el paquete de sesión;
- conservar fallback si el problema no es admisible;
- publicar alpha30.

## Criterios de entrada y salida

### Entrada

- conservar todos los gates previos;
- no declarar campañas no ejecutadas;
- no permitir geometría optimizada sin observaciones reales;
- mantener PR en borrador.

### Salida

- ventana BA creada desde datos runtime;
- telemetría persistida automáticamente;
- paquete de sesión contiene auditoría y campaña;
- CI producto e historial exitosos;
- ITER-012 archivada;
- `CURRENT.md` actualizado con ITER-013.
