# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha21`

## Estado acumulado

- La aplicación Android compila para `arm64-v8a` e incluye el puente STEP/OCCT.
- El pipeline fotogramétrico mantiene pruebas sintéticas para correspondencias, fundamental, pose, triangulación, tracks, grafo, cilindro y restricciones CAD.
- Existe una base revisionada separada por código, OT, plano, revisión, dimensión y superficie.
- La evidencia auditada de cinco familias ya está cargada sin sobrescribir OT distintas.
- La puerta `error_radio_mm <= 30` está implementada y probada en 29, 30 y 31 mm.
- Las unidades originales en pulgadas se conservan y se normalizan a milímetros.
- `4162045`, `4196111` y `10510386` permanecen bloqueados.
- La interfaz de conocimiento heredada todavía no utiliza completamente el motor revisionado.
- Los hashes reales de bytes de los PDF siguen pendientes; los valores actuales son identificadores provisionales de migración.
- No existe todavía bundle adjustment local/global ni calificación metrológica.
- La ejecución real Camera2/STEP debe validarse en Samsung A15 y Honor X5C.
- La aplicación no está autorizada para identificación automática industrial ni liberación dimensional.

## Avance global estimado

### Resultado al 2026-07-21

- **Avance integral del roadmap R0–R8: 41 %.**
- **Madurez funcional del prototipo alpha: aproximadamente 65 %.**
- **Preparación para uso industrial/metrológico: aproximadamente 7 %.**

El avance sube porque R1 y R3 ya tienen implementación ejecutable y pruebas automáticas. La preparación industrial cambia poco porque continúan pendientes la campaña física, la calibración y la incertidumbre trazable.

### Cálculo ponderado

| Fase | Peso del roadmap | Avance de la fase | Contribución |
|---|---:|---:|---:|
| R0 — Gobierno de evidencia | 8 % | 78 % | 6,24 % |
| R1 — Migración del modelo dimensional | 12 % | 75 % | 9,00 % |
| R2 — Saneamiento de familias históricas | 10 % | 50 % | 5,00 % |
| R3 — Motor de identificación geométrica | 12 % | 55 % | 6,60 % |
| R4 — Fotogrametría robusta para taller | 20 % | 45 % | 9,00 % |
| R5 — Optimización y calibración | 15 % | 5 % | 0,75 % |
| R6 — Robustez Android y dispositivos | 10 % | 10 % | 1,00 % |
| R7 — STEP, componentes y ensamblaje | 8 % | 45 % | 3,60 % |
| R8 — Calificación de ingeniería | 5 % | 0 % | 0,00 % |
| **Total** | **100 %** |  | **41,19 % → 41 %** |

### Interpretación

- El modelo revisionado y la puerta de radio ya son código probado, no solo diseño.
- El porcentaje no supone que la interfaz heredada esté completamente migrada.
- Los resultados `MATCH` siguen siendo decisiones lógicas alpha, no certificaciones metrológicas.
- El porcentaje solo aumentará al completar puertas de salida con evidencia.

## Iteración actual o última cerrada

### ITER-003 — Modelo OT/plano/revisión y puerta de radio

Registro:

`history/ITER-003_2026-07-21_revisioned-evidence-radius-gate.md`

Resultado:

- esquema normalizado e idempotente creado;
- radios `BARE_SHELL` y `OUTER_LAGGING` separados;
- evidencia de cinco familias cargada por OT/plano/revisión;
- frontera 29/30/31 mm aprobada;
- pulgadas/milímetros aprobados;
- tres familias conflictivas bloqueadas;
- GitHub Actions producto run `#409` en `success`;
- versión `0.18.0-alpha21` compilada.

## Bloqueos activos

1. `DATA-001`: la interfaz heredada aún consulta el modelo plano.
2. `DATA-002`: `4162045` no puede producir `MATCH`.
3. `DATA-006`: `4196111` mantiene conflicto posible con `4162038`.
4. `DATA-007`: faltan SHA-256 reales de los bytes PDF.
5. `DATA-009`: falta puntuación axial multivariable.
6. `VALID-001`: no existe validación física ni repetibilidad en dispositivos.
7. `ANDROID-001`: Camera2 no siempre solicita la máxima resolución nativa.
8. `RECON-001`: no existe bundle adjustment local/global.
9. `CAL-001`: no existen perfiles de distorsión calibrados por dispositivo.
10. `ABI-001`: OCCT disponible solo para `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-004 — Identificación multivariable y auditoría de decisiones

Prioridad de trabajo:

1. combinar radio con largo de manto, centros y eje cuando exista evidencia;
2. clasificar cada residual dimensional en aprobado, advertencia o contradicción;
3. bloquear cruces entre OT o revisiones incompatibles;
4. distinguir evidencia faltante de evidencia contradictoria;
5. generar una puntuación reproducible con razones y fuentes;
6. persistir la auditoría de cada decisión;
7. agregar pruebas para identidad parcial, OT cruzada y revisiones ambiguas;
8. mantener las tres familias conflictivas en `BLOCKED`;
9. publicar `0.18.0-alpha22`.

## Criterios de entrada

- comenzar desde la cabeza posterior al cierre de ITER-003;
- leer el registro ITER-003;
- no debilitar la puerta de radio de 30 mm;
- no convertir ausencia de evidencia en coincidencia;
- conservar la base heredada para rollback lógico;
- no declarar hashes PDF verificados mientras no se calculen sobre los archivos originales.

## Criterios de salida

- motor multivariable con pruebas deterministas;
- auditoría de decisión persistente;
- OT cruzadas producen `REVIEW` o `BLOCKED`;
- decisiones incluyen residual, tolerancia y fuente por dimensión;
- versión alpha22 compilada;
- CI del producto e historial exitosos;
- ITER-004 archivada en `history/`;
- este archivo actualizado con ITER-005 y sus puertas de salida.
