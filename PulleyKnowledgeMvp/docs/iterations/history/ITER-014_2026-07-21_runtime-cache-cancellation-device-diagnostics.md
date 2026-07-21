# ITER-014 — Caché runtime, cancelación y diagnóstico automático de dispositivo

## Identificación

- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8` (borrador)
- **Versión:** `0.18.0-alpha32`
- **Commit funcional validado:** `c8158f463d5ac286e7f8601ae89e0fd3d78cd5ce`
- **Fases:** R4, R5, R6 y R8

## Objetivo

Eliminar la doble preparación de imágenes entre métricas suplementarias y ventana BA; introducir cancelación y deadline fail-closed; recolectar diagnósticos Android disponibles sin permisos privilegiados y vincularlos a la evidencia de campaña, sin presentar temperatura de batería ni estado térmico como metrología.

## Alcance ejecutado

### Incluido

- `RuntimeFramePreparationCache` compartido por métricas y ventana BA;
- una decodificación a escala de análisis por frame seleccionado;
- reutilización de features e intrínsecos dentro del tramo posterior al reporte;
- persistencia `runtime_frame_cache.json`;
- `RuntimeExecutionControlCore` con estados `RUNNING`, `CANCELLED` y `TIMED_OUT`;
- deadline monotónico de 180 segundos;
- checkpoints por preparación, frame, par, track y antes del BA;
- botón visible para cancelar y conservar fallback;
- `runtime_abort.json` con fase, razón y prohibición de aceptar geometría optimizada;
- `DeviceDiagnosticsCore` independiente de Android;
- `AndroidDeviceDiagnosticsCollector` para temperatura de batería, estado térmico del sistema, PSS, heap, RAM disponible y salidas nativas históricas cuando el SDK lo permite;
- lectura opcional de eventos locales desde `native-events.log`;
- diagnóstico automático consumido por `InitialDeviceCampaignCore`;
- campaña `REVIEW` cuando el diagnóstico es parcial y `BLOCKED` cuando falta o existen eventos nativos;
- persistencia separada de campaña y diagnóstico;
- exportación de caché, aborto y diagnóstico dentro del ZIP de sesión;
- esquema de paquete `skm-polea-capture/4`;
- gate v57 integrado a GitHub Actions;
- publicación de alpha32.

### Excluido

- checkpoints internos dentro del bucle de pares de `SessionOverlapAnalyzer`;
- cancelación inmediata mientras ese analizador monolítico está ejecutándose;
- medición de temperatura de CPU, GPU o SoC;
- termografía o metrología térmica trazable;
- captura universal de todos los fallos JNI en Android anteriores a API 30;
- campañas físicas Samsung A15 y Honor X5C;
- optimización de rotaciones e intrínsecos;
- BA global;
- calificación metrológica;
- `armeabi-v7a`.

## Cambios y decisiones

La preparación compartida comienza después de obtener `SessionOverlapAnalyzer.Report`. Los mismos frames seleccionados se decodifican una vez y producen gray, fracciones de altas luces/canales recortados, features e intrínsecos. El builder suplementario y el builder BA consumen esa misma caché, eliminando la duplicación introducida en ITER-013.

El control usa tiempo monotónico y checkpoints cooperativos. Una cancelación del operador, interrupción del thread o deadline produce `REVIEW`, pasa `problem=null` al coordinador, persiste `runtime_abort.json` y conserva solamente la geometría no optimizada. No existe una ruta que etiquete el fallback como BA aceptado.

La cancelación todavía no entra en el bucle interno de `SessionOverlapAnalyzer.analyze()`. Si la solicitud ocurre mientras esa función está procesando pares, será observada al retornar al siguiente checkpoint. Esto mantiene seguridad fail-closed, pero no garantiza latencia inmediata de cancelación.

El diagnóstico utiliza únicamente APIs disponibles sin privilegios. `ACTION_BATTERY_CHANGED` entrega temperatura de batería y `PowerManager` entrega nivel térmico abstracto; ninguno equivale a temperatura del procesador ni a una medición trazable. `ApplicationExitInfo.REASON_CRASH_NATIVE` solo está disponible en Android 11/API 30 o superior y representa historial del sistema, no instrumentación JNI exhaustiva.

## Evidencia y validación

| Prueba o revisión | Entorno | Resultado | Evidencia |
|---|---|---|---|
| deadline monotónico | JDK | PASS | checkpoint posterior al límite produce `TIMED_OUT` |
| cancelación de usuario | JDK | PASS | siguiente checkpoint produce `CANCELLED` |
| fallback por interrupción | JDK | PASS | decisión `REVIEW`, sin geometría optimizada |
| diagnóstico completo | JDK | PASS | temperatura, térmico, PSS, RAM e historial disponibles |
| diagnóstico parcial | JDK | PASS | brechas explícitas y campaña `REVIEW` |
| evento nativo | JDK | PASS | campaña `BLOCKED` |
| primer intento alpha32 | GitHub Actions run `#635` | FAIL CONTROLADO | 31 gates pasaron; Gradle detectó adaptador ausente y conversión `long`/`int` |
| segundo intento alpha32 | GitHub Actions run `#639` | FAIL CONTROLADO | regresión de dependencia en gate puro v56; Gradle no ejecutado |
| gate producto final | GitHub Actions run `#643` | PASS | 31 gates, Gradle, APK y cierre OCCT |
| historial previo al cierre | GitHub Actions run `#179` | PASS | estructura de iteraciones válida |
| AAR STEP | GitHub Actions run `#643` | PASS | SHA-256 esperado y 25 bibliotecas nativas |
| campaña Samsung A15 | hardware | NO EJECUTADA | no existe dispositivo conectado |
| campaña Honor X5C | hardware | NO EJECUTADA | no existe dispositivo conectado |
| metrología | instrumentos trazables | NO EJECUTADA | fuera de alcance |

