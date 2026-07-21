# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha28`

## Estado acumulado

- La aplicación compila para `arm64-v8a` e incluye STEP/OCCT.
- La base dimensional, identificación, safety gate, BA local y auditoría permanecen probados.
- Existen productores de degeneración visual y refinamiento acotado del pose graph.
- Existe una puerta profesional de campañas de dispositivo, corpus STEP y metrología.
- El estado alpha, piloto e industrial se separa explícitamente.
- Las campañas no ejecutadas no se convierten en aprobación.
- El manifiesto de evidencia tiene huella SHA-256 determinista.
- La integración runtime completa y las campañas físicas todavía están pendientes.
- El producto no está calificado para metrología industrial.

## Avance global estimado

- **Avance integral R0–R8: 70 %.**
- **Madurez funcional alpha: aproximadamente 91 %.**
- **Preparación industrial/metrológica: aproximadamente 15 %.**

## Iteración actual o última cerrada

### ITER-010 — Puerta profesional de calificación y campañas

Registro:

`history/ITER-010_2026-07-21_engineering-qualification-gate.md`

Resultado:

- campañas de dispositivo modeladas de forma fail-closed;
- separación alpha, piloto e industrial;
- metrología trazable obligatoria;
- manifiesto SHA-256 reproducible;
- 27 gates Java aprobados;
- GitHub Actions producto run `#530` en `success`;
- historial run `#104` en `success`;
- `0.18.0-alpha28` compilada y publicada.

## Bloqueos activos

1. `RECON-004`: productores visuales no conectados al analizador runtime.
2. `PG-002`: refinador no conectado al pose graph runtime.
3. `BA-002`: coordinador no invocado por `CaptureActivity`.
4. `BA-004`: observaciones BA no expuestas por el reporte.
5. `DEV-001`: campaña Samsung A15 no ejecutada.
6. `DEV-002`: campaña Honor X5C no ejecutada.
7. `MET-001`: no existe banco metrológico trazable.
8. `STEP-001`: corpus STEP real insuficiente.
9. `APP-001`: aprobación formal de ingeniería inexistente.
10. `ABI-001`: OCCT disponible solo para `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-011 — Integración runtime completa y campaña física inicial

Conectar los productores y refinadores al pipeline real, instrumentar duración, memoria, temperatura y JNI, ejecutar la primera campaña Samsung A15/Honor X5C e iniciar el corpus STEP real.

## Criterios de entrada

- conservar los 27 gates previos;
- usar hashes reales de APK;
- no reutilizar pruebas sintéticas como evidencia física;
- mantener calificación industrial bloqueada.

## Criterios de salida

- integración runtime completa o bloqueos documentados;
- campaña inicial en ambos dispositivos o fallos explícitos;
- métricas térmicas, memoria, tiempo y JNI;
- corpus STEP real iniciado;
- CI e historial exitosos.
