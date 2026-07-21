# ITER-004 — Identificación multivariable y auditoría de decisiones

## Identificación

- **Fecha de inicio:** 2026-07-21
- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8`
- **Commit base:** `7db0d60036c7afb4672dad47e203e7cbafa0f7a8`
- **Commit funcional de cierre:** `28a1274e9fd140ade4488548444ff4c1da8be289`
- **Versión de aplicación:** `0.18.0-alpha22`
- **Fases del roadmap:** `R0`, `R1`, `R3`
- **Responsable técnico:** MedinaParra / asistencia de ingeniería

## Objetivo

Extender la puerta de radio con una decisión multivariable por OT, plano y revisión, distinguir datos faltantes de contradicciones dimensionales y conservar una auditoría local inmutable de cada evaluación.

## Alcance ejecutado

### Incluido

- motor puro `PulleyIdentificationDecisionCore`;
- entrada explícita por radio, superficie y cotas axiales;
- comparación de largo, cara, centros, eje y diámetros con tolerancias por tipo;
- residuos `PASS`, `WARNING`, `CONTRADICTION` y `MISSING_REFERENCE`;
- puntuación reproducible;
- huella determinista de la decisión;
- lectura de evidencia desde SQLite mediante `RevisionedKnowledgeRepository`;
- tabla append-only `identification_decision_audit`;
- servicio local SQLite → decisión → auditoría;
- prueba de coincidencia completa, evidencia parcial, cruce de cotas OT y familia conflictiva;
- integración del nuevo gate en GitHub Actions.

### Excluido

- reemplazo visual de `MainActivity`;
- validación automática de tolerancias de fabricación específicas de cada plano;
- resolución documental de códigos bloqueados;
- SHA-256 real de PDF;
- campañas fotográficas reales;
- optimización geométrica.

## Cambios y decisiones

### Código y arquitectura

Se agregaron:

- `PulleyIdentificationDecisionCore.java`;
- `RevisionedKnowledgeRepository.java`;
- `RevisionedDecisionAuditStore.java`;
- `RevisionedIdentificationService.java`;
- `PulleyIdentificationDecisionV47Test.java`;
- `run_identification_decision_v47_test.sh`.

La decisión se ejecuta en el siguiente orden:

1. resolver la evidencia por código, OT, plano, revisión y superficie;
2. ejecutar la puerta dura de radio;
3. buscar cada cota axial únicamente dentro del mismo alcance documental;
4. calcular error absoluto y tolerancia permitida;
5. clasificar el residual;
6. bloquear cualquier contradicción superior a dos tolerancias;
7. enviar a revisión la evidencia faltante o marginal;
8. permitir `MATCH` solo con radio y al menos una cota axial confirmada;
9. almacenar la decisión sin modificar la evidencia fuente.

Las tolerancias alpha se definen por clase de dimensión y no sustituyen las tolerancias indicadas en el plano. Cuando el plano disponga de tolerancia explícita, esta prevalece si es mayor que el mínimo de seguridad configurado.

### Auditoría

Cada registro conserva:

- identificador UUID;
- huella determinista de entradas y resultado;
- fecha;
- código, OT, plano y revisión;
- tipo de radio y superficie;
- radio observado, referencia y error;
- estado y puntuación;
- razones;
- residuos, tolerancias y fuentes por dimensión.

La auditoría es append-only: repetir una evaluación genera un nuevo evento y no reescribe decisiones anteriores.

## Evidencia y validación

| Prueba o revisión | Entorno | Resultado | Evidencia |
|---|---|---|---|
| Radio + cuatro cotas OT-1702 exactas | JDK | PASS / MATCH | score superior a 0,95 |
| Radio sin cotas axiales | JDK | PASS / REVIEW | identidad parcial no aprobada |
| Cotas incompatibles de otra intervención | JDK | PASS / BLOCKED | contradicción axial explícita |
| OT-781 en pulgadas normalizada | JDK | PASS / MATCH | radio y cara compatibles |
| Código `4162045` | JDK | PASS / BLOCKED | conflicto heredado respetado |
| Huella repetible | JDK | PASS | mismas entradas producen misma huella |
| Lectura SQLite revisionada | compilación Android | PASS | repositorio tipado |
| Auditoría append-only | compilación Android | PASS | tabla y servicio integrados |
| GitHub Actions producto | run `#430` | PASS | 21 gates, APK y cierre OCCT |
| Historial | GitHub Actions | PASS | gate independiente |
| Prueba de interfaz real | Android | NO EJECUTADA | interfaz heredada aún pendiente |
| Validación física | teléfono/instrumentos | NO EJECUTADA | fuera de alcance |

