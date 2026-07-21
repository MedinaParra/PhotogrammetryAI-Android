# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha24`

## Estado acumulado

- La aplicación compila para `arm64-v8a` e incluye STEP/OCCT.
- La evidencia dimensional está separada por código, OT, plano, revisión, dimensión y superficie.
- La puerta de radio de 30 mm, el motor multivariable y la auditoría local están probados.
- Existe una puerta fotogramétrica fail-closed que bloquea bajo paralaje, planaridad dominante, desconexión y reproyección excesiva.
- Existe un bundle adjustment local acotado a 8 cámaras, 120 puntos y 1500 observaciones.
- El BA fija la cámara 0, optimiza puntos y traslaciones, usa pérdida Huber y priors de escala/traslación.
- Una sesión cuyo safety gate no sea `READY` no puede ingresar al BA.
- Existen perfiles versionados Brown-Conrady y almacenamiento SQLite no destructivo.
- Los perfiles `UNCALIBRATED` o `LAB_ESTIMATED` no pueden activarse automáticamente.
- No existe todavía BA global, optimización de rotaciones ni calibración física por dispositivo.
- El safety gate y el BA aún no están integrados en el flujo runtime de `SessionOverlapAnalyzer`.
- Homografía, reflejos y ambigüedad repetitiva todavía requieren productores automáticos.
- No existe campaña física en Samsung A15 u Honor X5C.
- La aplicación no está autorizada para uso metrológico industrial.

## Avance global estimado

### Resultado al 2026-07-21

- **Avance integral del roadmap R0–R8: 55 %.**
- **Madurez funcional del prototipo alpha: aproximadamente 83 %.**
- **Preparación para uso industrial/metrológico: aproximadamente 9 %.**

El avance integral sube por la existencia de una optimización local probada y perfiles versionados. La preparación industrial continúa baja porque no hay calibración física, campaña de dispositivos ni incertidumbre trazable.

### Cálculo ponderado

| Fase | Peso | Avance | Contribución |
|---|---:|---:|---:|
| R0 — Gobierno de evidencia | 8 % | 82 % | 6,56 % |
| R1 — Modelo dimensional | 12 % | 85 % | 10,20 % |
| R2 — Familias históricas | 10 % | 50 % | 5,00 % |
| R3 — Identificación | 12 % | 85 % | 10,20 % |
| R4 — Fotogrametría robusta | 20 % | 68 % | 13,60 % |
| R5 — Optimización y calibración | 15 % | 35 % | 5,25 % |
| R6 — Robustez Android/dispositivos | 10 % | 10 % | 1,00 % |
| R7 — STEP/ensamblaje | 8 % | 45 % | 3,60 % |
| R8 — Calificación de ingeniería | 5 % | 0 % | 0,00 % |
| **Total** | **100 %** |  | **55,41 % → 55 %** |

### Interpretación

- El 55 % representa el recorrido técnico completo, no solo el número de funciones compiladas.
- El BA actual es local, acotado y sintéticamente probado; no es global.
- Los perfiles de cámara están preparados para almacenar una futura calibración, pero no constituyen calibración física.
- Un resultado `MATCH` o una reconstrucción optimizada continúan siendo resultados alpha, no liberaciones dimensionales.
- El porcentaje aumentará únicamente al cumplir puertas runtime, de dispositivos y metrológicas.

## Iteración actual o última cerrada

### ITER-006 — Bundle adjustment local acotado y perfiles de cámara

Registro:

`history/ITER-006_2026-07-21_local-bundle-adjustment-camera-profiles.md`

Resultado:

- BA local limitado y fail-closed implementado;
- cámara 0 fija y priors de traslación;
- pérdida Huber y aceptación solo de iteraciones que reducen costo;
- RMS inicial/final, P90 y profundidad positiva reportados;
- perfiles Brown-Conrady versionados;
- almacenamiento SQLite de perfiles;
- perfiles no validados rechazados para corrección automática;
- gate sintético de convergencia aprobado;
- GitHub Actions producto run `#460` en `success` al cierre;
- `0.18.0-alpha24` compilada para `arm64-v8a`.

## Bloqueos activos

1. `DATA-001`: interfaz dimensional heredada pendiente.
2. `DATA-007`: SHA-256 reales de PDF pendientes.
3. `RECON-003`: homografía no calculada automáticamente.
4. `VISION-001`: reflejos no medidos automáticamente.
5. `VISION-002`: ambigüedad repetitiva no agregada por sesión.
6. `BA-001`: rotaciones no optimizadas.
7. `BA-002`: BA local no conectado al pipeline runtime.
8. `BA-003`: no existe BA global ni eliminación Schur.
9. `CAL-001`: no existen perfiles físicos Samsung A15/Honor X5C.
10. `UI-001`: safety gate no gobierna todavía los botones de reconstrucción.
11. `VALID-001`: no existe campaña física ni metrológica.
12. `ABI-001`: OCCT disponible solo para `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-007 — Integración runtime del safety gate y BA local

Prioridad:

1. integrar el safety gate en el flujo real de reconstrucción;
2. bloquear ejecución automática para estados `REVIEW` y `BLOCKED`;
3. construir ventanas BA desde tracks, poses, intrínsecos y observaciones reales;
4. ejecutar BA local únicamente con problemas admitidos y dentro de límites;
5. persistir métricas antes/después, parámetros, duración y estado;
6. conservar fallback seguro hacia geometría sin optimizar;
7. usar perfiles `VALIDATED` cuando existan y Camera2 físico como fuente provisional;
8. probar divergencia, interrupción, memoria insuficiente y recuperación;
9. publicar `0.18.0-alpha25`.

## Criterios de entrada

- comenzar desde la cabeza final posterior a ITER-006;
- no ampliar ventanas sin medir memoria y tiempo;
- mantener cámara 0 fija y priors de escala;
- no activar perfiles no validados;
- no declarar BA global ni calibración física;
- conservar todos los gates anteriores.

## Criterios de salida

- safety gate integrado al flujo runtime;
- BA local alimentado con observaciones reales del pipeline;
- persistencia de resultados y parámetros;
- divergencia produce fallback seguro y estado `REVIEW` o `BLOCKED`;
- pruebas de interrupción y recursos aprobadas;
- versión alpha25 compilada;
- CI producto e historial exitosos;
- ITER-007 archivada;
- este archivo actualizado con ITER-008 y sus criterios.
