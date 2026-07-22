# Estado actual de iteraciones

**Actualizado:** 2026-07-22  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha39`

## Estado acumulado

- Alpha39 compila para `arm64-v8a` con STEP/OCCT.
- Evidencia dimensional, identificación, safety gate, pose graph y reconstrucción mantienen gates automáticos.
- La ventana BA permanece limitada a 8 cámaras, 120 puntos y 1500 observaciones con cámara global 0 fija.
- El BA base optimiza puntos y traslaciones con Huber y priors.
- Una segunda etapa corrige pequeñas rotaciones de cámaras secundarias con límites de 0,35° por paso y 3° acumulados.
- Una tercera etapa puede corregir una escala focal conjunta para `fx/fy` cuando existe observabilidad suficiente.
- La cámara 0, `cx`, `cy`, distorsión y razón `fx/fy` permanecen fijos.
- La observabilidad focal combina cantidad de observaciones, cobertura radial, diversidad de profundidad e información respecto del prior.
- Una escena focal mal condicionada conserva exactamente el resultado rotacional anterior.
- La sensibilidad focal se etiqueta `EMPIRICAL_FOCAL_SENSITIVITY_NOT_CALIBRATION`.
- Los residuos rotacionales se etiquetan `EMPIRICAL_RESIDUAL_INTERVAL_NOT_METROLOGICAL`.
- Antes de exportar, se verifican rutas, tamaños, archivos listados y SHA-256 de la generación activa.
- Una alteración, archivo inyectado o archivo faltante bloquea el ZIP de sesión.
- No existe todavía campaña física completada ni calificación industrial.

## Avance global estimado

- **Avance integral: 94 %.**
- **Madurez alpha: 99 %.**
- **Preparación industrial/metrológica: 25 %.**

El incremento corresponde a corrección focal acotada, observable y validada. La preparación industrial continúa baja porque Samsung A15, Honor X5C, calibración física e instrumentos trazables no fueron ejecutados.

## Iteración actual o última cerrada

### ITER-018 — Intrínsecos focales condicionados y observabilidad

Registro: `history/ITER-018_2026-07-22_conditioned-focal-ba-observability.md`

- corrección focal conjunta de `fx/fy` por cámara secundaria;
- cámara 0 y principal point fijos;
- razón `fx/fy` invariante;
- prior fuerte, damping y límites porcentuales;
- observabilidad radial y de profundidad;
- fallback exacto para escena mal condicionada;
- sensibilidad explícitamente no calibración;
- evidencia `runtime_focal_ba.json`;
- producto run `#729`: `success`;
- 35 gates Java;
- Gradle y cierre OCCT aprobados;
- APK alpha39 publicada;
- uso industrial continúa bloqueado.

## Bloqueos activos

1. `INTR-002`: observabilidad focal validada sintéticamente, no con cámaras físicas;
2. `BA-003`: no existe BA global ni Schur complement;
3. `STAT-001`: sensibilidad e intervalos no representan incertidumbre física o dimensional;
4. `RUNTIME-003`: fundamental, pose, triangulación y Bitmap decode no tienen todos los checkpoints internos;
5. `TX-001`: no existe journal coordinado entre SQLite y filesystem;
6. `SIGN-001`: la huella de evidencia no está firmada por una identidad corporativa;
7. `DEVICE-001`: campaña Samsung A15 no ejecutada;
8. `DEVICE-002`: campaña Honor X5C no ejecutada;
9. `VALID-001`: metrología trazable ausente;
10. `DATA-007`: hashes reales de todos los PDF pendientes;
11. `ABI-001`: OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-019 — Checkpoints geométricos y journal coordinado

Instrumentar cancelación dentro de fundamental, pose y triangulación; coordinar estados SQLite/filesystem mediante un journal recuperable; impedir que un fallo posterior invalide una generación ya publicada; probar recuperación, rollback y evidencia incompleta.

## Criterios de entrada

- conservar los 35 gates previos;
- mantener límites 8/120/1500 y cámara 0 fija;
- conservar fallback rotacional/focal e integridad de exportación;
- no declarar campañas físicas no ejecutadas;
- mantener PR en borrador.

## Criterios de salida

- checkpoints geométricos internos verificados;
- journal coordinado y recuperable;
- recuperación de generación publicada probada;
- evidencia incompleta bloqueada;
- nueva alpha compilada;
- CI producto e historial exitosos;
- ITER-019 archivada.