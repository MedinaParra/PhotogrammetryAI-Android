# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha29`

## Estado acumulado

- Alpha29 compila para `arm64-v8a` con STEP/OCCT.
- Evidencia dimensional, identificación, safety gate, BA local, productores visuales y pose graph mantienen gates sintéticos.
- La validación runtime ya está expuesta en una pantalla del producto.
- Existe un formulario fail-closed para registrar campañas físicas reales y guardar evidencia JSON local.
- La falta de una ventana BA serializada se declara como fallback, no como optimización.
- No existe todavía campaña física completada ni calificación metrológica industrial.

## Avance global estimado

- **Avance integral: 74 %.**
- **Madurez alpha: 93 %.**
- **Preparación industrial/metrológica: 16 %.**

El incremento se atribuye a integración visible, auditoría runtime y preparación de campañas. La preparación industrial solo sube un punto porque Samsung A15, Honor X5C e instrumentos trazables no fueron ejecutados.

## Iteración actual o última cerrada

### ITER-011 — Integración runtime visible y campaña física inicial

Registro: `history/ITER-011_2026-07-21_runtime-review-device-campaign-ui.md`

- pantalla de revisión runtime;
- safety gate y decisión persistente accesibles desde la app;
- fallback explícito cuando falta BA real;
- formulario de campaña física;
- evidencia JSON local;
- gate v54 incorporado;
- producto run `#557`: `success`;
- historial run `#127`: `success`;
- APK alpha29 publicada.

## Bloqueos activos

1. `BA-002`: observaciones runtime aún no serializadas para construir BA real;
2. `DEVICE-001`: campaña Samsung A15 no ejecutada;
3. `DEVICE-002`: campaña Honor X5C no ejecutada;
4. `DEVICE-003`: temperatura y RSS todavía se ingresan manualmente;
5. `VALID-001`: metrología trazable ausente;
6. `DATA-007`: hashes reales de todos los PDF pendientes;
7. `ABI-001`: OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-012 — Ventana BA runtime y telemetría automática

Construir el problema BA desde cámaras, puntos y observaciones reales; medir recursos automáticamente y exportar la auditoría completa dentro del paquete de sesión.

## Criterios de entrada

- conservar todos los gates previos;
- no declarar campañas no ejecutadas;
- no optimizar sin observaciones runtime;
- mantener cámara 0 fija y límites de memoria;
- PR en borrador.

## Criterios de salida

- problema BA real construido desde el pipeline;
- telemetría automática de duración y memoria;
- auditoría runtime exportada;
- fallback seguro probado;
- alpha30 compilada;
- CI producto e historial exitosos;
- ITER-012 archivada;
- este archivo actualizado con ITER-013.
