# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha32`

## Estado acumulado

- Alpha32 compila para `arm64-v8a` con STEP/OCCT.
- Evidencia dimensional, identificación, safety gate, BA local, productores visuales y pose graph mantienen gates automáticos.
- La aplicación construye una ventana BA desde poses, tracks, puntos fusionados y píxeles reales, limitada a 8 cámaras, 120 puntos y 1500 observaciones con cámara global 0 fija.
- Competencia homografía/fundamental, desenfoque, reflejos y ambigüedad repetitiva gobiernan la admisión del BA.
- Métricas y ventana BA reutilizan una sola preparación post-reporte de frames, gray, features e intrínsecos.
- El runtime dispone de deadline monotónico de 180 segundos y cancelación visible con fallback sin optimizar.
- Caché, métricas, ventana, telemetría, diagnóstico, auditoría y aborto se guardan en la sesión y se exportan dentro del ZIP.
- El diagnóstico automático registra temperatura de batería, estado térmico Android, PSS, heap, RAM y salidas nativas disponibles; no equivale a temperatura de CPU ni metrología.
- La campaña física consume el diagnóstico automático y conserva estados `READY`, `REVIEW` y `BLOCKED`.
- No existe todavía campaña física completada ni calificación metrológica industrial.

## Avance global estimado

- **Avance integral: 86 %.**
- **Madurez alpha: 97 %.**
- **Preparación industrial/metrológica: 20 %.**

El incremento corresponde a robustez runtime, caché compartida, cancelación fail-closed y diagnóstico automático. La preparación industrial sigue baja porque Samsung A15, Honor X5C e instrumentos trazables no fueron ejecutados.

## Iteración actual o última cerrada

### ITER-014 — Caché runtime, cancelación y diagnóstico automático de dispositivo

Registro: `history/ITER-014_2026-07-21_runtime-cache-cancellation-device-diagnostics.md`

- preparación post-reporte compartida entre métricas y BA;
- deadline de 180 segundos;
- cancelación visible y checkpoints cooperativos;
- fallback obligatorio ante timeout/interrupción;
- diagnóstico Android sin permisos privilegiados;
- campaña enriquecida con evidencia automática;
- paquete de sesión actualizado a esquema 4;
- gate v57 incorporado;
- producto run `#643`: `success`;
- 31 gates Java;
- APK alpha32 publicada;
- uso industrial continúa bloqueado.

## Bloqueos activos

1. `RUNTIME-002`: `SessionOverlapAnalyzer` no posee checkpoints dentro de sus bucles internos;
2. `RECOVERY-001`: resultados parciales no se promueven mediante commit transaccional de sesión;
3. `DEVICE-001`: campaña Samsung A15 no ejecutada;
4. `DEVICE-002`: campaña Honor X5C no ejecutada;
5. `DEVICE-005`: temperatura automática corresponde a batería/estado térmico abstracto;
6. `JNI-001`: historial de salidas nativas no constituye cobertura JNI exhaustiva;
7. `BA-001`: rotaciones e intrínsecos no se optimizan;
8. `BA-003`: no existe BA global;
9. `VALID-001`: metrología trazable ausente;
10. `DATA-007`: hashes reales de todos los PDF pendientes;
11. `ABI-001`: OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-015 — Checkpoints profundos, recuperación transaccional y campaña reproducible

Introducir cancelación dentro del analizador multivista, promover archivos de forma atómica, invalidar evidencia parcial y generar un manifiesto de campaña que vincule APK, dispositivo, sesión y hashes.

## Criterios de entrada

- conservar los 31 gates previos;
- no relajar puertas visuales;
- mantener límites 8/120/1500 y cámara 0 fija;
- no declarar campañas físicas no ejecutadas;
- mantener PR en borrador.

## Criterios de salida

- checkpoints dentro de selección, decodificación y bucles de pares;
- timeout/cancelación observados durante análisis multivista;
- archivos temporales promovidos atómicamente;
- evidencia parcial nunca reutilizada como vigente;
- manifiesto de campaña reproducible exportado;
- alpha33 compilada;
- CI producto e historial exitosos;
- ITER-015 archivada;
- este archivo actualizado con ITER-016.