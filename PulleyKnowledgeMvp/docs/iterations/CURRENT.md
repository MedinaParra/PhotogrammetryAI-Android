# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha26`

## Estado acumulado

- La aplicación compila para `arm64-v8a` e incluye STEP/OCCT.
- La evidencia dimensional y la identificación por OT/plano/revisión permanecen auditables.
- Existe safety gate fotogramétrico fail-closed y BA local acotado.
- La política runtime distingue geometría optimizada, fallback revisable y bloqueo.
- Existe RANSAC de homografía y competencia contra soporte fundamental.
- Existen productores agregados de desenfoque, reflejos/saturación y ambigüedad repetitiva.
- Las métricas faltantes permanecen como `NaN` y brechas de evidencia.
- Los productores de ITER-008 todavía no se invocan automáticamente dentro de `SessionOverlapAnalyzer`.
- No existe calibración física ni validación metrológica.

## Avance global estimado

- **Avance integral R0–R8: 62 %.**
- **Madurez funcional alpha: aproximadamente 87 %.**
- **Preparación industrial/metrológica: aproximadamente 11 %.**

| Fase | Peso | Avance | Contribución |
|---|---:|---:|---:|
| R0 — Gobierno de evidencia | 8 % | 84 % | 6,72 % |
| R1 — Modelo dimensional | 12 % | 85 % | 10,20 % |
| R2 — Familias históricas | 10 % | 50 % | 5,00 % |
| R3 — Identificación | 12 % | 85 % | 10,20 % |
| R4 — Fotogrametría robusta | 20 % | 86 % | 17,20 % |
| R5 — Optimización y calibración | 15 % | 45 % | 6,75 % |
| R6 — Robustez Android/dispositivos | 10 % | 20 % | 2,00 % |
| R7 — STEP/ensamblaje | 8 % | 45 % | 3,60 % |
| R8 — Calificación de ingeniería | 5 % | 0 % | 0,00 % |
| **Total** | **100 %** |  | **61,67 % → 62 %** |

## Iteración actual o última cerrada

### ITER-008 — Productores automáticos de degeneración visual

Registro:

`history/ITER-008_2026-07-21_visual-degeneracy-producers.md`

Resultado:

- RANSAC determinista de homografía;
- competencia homografía/fundamental;
- agregación de desenfoque, reflejos y repetición;
- brechas explícitas ante muestras insuficientes;
- gate v51 aprobado;
- GitHub Actions producto run `#500` en `success`;
- `0.18.0-alpha26` compilada y publicada.

## Bloqueos activos

1. `DATA-001`: interfaz dimensional heredada pendiente.
2. `DATA-007`: SHA-256 reales de PDF pendientes.
3. `RECON-004`: productores visuales no conectados al analizador runtime.
4. `VISION-003`: highlights no extraídos todavía desde bitmap runtime.
5. `VISION-004`: umbrales sin banco real de superficies metálicas.
6. `BA-001`: rotaciones no optimizadas por BA local.
7. `BA-002`: coordinador no invocado por `CaptureActivity`.
8. `BA-004`: observaciones BA no expuestas por el reporte.
9. `CAL-001`: no existen perfiles físicos Samsung A15/Honor X5C.
10. `VALID-001`: no existe campaña física ni metrológica.
11. `ABI-001`: OCCT disponible solo para `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-009 — Refinamiento robusto del grafo global de poses

Prioridad:

1. crear refinamiento acotado del pose graph;
2. fijar el nodo 0 como gauge;
3. preservar escala mediante priors;
4. usar pérdida robusta y rechazar aristas incompatibles;
5. medir residuos antes/después;
6. bloquear divergencia y conservar poses iniciales;
7. publicar `0.18.0-alpha27`.

## Criterios de entrada

- conservar los 25 gates previos;
- limitar nodos, aristas e iteraciones;
- no denominar BA global a este refinamiento;
- mantener fallback a poses iniciales;
- no declarar validación física.

## Criterios de salida

- optimizador de pose graph probado;
- gauge fijo;
- reducción de residuos de ciclo;
- outliers rechazados;
- alpha27 compilada;
- CI producto e historial exitosos;
- ITER-009 archivada;
- este archivo actualizado con ITER-010.
