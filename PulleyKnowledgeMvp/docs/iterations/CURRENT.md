# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha22`

## Estado acumulado

- La aplicación Android compila para `arm64-v8a` e incluye el puente STEP/OCCT.
- Existe una base revisionada separada por código, OT, plano, revisión, dimensión y superficie.
- La puerta `error_radio_mm <= 30` está implementada y probada.
- El motor multivariable combina radio y cotas axiales sin cruzar OT ni revisiones.
- Evidencia faltante produce `REVIEW`; contradicción crítica produce `BLOCKED`.
- Cada decisión incluye puntuación, residuos, tolerancias, fuentes y huella reproducible.
- El servicio local lee SQLite y agrega una auditoría append-only.
- `4162045`, `4196111` y `10510386` permanecen bloqueados.
- La interfaz `MainActivity` aún utiliza el ranking heredado y no se declara migrada.
- Los hashes reales de bytes PDF siguen pendientes.
- El pipeline fotogramétrico dispone de múltiples pruebas sintéticas, pero aún carece de una puerta unificada de degeneraciones.
- No existe bundle adjustment local/global ni calibración de distorsión por dispositivo.
- No existe validación física en Samsung A15 u Honor X5C.
- La aplicación no está autorizada para identificación automática industrial ni liberación dimensional.

## Avance global estimado

### Resultado al 2026-07-21

- **Avance integral del roadmap R0–R8: 46 %.**
- **Madurez funcional del prototipo alpha: aproximadamente 72 %.**
- **Preparación para uso industrial/metrológico: aproximadamente 8 %.**

El motor de datos e identificación avanza de forma importante, pero la preparación industrial permanece baja porque todavía faltan pruebas físicas, calibración e incertidumbre trazable.

### Cálculo ponderado

| Fase | Peso del roadmap | Avance de la fase | Contribución |
|---|---:|---:|---:|
| R0 — Gobierno de evidencia | 8 % | 82 % | 6,56 % |
| R1 — Migración del modelo dimensional | 12 % | 85 % | 10,20 % |
| R2 — Saneamiento de familias históricas | 10 % | 50 % | 5,00 % |
| R3 — Motor de identificación geométrica | 12 % | 85 % | 10,20 % |
| R4 — Fotogrametría robusta para taller | 20 % | 45 % | 9,00 % |
| R5 — Optimización y calibración | 15 % | 5 % | 0,75 % |
| R6 — Robustez Android y dispositivos | 10 % | 10 % | 1,00 % |
| R7 — STEP, componentes y ensamblaje | 8 % | 45 % | 3,60 % |
| R8 — Calificación de ingeniería | 5 % | 0 % | 0,00 % |
| **Total** | **100 %** |  | **46,31 % → 46 %** |

### Interpretación

- El porcentaje refleja implementación probada, no cantidad de archivos.
- El motor multivariable todavía necesita tolerancias específicas por plano y validación física.
- La auditoría está integrada como servicio local, pero aún no se exporta con la sesión.
- Un `MATCH` continúa siendo una hipótesis lógica alpha, no una certificación metrológica.

## Iteración actual o última cerrada

### ITER-004 — Identificación multivariable y auditoría de decisiones

Registro:

`history/ITER-004_2026-07-21_multivariable-identification-audit.md`

Resultado:

- radio y cotas axiales evaluados conjuntamente;
- residuos `PASS`, `WARNING`, `CONTRADICTION` y `MISSING_REFERENCE`;
- cruces dimensionales incompatibles bloqueados;
- puntuación y huella reproducible;
- repositorio SQLite y auditoría append-only;
- servicio local completo;
- GitHub Actions producto run `#430` en `success`;
- 21 gates ejecutados;
- versión `0.18.0-alpha22` compilada.

## Bloqueos activos

1. `DATA-001`: la interfaz visual aún utiliza el modelo heredado.
2. `DATA-002`: `4162045` no puede producir `MATCH`.
3. `DATA-006`: `4196111` mantiene posible conflicto con `4162038`.
4. `DATA-007`: faltan SHA-256 reales de PDF.
5. `TOL-001`: varias tolerancias siguen siendo genéricas.
6. `AUDIT-001`: auditoría no exportada aún en paquete de sesión.
7. `RECON-001`: falta puerta unificada para degeneraciones fotogramétricas.
8. `RECON-002`: no existe bundle adjustment local/global.
9. `CAL-001`: no existen perfiles calibrados de distorsión.
10. `VALID-001`: no existe campaña física en dispositivos.
11. `ABI-001`: OCCT disponible solo para `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-005 — Puerta de seguridad fotogramétrica y degeneraciones

Prioridad de trabajo:

1. definir un reporte común de cobertura y salud geométrica;
2. evaluar cantidad de cuadros y cobertura orbital;
3. evaluar pares útiles, coherencia y conectividad;
4. bloquear bajo paralaje;
5. bloquear dominancia planar/homográfica incompatible;
6. bloquear grafos desconectados o ciclos gravemente inconsistentes;
7. revisar reflejos, desenfoque y reproyección elevada;
8. emitir `READY`, `REVIEW` o `BLOCKED` con razones;
9. agregar pruebas sintéticas positivas y negativas;
10. publicar `0.18.0-alpha23`.

## Criterios de entrada

- comenzar desde la cabeza posterior a ITER-004;
- mantener la IMU y órbita como priors;
- no interpretar una gran cantidad de matches como geometría válida por sí sola;
- no ocultar degradaciones detrás de una puntuación promedio;
- conservar los gates anteriores.

## Criterios de salida

- puerta fotogramétrica determinista;
- bajo paralaje y planaridad degenerada producen `BLOCKED`;
- reflejos y reproyección marginal producen `REVIEW` cuando sean recuperables;
- estado y razones consumibles por la aplicación;
- versión alpha23 compilada;
- CI producto e historial exitosos;
- ITER-005 archivada;
- este archivo actualizado con ITER-006 y sus criterios.
