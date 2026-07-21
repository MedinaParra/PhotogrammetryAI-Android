# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha30`

## Estado acumulado

- Alpha30 compila para `arm64-v8a` con STEP/OCCT.
- Evidencia dimensional, identificación, safety gate, BA local, productores visuales y pose graph mantienen gates sintéticos.
- La validación runtime está expuesta dentro del producto.
- La aplicación construye una ventana BA desde poses, tracks, puntos fusionados y coordenadas de píxel de una sesión real.
- La ventana está limitada a 8 cámaras, 120 puntos y 1500 observaciones, con cámara global 0 fija.
- Se serializan matrices, intrínsecos, puntos, observaciones y SHA-256 reproducible.
- Duración, heap, PSS, memoria disponible y almacenamiento se registran automáticamente.
- El ZIP de sesión incluye `runtime_ba_window.json`, `runtime_telemetry.json` y `runtime_audit.json`.
- Las métricas suplementarias de homografía, reflejos y repetición todavía llegan como desconocidas; el safety gate conserva `REVIEW` y evita una falsa optimización.
- No existe todavía campaña física completada ni calificación metrológica industrial.

## Avance global estimado

- **Avance integral: 78 %.**
- **Madurez alpha: 95 %.**
- **Preparación industrial/metrológica: 17 %.**

El incremento se atribuye a la ventana BA real, serialización completa, telemetría automática y exportación auditable. La preparación industrial solo sube un punto porque Samsung A15, Honor X5C, temperatura automática e instrumentos trazables no fueron ejecutados.

## Iteración actual o última cerrada

### ITER-012 — Ventana BA runtime y telemetría automática

Registro: `history/ITER-012_2026-07-21_runtime-ba-window-telemetry.md`

- problema BA construido desde evidencia runtime;
- límites 8/120/1500 y cámara 0 fija;
- serialización completa y huella SHA-256;
- telemetría automática de duración, heap, PSS, memoria y almacenamiento;
- auditoría runtime incluida en el paquete de sesión;
- gate v55 incorporado;
- producto run `#579`: `success`;
- APK alpha30 publicada;
- uso industrial continúa bloqueado.

## Bloqueos activos

1. `VISION-003`: métricas suplementarias no conectadas todavía al flujo runtime;
2. `BA-001`: rotaciones e intrínsecos no se optimizan;
3. `BA-003`: no existe BA global;
4. `DEVICE-001`: campaña Samsung A15 no ejecutada;
5. `DEVICE-002`: campaña Honor X5C no ejecutada;
6. `DEVICE-004`: temperatura y fallos JNI no se capturan automáticamente;
7. `VALID-001`: metrología trazable ausente;
8. `DATA-007`: hashes reales de todos los PDF pendientes;
9. `ABI-001`: OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-013 — Métricas suplementarias runtime y ejecución BA admitida

Conectar competencia homografía/fundamental, desenfoque, reflejos y repetición a los pares y frames reales; alimentar el safety gate completo y ejecutar BA únicamente cuando toda la evidencia produzca `READY`.

## Criterios de entrada

- conservar los 29 gates previos;
- mantener límites 8/120/1500 y cámara 0 fija;
- no sustituir métricas faltantes por valores favorables;
- no declarar campañas físicas no ejecutadas;
- mantener PR en borrador.

## Criterios de salida

- métricas suplementarias producidas desde pares y frames runtime;
- safety gate completo sin brechas cuando existe evidencia suficiente;
- BA ejecutado solamente en un caso `READY`;
- escenas degeneradas producen `REVIEW` o `BLOCKED`;
- métricas antes/después y fallback exportados;
- alpha31 compilada;
- CI producto e historial exitosos;
- ITER-013 archivada;
- este archivo actualizado con ITER-014.
