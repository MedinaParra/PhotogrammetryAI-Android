# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha27`

## Estado acumulado

- La aplicación compila para `arm64-v8a` e incluye STEP/OCCT.
- La base dimensional, identificación multivariable y auditoría permanecen activas.
- Existe safety gate, BA local, política runtime y productores de degeneración visual.
- Existe refinamiento robusto del pose graph limitado a traslaciones.
- El nodo 0 permanece fijo y los priors conservan la escala local.
- Problemas desconectados, sobredimensionados o divergentes se bloquean o revierten.
- La integración automática de los nuevos productores, coordinador y refinador en el pipeline real sigue pendiente.
- No existe calibración física ni validación metrológica.

## Avance global estimado

- **Avance integral R0–R8: 66 %.**
- **Madurez funcional alpha: aproximadamente 89 %.**
- **Preparación industrial/metrológica: aproximadamente 12 %.**

| Fase | Peso | Avance | Contribución |
|---|---:|---:|---:|
| R0 — Gobierno de evidencia | 8 % | 84 % | 6,72 % |
| R1 — Modelo dimensional | 12 % | 85 % | 10,20 % |
| R2 — Familias históricas | 10 % | 50 % | 5,00 % |
| R3 — Identificación | 12 % | 85 % | 10,20 % |
| R4 — Fotogrametría robusta | 20 % | 86 % | 17,20 % |
| R5 — Optimización y calibración | 15 % | 70 % | 10,50 % |
| R6 — Robustez Android/dispositivos | 10 % | 22 % | 2,20 % |
| R7 — STEP/ensamblaje | 8 % | 45 % | 3,60 % |
| R8 — Calificación de ingeniería | 5 % | 0 % | 0,00 % |
| **Total** | **100 %** |  | **65,62 % → 66 %** |

## Iteración actual o última cerrada

### ITER-009 — Refinamiento robusto y acotado del grafo de poses

Registro:

`history/ITER-009_2026-07-21_bounded-pose-graph-refinement.md`

Resultado:

- optimización limitada a 48 nodos y 240 aristas;
- nodo 0 fijo;
- pérdida Huber y priors de posición;
- reducción de residuos y rechazo de outlier;
- fallback ante divergencia;
- gate v52 aprobado;
- GitHub Actions producto run `#512` en `success`;
- `0.18.0-alpha27` compilada y publicada.

## Bloqueos activos

1. `DATA-001`: interfaz dimensional heredada pendiente.
2. `DATA-007`: SHA-256 reales de PDF pendientes.
3. `RECON-004`: productores visuales no conectados al analizador runtime.
4. `PG-001`: rotaciones no optimizadas.
5. `PG-002`: refinador no conectado al pose graph runtime.
6. `BA-002`: coordinador no invocado por `CaptureActivity`.
7. `BA-004`: observaciones BA no expuestas por el reporte.
8. `CAL-001`: no existen perfiles físicos Samsung A15/Honor X5C.
9. `VALID-001`: no existe campaña física ni metrológica.
10. `ABI-001`: OCCT disponible solo para `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-010 — Puerta profesional de calificación y campañas

Prioridad:

1. modelar campañas de dispositivo con evidencia por ejecución;
2. modelar corpus STEP de taller y tasa de importación;
3. modelar repetibilidad y metrología trazable;
4. separar `ALPHA_READY`, `FIELD_PILOT_REVIEW` e `INDUSTRIAL_BLOCKED`;
5. producir huella determinista del manifiesto de evidencia;
6. bloquear uso industrial sin campañas físicas;
7. publicar `0.18.0-alpha28`.

## Criterios de entrada

- conservar los 26 gates previos;
- no inventar resultados de Samsung A15 u Honor X5C;
- considerar evidencia no ejecutada como bloqueo;
- mantener PR en borrador;
- no declarar autorización metrológica.

## Criterios de salida

- puerta profesional implementada y probada;
- campañas faltantes bloquean calificación industrial;
- manifiesto auditable y reproducible;
- alpha28 compilada;
- CI producto e historial exitosos;
- ITER-010 archivada;
- este archivo actualizado con ITER-011.
