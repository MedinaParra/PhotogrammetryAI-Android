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

| Fase | Peso | Avance | Contribución |
|---|---:|---:|---:|
| R0 — Gobierno de evidencia | 8 % | 90 % | 7,20 % |
| R1 — Modelo dimensional | 12 % | 85 % | 10,20 % |
| R2 — Familias históricas | 10 % | 50 % | 5,00 % |
| R3 — Identificación | 12 % | 85 % | 10,20 % |
| R4 — Fotogrametría robusta | 20 % | 86 % | 17,20 % |
| R5 — Optimización y calibración | 15 % | 70 % | 10,50 % |
| R6 — Robustez Android/dispositivos | 10 % | 28 % | 2,80 % |
| R7 — STEP/ensamblaje | 8 % | 48 % | 3,84 % |
| R8 — Calificación de ingeniería | 5 % | 55 % | 2,75 % |
| **Total** | **100 %** |  | **69,69 % → 70 %** |

## Iteración actual o última cerrada

### ITER-010 — Puerta profesional de calificación y campañas

Registro:

`history/ITER-010_2026-07-21_engineering-qualification-gate.md`

Resultado:

- campañas de dispositivo modeladas de forma fail-closed;
- control de temperatura, memoria, JNI, STEP y reconstrucción;
- separación alpha, piloto e industrial;
- metrología trazable obligatoria para calificación industrial;
- manifiesto SHA-256 reproducible;
- gate v53 aprobado;
- GitHub Actions producto run `#528` en `success` al cierre funcional;
- `0.18.0-alpha28` compilada y publicada.

## Bloqueos activos

1. `DATA-001`: interfaz dimensional heredada pendiente.
2. `DATA-007`: SHA-256 reales de PDF pendientes.
3. `RECON-004`: productores visuales no conectados al analizador runtime.
4. `PG-002`: refinador no conectado al pose graph runtime.
5. `BA-002`: coordinador no invocado por `CaptureActivity`.
6. `BA-004`: observaciones BA no expuestas por el reporte.
7. `DEV-001`: campaña Samsung A15 no ejecutada.
8. `DEV-002`: campaña Honor X5C no ejecutada.
9. `MET-001`: no existe banco metrológico trazable.
10. `STEP-001`: corpus STEP real insuficiente.
11. `APP-001`: aprobación formal de ingeniería inexistente.
12. `ABI-001`: OCCT disponible solo para `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-011 — Integración runtime completa y campaña física inicial

Prioridad:

1. conectar safety gate y métricas suplementarias al analizador real;
2. exponer observaciones para BA y conectar el refinador del pose graph;
3. gobernar la interfaz mediante `READY`, `REVIEW` y `BLOCKED`;
4. instrumentar duración, memoria, temperatura y fallos JNI;
5. instalar alpha28 en Samsung A15 y Honor X5C;
6. ejecutar al menos tres recorridos por dispositivo;
7. iniciar un corpus real de al menos 20 STEP;
8. registrar resultados sin alterar la puerta industrial.

## Criterios de entrada

- conservar los 27 gates previos;
- usar hashes reales de APK por campaña;
- no reutilizar pruebas sintéticas como evidencia física;
- registrar cualquier fallo o interrupción;
- mantener calificación industrial bloqueada.

## Criterios de salida

- integración runtime completa o bloqueos técnicos documentados;
- campaña física inicial en ambos dispositivos o fallos explícitos;
- métricas térmicas, memoria, tiempo y JNI disponibles;
- corpus STEP real iniciado;
- CI e historial exitosos;
- ITER-011 archivada;
- siguiente iteración ajustada a evidencia física.
