# ITER-003 — Modelo OT/plano/revisión y puerta de radio

## Identificación

- **Fecha de inicio:** 2026-07-21
- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8`
- **Commit base:** `30975056099349cc76549ead4e7a1d2544ec7f88`
- **Commit funcional de cierre:** `7db0d60036c7afb4672dad47e203e7cbafa0f7a8`
- **Versión de aplicación:** `0.18.0-alpha21`
- **Fases del roadmap:** `R0`, `R1`, `R2`, `R3`
- **Responsable técnico:** MedinaParra / asistencia de ingeniería

## Objetivo

Implementar una representación revisionada de la evidencia dimensional, conservar cada cota por código de material, OT, plano, revisión, tipo y superficie, e impedir que una diferencia superior a 30 mm en radio produzca una coincidencia automática.

## Alcance ejecutado

### Incluido

- modelo Java inmutable `MaterialFamily -> WorkOrder -> Drawing -> DrawingRevision -> DimensionEvidence`;
- tipos explícitos para radio de manto desnudo y radio exterior revestido;
- conservación de valor y unidad originales y valor normalizado en milímetros;
- fuente, revisión, aprobación, método de extracción y confianza por evidencia;
- base SQLite normalizada separada de la base heredada;
- siembra idempotente de las cinco familias con planos dimensionales auditados;
- conflictos bloqueantes para `4162045`, `4196111` y `10510386`;
- evaluación `MATCH`, `REVIEW` y `BLOCKED` con explicación legible;
- pruebas de frontera de 29, 30 y 31 mm;
- prueba de conversión de pulgadas a milímetros;
- inicialización de la nueva base desde `LauncherActivity`;
- integración del gate en GitHub Actions.

### Excluido

- reemplazo completo de la interfaz heredada de identificación;
- extracción automática de PDF;
- obtención del SHA-256 real de todos los documentos Drive;
- puntuación multivariable de cotas axiales;
- bundle adjustment y calibración;
- prueba física en teléfonos;
- validación metrológica.

## Cambios y decisiones

### Código y arquitectura

Se agregaron:

- `RevisionedPulleyKnowledgeCore.java`;
- `RevisionedKnowledgeOpenHelper.java`;
- `RevisionedPulleyKnowledgeV46Test.java`;
- `run_revisioned_knowledge_v46_test.sh`.

La base revisionada usa las tablas:

- `material_family`;
- `work_order`;
- `drawing`;
- `drawing_revision`;
- `dimension_evidence`;
- `validation_conflict`.

La base heredada `pulley_knowledge.db` no fue destruida ni migrada de manera irreversible. La nueva base se denomina `pulley_revisioned_knowledge.db`, permitiendo comparar resultados y revertir lógicamente durante la etapa alpha.

La clave de evidencia implementada es:

```text
material_code + ot_number + drawing_number + revision + dimension_kind + surface_kind
```

La regla dura implementada es:

```text
error_radio_mm = abs(radio_observado - radio_referencia)
MATCH solo cuando error_radio_mm <= 30 y no existe conflicto bloqueante
```

Las superficies incompatibles son:

- `BARE_SHELL` para el manto metálico;
- `OUTER_LAGGING` para la superficie exterior visible con revestimiento.

Una observación que mezcla ambos tipos se rechaza antes de evaluar la coincidencia.

### Datos y evidencia

Se cargaron como evidencia revisionada:

- `10415863`: OT-262 y OT-1702; OT-1702 conserva radios 700/720 mm y cotas axiales del plano `SKM-1702-03`;
- `10415860`: OT-243 y OT-1645; OT-1645 conserva radios 400/420,25 mm y cotas del plano `ARMADO-OT-1645`;
- `4162054`: OT-651; radios 304,80/336,55 mm, cara 1524 mm, cuerpo 2032 mm y eje 3051,18 mm;
- `4196149`: OT-781; radio original 15 pulgadas, normalizado a 381 mm, y cara original 90 pulgadas;
- `1462827`: OT-1570; radios 508/523 mm, cuerpo 2100 mm, centros 2630 mm y eje 2870 mm.

Se mantuvieron bloqueados:

- `4162045`, por identidad documental incompatible;
- `4196111`, por posible conflicto con `4162038` y falta de radio aprobado;
- `10510386`, por ausencia de radio trazable para OT-270.

Los campos `sourceSha256` sembrados en esta iteración son identificadores deterministas provisionales de migración, no hashes obtenidos de los bytes originales de cada PDF. No deben presentarse como verificación criptográfica del documento. `DATA-007` permanece abierto hasta reemplazarlos por SHA-256 reales.

## Evidencia y validación

| Prueba o revisión | Entorno | Resultado | Evidencia |
|---|---|---|---|
| Modelo y reglas Java | prueba pura JDK | PASS | `RevisionedPulleyKnowledgeV46Test` |
| Error de radio 29 mm | prueba pura JDK | PASS / MATCH | dentro del máximo |
| Error de radio 30 mm | prueba pura JDK | PASS / MATCH | frontera incluida |
| Error de radio 31 mm | prueba pura JDK | PASS / BLOCKED | supera el máximo |
| Conversión 15 in a 381 mm | prueba pura JDK | PASS | unidad original conservada |
| Superficie incompatible | prueba pura JDK | PASS / excepción controlada | manto y revestimiento separados |
| Migración/siembra repetida | prueba pura JDK | PASS | `upsert` idempotente |
| Conflictos críticos | prueba pura JDK | PASS / BLOCKED | tres familias bloqueadas |
| GitHub Actions producto | run `#409` | PASS | 20 gates puros, compilación APK y cierre OCCT |
| Historial de iteraciones | GitHub Actions | PASS | validador independiente |
| Prueba en dispositivo | Samsung A15/Honor X5C | NO EJECUTADA | fuera de alcance |
| Medición física | instrumento trazable | NO EJECUTADA | fuera de alcance |

