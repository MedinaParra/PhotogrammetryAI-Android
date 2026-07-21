# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha25`

## Estado acumulado

- La aplicación compila para `arm64-v8a` e incluye STEP/OCCT.
- La evidencia dimensional está versionada por código, OT, plano, revisión, dimensión y superficie.
- La identificación multivariable y la puerta de radio de 30 mm están probadas.
- El safety gate fotogramétrico usa estados `READY`, `REVIEW` y `BLOCKED`.
- Existe BA local acotado con cámara 0 fija, pérdida Huber y priors de traslación.
- Existe una política runtime que acepta geometría optimizada solo cuando el gate y el BA son válidos.
- Interrupción, recursos insuficientes o ausencia de problema BA producen fallback explícito y `REVIEW`.
- Gate `BLOCKED` no promueve geometría.
- Las decisiones runtime se auditan en SQLite append-only.
- `CaptureActivity` todavía no llama al coordinador nuevo y el reporte no expone todas las observaciones BA.
- No existe calibración física ni validación metrológica.

## Avance global estimado

- **Avance integral R0–R8: 59 %.**
- **Madurez funcional alpha: aproximadamente 85 %.**
- **Preparación industrial/metrológica: aproximadamente 10 %.**

| Fase | Peso | Avance | Contribución |
|---|---:|---:|---:|
| R0 — Gobierno de evidencia | 8 % | 84 % | 6,72 % |
| R1 — Modelo dimensional | 12 % | 85 % | 10,20 % |
| R2 — Familias históricas | 10 % | 50 % | 5,00 % |
| R3 — Identificación | 12 % | 85 % | 10,20 % |
| R4 — Fotogrametría robusta | 20 % | 72 % | 14,40 % |
| R5 — Optimización y calibración | 15 % | 45 % | 6,75 % |
| R6 — Robustez Android/dispositivos | 10 % | 18 % | 1,80 % |
| R7 — STEP/ensamblaje | 8 % | 45 % | 3,60 % |
| R8 — Calificación de ingeniería | 5 % | 0 % | 0,00 % |
| **Total** | **100 %** |  | **58,67 % → 59 %** |

## Iteración actual o última cerrada

### ITER-007 — Orquestación runtime de safety gate, BA y fallback

Registro:

`history/ITER-007_2026-07-21_runtime-safety-ba-fallback.md`

Resultado:

- política runtime fail-closed implementada;
- BA aceptado solo con gate `READY` y reducción válida del error;
- fallback sin optimizar claramente diferenciado;
- auditoría SQLite append-only;
- gate v50 aprobado;
- GitHub Actions producto run `#479` en `success`;
- `0.18.0-alpha25` compilada y publicada.

## Bloqueos activos

1. `DATA-001`: interfaz dimensional heredada pendiente.
2. `DATA-007`: SHA-256 reales de PDF pendientes.
3. `RECON-003`: homografía no calculada automáticamente.
4. `VISION-001`: reflejos no medidos automáticamente.
5. `VISION-002`: ambigüedad repetitiva no agregada por sesión.
6. `BA-001`: rotaciones no optimizadas.
7. `BA-002`: coordinador no invocado por `CaptureActivity`.
8. `BA-004`: observaciones BA no expuestas por el reporte.
9. `CAL-001`: no existen perfiles físicos Samsung A15/Honor X5C.
10. `UI-001`: safety gate no gobierna todavía los botones visuales.
11. `VALID-001`: no existe campaña física ni metrológica.
12. `ABI-001`: OCCT disponible solo para `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-008 — Productores automáticos de degeneración visual

Prioridad:

1. implementar competencia homografía/fundamental;
2. detectar dominancia planar sin confundir falta de datos con aprobación;
3. agregar ambigüedad repetitiva por par y por sesión;
4. agregar métricas conservadoras de reflejos y desenfoque;
5. producir `SupplementalMetrics` para el safety gate;
6. probar escenas volumétricas, planas, repetitivas y reflectantes;
7. publicar `0.18.0-alpha26`.

## Criterios de entrada

- conservar los 24 gates previos;
- mantener política fail-closed;
- no rellenar métricas desconocidas con valores favorables;
- respetar límites de memoria Android;
- no declarar validación física.

## Criterios de salida

- productor homografía/fundamental probado;
- agregador de degradación visual probado;
- métricas suplementarias trazables;
- alpha26 compilada;
- CI producto e historial exitosos;
- ITER-008 archivada;
- este archivo actualizado con ITER-009.