## Resultados

- Una coincidencia por código o nombre ya no es suficiente para producir `MATCH` en el nuevo motor.
- La evidencia parcial se diferencia de una contradicción real.
- Las cotas de una OT no se recuperan silenciosamente desde otra OT.
- Cada dimensión muestra observado, referencia, error, límite, estado y fuente.
- El servicio puede ejecutar y auditar decisiones completamente en local.
- La versión alpha22 compila con el motor STEP intacto.
- La interfaz principal todavía debe migrarse al servicio revisionado.
- No se declara precisión industrial.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Mitigación | Estado |
|---|---|---|---|---|
| DATA-001 | alta | `MainActivity` aún utiliza el ranking heredado | migrar UI después de estabilizar gates | abierto |
| DATA-007 | alta | SHA-256 PDF real pendiente | hash durante importación de archivo | abierto |
| TOL-001 | media | tolerancias alpha son genéricas cuando el plano no especifica una | cargar tolerancias por plano | abierto |
| AUDIT-001 | media | auditoría aún no se exporta en paquete de sesión | integrar exportación futura | abierto |
| VALID-001 | crítica | ninguna decisión ha sido comparada físicamente | campaña R6/R8 | abierto |
| RECON-001 | alta | pipeline puede aceptar degeneraciones geométricas antes de modelar | ITER-005 | abierto |

## Estado del roadmap

| Fase | Antes | Después | Puerta de salida |
|---|---|---|---|
| R0 — Gobierno | 78 % | 82 % | abierta: hashes/exportación pendientes |
| R1 — Modelo dimensional | 75 % | 85 % | abierta: UI heredada pendiente |
| R3 — Identificación | 55 % | 85 % | abierta: tolerancias por plano y validación física |
| R4–R8 | sin cambio | sin cambio | abiertas |

Avance ponderado estimado después de ITER-004: **46 %**.

## Siguiente iteración obligatoria

### ITER-005 — Puerta de seguridad fotogramétrica y degeneraciones

Objetivos concretos:

1. reunir métricas de cobertura, pares, paralaje, planaridad, reflejos, grafo y reproyección;
2. bloquear escenas de bajo paralaje;
3. bloquear dominancia homográfica/planar no apta para reconstrucción volumétrica;
4. bloquear grafos desconectados y ciclos inconsistentes;
5. enviar a revisión exceso de reflejos, desenfoque o error de reproyección;
6. generar estados `READY`, `REVIEW` y `BLOCKED` con razones;
7. agregar bancos sintéticos positivos y negativos;
8. publicar `0.18.0-alpha23`.

## Criterios de entrada y salida

### Entrada

- partir de la cabeza de ITER-004;
- no modificar la regla dimensional de radio;
- reutilizar las métricas existentes sin declararlas validación física;
- tratar la IMU/orbita como prior, no como pose verdadera.

### Salida

- puerta de seguridad fotogramétrica ejecutable;
- pruebas de bajo paralaje, planaridad, reflejos y desconexión;
- fallos geométricos terminan en `BLOCKED`;
- degradaciones recuperables terminan en `REVIEW`;
- alpha23 y CI exitosos;
- ITER-005 archivada;
- `CURRENT.md` actualizado con ITER-006.
