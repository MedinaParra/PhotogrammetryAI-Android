# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha34`

## Estado acumulado

- Alpha34 compila para `arm64-v8a` con STEP/OCCT.
- Evidencia dimensional, identificación, safety gate, pose graph y reconstrucción mantienen gates automáticos.
- La ventana BA permanece limitada a 8 cámaras, 120 puntos y 1500 observaciones con cámara global 0 fija.
- El BA base optimiza puntos y traslaciones con Huber y priors.
- Una segunda etapa puede corregir pequeñas rotaciones en cámaras distintas de la cámara 0.
- Los pasos rotacionales están limitados a 0,35° y el desplazamiento acumulado a 3° por cámara.
- La etapa rotacional solo se aplica cuando mejora costo robusto y RMS, conserva profundidad positiva y mantiene el gauge exacto.
- Cuando la etapa rotacional no es admisible se conserva el resultado base exacto.
- Los residuos publican mediana, MAD, P90 e intervalo empírico 2,5–97,5 % en píxeles.
- Toda estadística rotacional se etiqueta `EMPIRICAL_RESIDUAL_INTERVAL_NOT_METROLOGICAL`.
- Métricas visuales, cancelación profunda parcial, generaciones transaccionales y manifiestos reproducibles permanecen activas.
- El exportador solo incluye la generación comprometida vigente.
- No existe todavía campaña física completada ni calificación industrial.

## Avance global estimado

- **Avance integral: 92 %.**
- **Madurez alpha: 99 %.**
- **Preparación industrial/metrológica: 24 %.**

El incremento corresponde al refinamiento rotacional acotado y auditable. La preparación industrial sigue baja porque Samsung A15, Honor X5C, calibración física e instrumentos trazables no fueron ejecutados.

## Iteración actual o última cerrada

### ITER-016 — Rotaciones BA acotadas e intervalos estadísticos de residuos

Registro: `history/ITER-016_2026-07-21_bounded-rotational-ba-statistical-residuals.md`

- segunda etapa posterior al BA estable;
- cámara 0 fija en rotación y traslación;
- centros de cámara preservados durante propuestas;
- damping, prior rotacional y límites 0,35°/3°;
- aceptación solo con mejora geométrica integral;
- fallback exacto al BA base;
- estadística empírica explícitamente no metrológica;
- evidencia `runtime_rotational_ba.json`;
- auditoría runtime esquema 5;
- gate v59 incorporado;
- producto run `#688`: `success`;
- 33 gates Java;
- APK alpha34 publicada;
- uso industrial continúa bloqueado.

## Bloqueos activos

1. `INTR-001`: intrínsecos permanecen fijos y dependen de Camera2/perfiles no validados físicamente;
2. `BA-003`: no existe BA global ni Schur complement;
3. `STAT-001`: intervalos de residuos no representan incertidumbre física o dimensional;
4. `RUNTIME-003`: fundamental, pose, triangulación y Bitmap decode no tienen checkpoints internos;
5. `TX-001`: no existe transacción coordinada entre SQLite y filesystem;
6. `SIGN-001`: la huella de evidencia no está firmada por una identidad corporativa;
7. `DEVICE-001`: campaña Samsung A15 no ejecutada;
8. `DEVICE-002`: campaña Honor X5C no ejecutada;
9. `VALID-001`: metrología trazable ausente;
10. `DATA-007`: hashes reales de todos los PDF pendientes;
11. `ABI-001`: OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-017 — Intrínsecos focales condicionados y observabilidad

Permitir una corrección focal pequeña y fuertemente priorizada, mantener principal point y distorsión fijos, bloquear problemas mal condicionados y conservar fallback al BA rotacional/base.

## Criterios de entrada

- conservar los 33 gates previos;
- mantener límites 8/120/1500 y cámara 0 fija;
- conservar generaciones transaccionales y manifiestos reproducibles;
- no optimizar `cx`, `cy` ni distorsión;
- no declarar perfiles físicos no validados;
- mantener PR en borrador.

## Criterios de salida

- corrección focal acotada implementada;
- observabilidad y límites verificados;
- aceptación exige mejora geométrica sin absorber error de pose;
- fallback por mala condición probado;
- sensibilidad etiquetada como estadística no metrológica;
- alpha35 compilada;
- CI producto e historial exitosos;
- ITER-017 archivada;
- este archivo actualizado con ITER-018.