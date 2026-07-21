# ITER-007 — Orquestación runtime de safety gate, BA y fallback

## Identificación

- **Fecha de inicio:** 2026-07-21
- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8` (borrador)
- **Versión:** `0.18.0-alpha25`
- **Commit funcional:** `bef1e9a1f4ae3d9105f0a0f3d6a1a342b8b7cfab`
- **Fases:** R4, R5 y R6

## Objetivo

Crear una capa runtime fail-closed que una la puerta fotogramétrica, el BA local acotado, la aceptación de geometría optimizada, el fallback sin optimizar y una auditoría persistente.

## Alcance ejecutado

### Incluido

- decisión `READY`, `REVIEW` y `BLOCKED` después del safety gate;
- aceptación de BA solo cuando reduce o mantiene el RMS y conserva profundidad positiva;
- fallback explícito ante interrupción, recursos insuficientes, problema BA ausente o BA no aceptado;
- auditoría append-only en SQLite;
- coordinador Android que recibe reporte, métricas suplementarias y problema BA;
- gate determinista en CI;
- versión alpha25 y APK arm64-v8a.

### Excluido

- modificación del botón de `CaptureActivity` para llamar al coordinador;
- construcción automática del problema BA desde internals privados de `SessionOverlapAnalyzer`;
- optimización de rotaciones e intrínsecos;
- campaña física y validación metrológica.

## Cambios y decisiones

- `RuntimeReconstructionDecisionCore` impide etiquetar un fallback como geometría optimizada.
- Gate `BLOCKED` no permite promover ni geometría optimizada ni fallback.
- Gate `REVIEW` conserva únicamente una salida revisable, nunca una aprobación automática.
- `RuntimeReconstructionDecisionStore` registra cada decisión como fila nueva y no destructiva.
- `RuntimeReconstructionCoordinator` ejecuta BA únicamente si el gate está `READY`, existe problema y el presupuesto está disponible.
- La ausencia de observaciones BA reales se registra como `BA_PROBLEM_UNAVAILABLE`.

## Evidencia y validación

| Prueba | Resultado | Evidencia |
|---|---|---|
| BA aceptado publica geometría optimizada | PASS | `RuntimeReconstructionV50Test` |
| Problema BA ausente usa fallback y `REVIEW` | PASS | gate v50 |
| Gate bloqueado impide cualquier promoción | PASS | gate v50 |
| Interrupción y recursos insuficientes | PASS | fallback fail-closed |
| Compilación Android alpha25 | PASS | GitHub Actions run `#479` |
| AAR STEP y cierre OCCT | APROBADO | run `#479`, 25 bibliotecas nativas |
| Historial previo al registro | PASS | run `#65` |
| Prueba física Samsung/Honor | NO EJECUTADA | fuera de alcance |

## Resultados

- Se creó una política runtime reproducible para aceptar o rechazar BA.
- La geometría anterior permanece disponible como fallback solo en estado `REVIEW`.
- Las decisiones quedan auditadas en SQLite.
- Alpha25 compila y publica APK.
- La ruta visual todavía no invoca automáticamente el coordinador.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Estado |
|---|---|---|---|
| BA-002 | alta | `CaptureActivity` aún llama directamente al analizador | abierto |
| BA-004 | alta | reporte no expone todas las observaciones para formar BA | abierto |
| UI-001 | alta | safety gate no gobierna todavía el botón visual | abierto |
| CAL-001 | crítica | no existen calibraciones físicas | abierto |
| VALID-001 | crítica | no hay campaña física/metrológica | abierto |

## Estado del roadmap

- Avance integral anterior: **55 %**.
- Avance integral después de ITER-007: **59 %**.
- Madurez funcional alpha estimada: **85 %**.
- Preparación industrial/metrológica estimada: **10 %**.

El incremento es limitado porque la política runtime y auditoría están listas, pero la conexión visual y el productor BA real siguen pendientes.

## Siguiente iteración obligatoria

### ITER-008 — Productores automáticos de degeneración visual

Implementar competencia homografía/fundamental, ambigüedad repetitiva, reflejos y desenfoque agregados; producir métricas suplementarias para el safety gate y publicar alpha26.

## Criterios de entrada y salida

### Entrada

- conservar los 24 gates existentes;
- mantener estados fail-closed;
- no usar heurísticas faltantes como valores favorables;
- no declarar integración física.

### Salida

- productor homografía/fundamental probado;
- agregador de degradación visual probado;
- métricas suplementarias reproducibles;
- alpha26 compilada;
- CI e historial aprobados;
- ITER-008 definida como siguiente registro activo.
