# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha28`

## Estado acumulado

- Alpha28 compila para `arm64-v8a` con STEP/OCCT.
- Evidencia dimensional, identificación, safety gate, BA local, productores visuales y refinamiento del pose graph están cubiertos por pruebas sintéticas.
- Existe una puerta profesional para separar alpha, piloto e industria.
- Campañas no ejecutadas permanecen como bloqueo.
- No existe calificación metrológica industrial.

## Avance global estimado

- **Avance integral R0–R8: 70 %.**
- **Madurez funcional alpha: 91 %.**
- **Preparación industrial/metrológica: 15 %.**

## Iteración actual o última cerrada

### ITER-010 — Puerta profesional de calificación y campañas

Registro: `history/ITER-010_2026-07-21_engineering-qualification-gate.md`

- 27 gates Java aprobados;
- producto run `#530`: `success`;
- historial run `#104`: `success`;
- APK alpha28 publicada;
- calificación industrial bloqueada por evidencia física pendiente.

## Bloqueos activos

1. integración runtime completa pendiente;
2. Samsung A15 no ensayado;
3. Honor X5C no ensayado;
4. metrología trazable ausente;
5. corpus STEP real insuficiente;
6. OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-011 — Integración runtime completa y campaña física inicial

Conectar los componentes al pipeline, instrumentar recursos y ejecutar campañas reales sin alterar la puerta industrial.

## Criterios de entrada

- conservar 27 gates;
- usar hashes reales;
- no usar sintéticos como evidencia física;
- PR en borrador.

## Criterios de salida

- integración runtime o bloqueos documentados;
- campañas en ambos teléfonos o fallos explícitos;
- métricas térmicas/memoria/JNI;
- corpus STEP iniciado;
- CI e historial exitosos.