## Resultados

- Métricas suplementarias y ventana BA reutilizan una preparación compartida.
- El runtime dispone de deadline y cancelación visible con fallback seguro.
- Caché, telemetría, auditoría, diagnóstico y aborto quedan exportables.
- La campaña física consume diagnóstico automático y conserva brechas.
- Alpha32 compila con STEP/OCCT para `arm64-v8a`.
- Se publicó `SKM-Polea-AI-CAD-STEP-v0.18.0-alpha32`.
- No se declara campaña física, temperatura de CPU, precisión metrológica ni uso industrial.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Mitigación | Estado |
|---|---|---|---|---|
| RUNTIME-002 | alta | `SessionOverlapAnalyzer` no posee checkpoints internos | ITER-015 | abierto |
| RECOVERY-001 | alta | los artefactos parciales no usan commit transaccional de sesión | ITER-015 | abierto |
| DEVICE-001 | crítica | campaña Samsung A15 no ejecutada | prueba física | abierto |
| DEVICE-002 | crítica | campaña Honor X5C no ejecutada | prueba física | abierto |
| DEVICE-005 | media | temperatura disponible es de batería/estado térmico abstracto | campaña con instrumentación externa | abierto |
| JNI-001 | alta | historial nativo es parcial en SDK antiguos y no cubre todo JNI | registro nativo estructurado | abierto |
| BA-001 | alta | rotaciones e intrínsecos permanecen fijos | iteración posterior | abierto |
| BA-003 | media | no existe BA global | iteración posterior | abierto |
| VALID-001 | crítica | no existe metrología trazable | campaña R8 | abierto |
| DATA-007 | media | hashes reales de todos los PDF pendientes | auditoría documental | abierto |
| ABI-001 | media | OCCT solo `arm64-v8a` | evaluar nuevo AAR | abierto |

## Estado del roadmap

- Avance integral anterior: **82 %**.
- Avance integral después de ITER-014: **86 %**.
- Madurez funcional alpha estimada: **97 %**.
- Preparación industrial/metrológica estimada: **20 %**.

El incremento corresponde a robustez runtime, eliminación de una duplicación y evidencia automática de dispositivo. La preparación industrial aumenta poco porque no hubo campaña en hardware objetivo ni mediciones trazables.

## Siguiente iteración obligatoria

### ITER-015 — Checkpoints profundos, recuperación transaccional y campaña reproducible

- introducir checkpoints dentro de selección, decodificación y bucles de pares del analizador;
- propagar cancelación hasta estimación fundamental, pose y triangulación;
- escribir resultados en archivos temporales y promoverlos de forma atómica;
- impedir que una sesión cancelada reutilice evidencia parcial como vigente;
- generar un manifiesto reproducible de campaña con APK, dispositivo, sesión y hashes;
- probar cancelación durante análisis, recuperación tras cierre y reanudación segura;
- publicar alpha33.

## Criterios de entrada y salida

### Entrada

- conservar los 31 gates;
- no relajar safety gate ni límites 8/120/1500;
- conservar cámara 0 fija;
- no declarar hardware no probado;
- mantener PR en borrador.

### Salida

- cancelación observada dentro del analizador multivista;
- ningún archivo parcial se presenta como resultado vigente;
- recuperación transaccional probada;
- manifiesto de campaña reproducible exportado;
- alpha33 compilada;
- CI producto e historial exitosos;
- ITER-015 archivada;
- `CURRENT.md` actualizado con ITER-016.