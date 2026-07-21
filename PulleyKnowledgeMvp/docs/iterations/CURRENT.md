# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha31`

## Estado acumulado

- Alpha31 compila para `arm64-v8a` con STEP/OCCT.
- Evidencia dimensional, identificación, safety gate, BA local, productores visuales y pose graph mantienen gates automáticos.
- La aplicación construye una ventana BA desde poses, tracks, puntos fusionados y coordenadas de píxel reales.
- La ventana permanece limitada a 8 cámaras, 120 puntos y 1500 observaciones, con cámara global 0 fija.
- Competencia homografía/fundamental, desenfoque, altas luces, canales recortados y ambigüedad repetitiva se calculan desde frames y pares runtime.
- El safety gate puede quedar completo y solo admite BA cuando la escena es volumétrica, la calidad es suficiente, la ventana es válida y existen recursos.
- Planaridad, reflejos, desenfoque, repetición o evidencia insuficiente producen `REVIEW` o `BLOCKED`.
- La sesión persiste métricas suplementarias, ventana BA, telemetría y auditoría, y las exporta dentro del ZIP.
- No existe todavía campaña física completada ni calificación metrológica industrial.

## Avance global estimado

- **Avance integral: 82 %.**
- **Madurez alpha: 96 %.**
- **Preparación industrial/metrológica: 18 %.**

El incremento corresponde al cierre de las brechas visuales runtime y a una ruta completa de admisión del BA. La preparación industrial sigue baja porque Samsung A15, Honor X5C, temperatura automática e instrumentos trazables no fueron probados.

## Iteración actual o última cerrada

### ITER-013 — Métricas suplementarias runtime y ejecución BA admitida

Registro: `history/ITER-013_2026-07-21_runtime-supplemental-metrics-admitted-ba.md`

- competencia homografía/fundamental conectada a pares reales;
- desenfoque y reflejos calculados desde frames reales;
- repetición evaluada mediante descriptor, mutualidad y cobertura;
- evidencia incompleta conserva brechas explícitas;
- escena volumétrica segura alcanza `READY` en gate v56;
- escena planar/reflectante/repetitiva queda `BLOCKED`;
- paquete runtime actualizado;
- producto run `#605`: `success`;
- 30 gates Java;
- APK alpha31 publicada;
- uso industrial continúa bloqueado.

## Bloqueos activos

1. `PERF-001`: frames y features se preparan dos veces para métricas y BA;
2. `RUNTIME-001`: no existe cancelación ni presupuesto temporal estricto;
3. `DEVICE-001`: campaña Samsung A15 no ejecutada;
4. `DEVICE-002`: campaña Honor X5C no ejecutada;
5. `DEVICE-004`: temperatura y eventos JNI no se capturan automáticamente;
6. `BA-001`: rotaciones e intrínsecos no se optimizan;
7. `BA-003`: no existe BA global;
8. `VALID-001`: metrología trazable ausente;
9. `DATA-007`: hashes reales de todos los PDF pendientes;
10. `ABI-001`: OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-014 — Caché runtime, cancelación y diagnóstico automático de dispositivo

Compartir decodificación, features e intrínsecos entre métricas y ventana BA; imponer timeout y cancelación cooperativa; registrar estado térmico, memoria y eventos nativos disponibles; incorporar los diagnósticos a la evidencia de campaña.

## Criterios de entrada

- conservar los 30 gates previos;
- no relajar puertas visuales;
- mantener límites 8/120/1500 y cámara 0 fija;
- no declarar campañas físicas no ejecutadas;
- mantener PR en borrador.

## Criterios de salida

- preparación compartida de frames para métricas y BA;
- timeout y cancelación producen fallback seguro;
- diagnóstico térmico, memoria y JNI persistido automáticamente;
- evidencia de campaña consume diagnósticos;
- alpha32 compilada;
- CI producto e historial exitosos;
- ITER-014 archivada;
- este archivo actualizado con ITER-015.
