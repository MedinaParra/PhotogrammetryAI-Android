# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha28`

## Estado acumulado

- Alpha28 compila para `arm64-v8a` con STEP/OCCT.
- Evidencia dimensional, identificación, safety gate, BA local, productores visuales y pose graph tienen gates sintéticos.
- La puerta profesional separa alpha, piloto e industria.
- No existe calificación metrológica industrial.

## Avance global estimado

- **Avance integral: 70 %.**
- **Madurez alpha: 91 %.**
- **Preparación industrial/metrológica: 15 %.**

## Iteración actual o última cerrada

### ITER-010 — Puerta profesional de calificación y campañas

Registro: `history/ITER-010_2026-07-21_engineering-qualification-gate.md`

- 27 gates Java;
- producto run `#530`: `success`;
- historial run `#104`: `success`;
- APK alpha28 publicada;
- uso industrial bloqueado.

## Bloqueos activos

1. integración runtime pendiente;
2. campañas Samsung A15/Honor X5C no ejecutadas;
3. metrología trazable ausente;
4. corpus STEP real insuficiente;
5. OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-011 — Integración runtime completa y campaña física inicial

Conectar el pipeline e iniciar campañas físicas con evidencia real.

## Criterios de entrada

- conservar 27 gates;
- hashes reales;
- no usar sintéticos como evidencia física;
- PR en borrador.

## Criterios de salida

- integración o bloqueos documentados;
- campañas reales o fallos explícitos;
- métricas de recursos/JNI;
- corpus STEP iniciado;
- CI e historial exitosos.