## Resultados

- La evidencia dimensional ya no necesita sobrescribirse entre OT diferentes.
- Radio, diámetro implícito y espesor radial dejan de compartir un campo ambiguo.
- La frontera solicitada de 30 mm está implementada y probada.
- Los planos en pulgadas conservan el valor original y su conversión.
- Las decisiones muestran radio observado, referencia, error y fuente.
- La APK alpha21 compila correctamente y conserva el cierre STEP/OCCT para `arm64-v8a`.
- La nueva base todavía convive con la interfaz heredada; la migración funcional completa continúa en ITER-004.
- No se declara validación industrial ni metrológica.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Mitigación | Estado |
|---|---|---|---|---|
| DATA-001 | crítica | la interfaz heredada aún consulta dimensiones planas | conectar motor revisionado en ITER-004 | abierto |
| DATA-002 | crítica | `4162045` tiene identidad incompatible | mantener `BLOCKED` | abierto |
| DATA-006 | alta | `4196111` puede mezclar `4162038` | auditoría documental adicional | abierto |
| DATA-007 | alta | hashes de fuente son provisionales, no hashes de bytes PDF | calcular SHA-256 reales al importar | abierto |
| DATA-009 | media | no existe todavía puntuación axial multivariable | implementar en ITER-004 | abierto |
| VALID-001 | crítica | sin campaña física | mantener uso industrial prohibido | abierto |
| RECON-001 | alta | no existe bundle adjustment | abordar en ITER-006 | abierto |
| ABI-001 | media | solo `arm64-v8a` | estudiar cierre OCCT 32-bit posteriormente | abierto |

## Estado del roadmap

| Fase | Antes | Después | Puerta de salida |
|---|---|---|---|
| R0 — Gobierno | 70 % | 78 % | abierta: hashes reales pendientes |
| R1 — Modelo dimensional | 10 % | 75 % | abierta: falta adopción completa por UI/sesiones |
| R2 — Saneamiento | 25 % | 50 % | abierta: tres familias bloqueadas |
| R3 — Identificación | 15 % | 55 % | abierta: falta puntuación axial y auditoría de decisión |
| R4–R8 | sin cambio | sin cambio | abiertas |

## Siguiente iteración obligatoria

### ITER-004 — Identificación multivariable y auditoría de decisiones

Objetivos concretos:

1. evaluar radio y cotas axiales por OT/plano/revisión;
2. diferenciar contradicciones críticas de datos faltantes;
3. producir puntuación y razones por dimensión;
4. persistir una auditoría reproducible de cada decisión;
5. evitar `MATCH` cuando solo existe coincidencia por código o nombre;
6. agregar pruebas con cotas de OT distintas, revisiones desconocidas y evidencia parcial;
7. publicar `0.18.0-alpha22` con CI exitoso.

## Criterios de entrada y salida

### Entrada

- partir de `7db0d60036c7afb4672dad47e203e7cbafa0f7a8` o una cabeza posterior del mismo PR;
- conservar la regla de radio de 30 mm;
- mantener la base heredada sin destrucción;
- mantener los tres códigos conflictivos bloqueados;
- reconocer que los hashes PDF reales siguen pendientes.

### Salida

- motor multivariable probado;
- auditoría persistente de decisión;
- casos de OT cruzada bloqueados o enviados a revisión;
- explicaciones con residuos y fuentes;
- CI del producto e historial en `success`;
- ITER-004 archivada;
- `CURRENT.md` actualizado con ITER-005 y sus criterios.
