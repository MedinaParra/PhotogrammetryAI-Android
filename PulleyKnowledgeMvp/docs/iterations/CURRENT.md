# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha23`

## Estado acumulado

- La aplicación compila para `arm64-v8a` e incluye STEP/OCCT.
- La evidencia dimensional está separada por código, OT, plano, revisión, dimensión y superficie.
- La puerta de radio de 30 mm y el motor multivariable están probados.
- Las decisiones se auditan localmente con residuos, fuentes y huella reproducible.
- Existe una puerta fotogramétrica fail-closed con estados `READY`, `REVIEW` y `BLOCKED`.
- Bajo paralaje, planaridad dominante, grafo desconectado y reproyección excesiva quedan bloqueados.
- Métricas importantes ausentes producen `REVIEW`, no aprobación silenciosa.
- El adaptador consume métricas del reporte de reconstrucción existente.
- Homografía, reflejos y ambigüedad repetitiva aún requieren integración automática al pipeline real.
- La interfaz aún no obliga a respetar la nueva puerta.
- No existe bundle adjustment local/global ni perfil calibrado de distorsión por dispositivo.
- No existe campaña física en Samsung A15 u Honor X5C.
- La aplicación no está autorizada para uso metrológico industrial.

## Avance global estimado

### Resultado al 2026-07-21

- **Avance integral del roadmap R0–R8: 50 %.**
- **Madurez funcional del prototipo alpha: aproximadamente 78 %.**
- **Preparación para uso industrial/metrológico: aproximadamente 8 %.**

### Cálculo ponderado

| Fase | Peso | Avance | Contribución |
|---|---:|---:|---:|
| R0 — Gobierno | 8 % | 82 % | 6,56 % |
| R1 — Modelo dimensional | 12 % | 85 % | 10,20 % |
| R2 — Familias históricas | 10 % | 50 % | 5,00 % |
| R3 — Identificación | 12 % | 85 % | 10,20 % |
| R4 — Fotogrametría robusta | 20 % | 65 % | 13,00 % |
| R5 — Optimización/calibración | 15 % | 5 % | 0,75 % |
| R6 — Dispositivos | 10 % | 10 % | 1,00 % |
| R7 — STEP/ensamblaje | 8 % | 45 % | 3,60 % |
| R8 — Calificación | 5 % | 0 % | 0,00 % |
| **Total** | **100 %** |  | **50,31 % → 50 %** |

## Iteración actual o última cerrada

### ITER-005 — Puerta de seguridad fotogramétrica y degeneraciones

Registro:

`history/ITER-005_2026-07-21_photogrammetry-safety-gate.md`

Resultado:

- puerta fail-closed implementada;
- bajo paralaje, planaridad, desconexión y reproyección excesiva bloqueados;
- degradaciones recuperables enviadas a revisión;
- métricas faltantes visibles;
- adaptador al reporte actual;
- GitHub Actions run `#444` en `success`;
- 22 gates ejecutados;
- `0.18.0-alpha23` compilada.

## Bloqueos activos

1. `DATA-001`: interfaz dimensional heredada pendiente.
2. `DATA-007`: SHA-256 reales de PDF pendientes.
3. `RECON-003`: homografía no calculada automáticamente.
4. `VISION-001`: reflejos no medidos automáticamente.
5. `VISION-002`: ambigüedad repetitiva no agregada por sesión.
6. `UI-001`: la interfaz no obliga a pasar el safety gate.
7. `RECON-002`: no existe bundle adjustment.
8. `CAL-001`: no existe perfil calibrado de distorsión.
9. `VALID-001`: no existe campaña física.
10. `ABI-001`: OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-006 — Bundle adjustment local acotado y perfiles de cámara

Prioridad:

1. optimizar puntos 3D y traslaciones de cámara en una ventana local;
2. fijar cámara 0 para eliminar el gauge;
3. usar priors para preservar escala y órbita;
4. usar pérdida Huber contra outliers;
5. limitar recursos para Android;
6. exigir safety gate `READY` antes de optimizar;
7. medir RMS inicial/final y profundidad positiva;
8. crear perfiles versionados de intrínsecos/distorsión;
9. probar mejora, outliers, problema insuficiente y distorsión;
10. publicar `0.18.0-alpha24`.

## Criterios de entrada

- partir de la cabeza posterior a ITER-005;
- no declarar optimización global;
- no afirmar calibración física sin patrón;
- conservar límites de memoria y tiempo;
- bloquear entradas insuficientes o safety gate no aprobado.

## Criterios de salida

- BA local acotado con mejora cuantificada;
- perfiles de cámara versionados;
- prueba de distorsión y escalado de resolución;
- safety gate respetado;
- alpha24 compilada;
- CI producto/historial exitosos;
- ITER-006 archivada;
- roadmap actualizado con ITER-007.
