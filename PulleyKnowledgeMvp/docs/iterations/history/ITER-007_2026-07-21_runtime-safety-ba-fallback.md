# ITER-007 — Orquestación runtime de safety gate, BA y fallback

## Identificación

- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8` (borrador)
- **Versión:** `0.18.0-alpha25`
- **Commit funcional:** `bef1e9a1f4ae3d9105f0a0f3d6a1a342b8b7cfab`
- **Fases:** R4, R5 y R6

## Objetivo ejecutado

Crear una capa runtime fail-closed que una la puerta fotogramétrica, el BA local acotado, la aceptación de la geometría optimizada, el fallback sin optimizar y una auditoría persistente.

## Implementación

- `RuntimeReconstructionDecisionCore` separa `READY`, `REVIEW` y `BLOCKED`.
- Solo un BA resuelto, aceptado y con RMS no degradado puede publicar geometría optimizada.
- Gate `REVIEW`, interrupción, presupuesto insuficiente o problema BA ausente conservan la geometría sin optimizar como fallback explícito.
- Gate `BLOCKED` no permite promover ni geometría optimizada ni fallback.
- `RuntimeReconstructionDecisionStore` conserva decisiones append-only en SQLite.
- `RuntimeReconstructionCoordinator` conecta un reporte real, métricas suplementarias, un problema BA real cuando está disponible y la auditoría.
- Se agregó el gate `RuntimeReconstructionV50Test` al workflow del producto.

## Validación

GitHub Actions run `#479` terminó en `success`:

- 24 gates Java;
- gate nuevo de aceptación/fallback aprobado;
- compilación Android alpha25;
- verificación SHA-256 del AAR STEP;
- cierre OCCT y 25 bibliotecas nativas aprobados;
- APK alpha25 publicada.

El validador independiente del historial run `#65` también terminó en `success`.

## Límites honestos

- El coordinador está compilado como servicio runtime, pero `CaptureActivity` todavía llama directamente a `SessionOverlapAnalyzer`.
- `SessionOverlapAnalyzer.Report` aún no expone por sí solo todas las observaciones necesarias para construir el problema BA.
- El BA no optimiza rotaciones ni intrínsecos.
- No hubo ejecución física en Samsung A15 u Honor X5C.
- No existe validación metrológica.

## Avance

- Avance integral anterior: **55 %**.
- Avance integral después de ITER-007: **59 %**.
- Madurez funcional alpha estimada: **85 %**.
- Preparación industrial/metrológica estimada: **10 %**.

El aumento se limita porque la capa de decisión está lista y auditada, pero la ruta visual y el productor de observaciones BA aún deben integrarse.

## Siguiente iteración

### ITER-008 — Productores automáticos de degeneración visual

- competencia homografía/fundamental por par;
- agregación de ambigüedad repetitiva;
- estimación conservadora de reflejos y desenfoque;
- conexión de métricas suplementarias al safety gate;
- alpha26 y CI completo.
