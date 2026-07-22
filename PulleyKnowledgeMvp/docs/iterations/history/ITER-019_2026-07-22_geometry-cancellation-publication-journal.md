# ITER-019 — Checkpoints geométricos y journal coordinado

## Identificación

- **Fecha de cierre:** 2026-07-22
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8` (borrador)
- **Versión:** `0.18.0-alpha40`
- **Commit funcional validado:** `58e946f852bf7dfb796a3c2c211f95ba35cd4423`
- **GitHub Actions:** producto run `#754`
- **Fases:** R4, R5 y R8

## Objetivo

Cerrar dos riesgos del runtime: permitir que una cancelación o vencimiento sea observado dentro de los bucles geométricos costosos, y coordinar la promoción de evidencias en filesystem con un journal SQLite recuperable para que una interrupción no deje una generación comprometida sin puntero ni reemplace una generación válida por evidencia incompleta.

## Alcance ejecutado

### Incluido

- checkpoints cooperativos dentro del RANSAC de matriz fundamental;
- checkpoints durante scoring de Sampson, normalización, acumulación y Jacobi;
- checkpoints en las dos descomposiciones SVD de la matriz esencial;
- checkpoints por candidato de pose, cheirality y DLT interno;
- checkpoints en triangulación dispersa y Jacobi por punto;
- puente opcional por reflexión para mantener compatibles los gates Java históricos;
- journal de publicación con fases `PREPARED`, `FILES_COMMITTED`, `POINTER_PUBLISHED`, `COMPLETE` y `ROLLED_BACK`;
- store Android SQLite independiente `runtime_publication_journal.db`;
- promoción del puntero activo mediante archivo temporal y respaldo recuperable;
- recuperación automática antes de crear la siguiente transacción;
- republicación del puntero cuando la carpeta comprometida y su manifiesto existen;
- rollback y eliminación de `.pending` cuando no existe generación comprometida completa;
- gate v61 con fallos inyectados después de promover la carpeta;
- publicación Android `0.18.0-alpha40`.

### Excluido

- cancelación dentro de la decodificación nativa de `Bitmap`;
- transacción ACID única que abarque simultáneamente SQLite y filesystem;
- recuperación frente a daño físico del almacenamiento;
- sincronización remota o respaldo en servidor;
- firma corporativa o atestación remota;
- campañas físicas Samsung A15 y Honor X5C;
- validación metrológica trazable;
- `armeabi-v7a`.

## Cambios y decisiones

Los núcleos `FundamentalMatrixCore`, `EssentialPoseCore` y `SparseTriangulationCore` continúan siendo utilizables como Java puro. Los checkpoints se resuelven mediante un puente opcional por reflexión: en Android, `RuntimeCancellationBridge` propaga cancelación y deadline; en pruebas históricas que compilan el núcleo aislado, la ausencia del puente conserva el comportamiento anterior.

La publicación de evidencia usa un protocolo de recuperación, no una afirmación de atomicidad entre dos tecnologías distintas. Primero se registra `PREPARED`, luego la carpeta `.pending` se promueve a `.committed`, se registra `FILES_COMMITTED`, se publica el puntero activo y finalmente se registra `COMPLETE`.

Si el proceso cae después de promover la carpeta pero antes de publicar el puntero, la siguiente transacción encuentra el registro incompleto. Cuando la carpeta `.committed` contiene `generation_manifest.json`, el puntero se reconstruye y el journal termina en `COMPLETE`. Cuando falta esa evidencia completa, los restos `.pending` se eliminan y el journal termina en `ROLLED_BACK`.

El puntero anterior no se elimina directamente. Se mueve a respaldo, se promueve el archivo temporal y solo después se elimina el respaldo. Si falla la promoción, se intenta restaurar el puntero anterior.

## Evidencia y validación

| Prueba o revisión | Entorno | Resultado | Evidencia |
|---|---|---|---|
| cancelación durante RANSAC fundamental | JDK | PASS | excepción en etapa `FUNDAMENTAL_RANSAC_*` |
| cancelación durante pose esencial | JDK | PASS | excepción en etapa `ESSENTIAL_*` |
| cancelación durante triangulación | JDK | PASS | excepción en etapa `SPARSE_TRIANGULATION_*` |
| commit normal journalizado | JDK | PASS | fase final `COMPLETE` y puntero activo |
| fallo inyectado tras promover carpeta | JDK | PASS | no se publica puntero prematuro |
| recuperación de carpeta comprometida | JDK | PASS | puntero reconstruido y fase `COMPLETE` |
| generación comprometida ausente | JDK | PASS | fase `ROLLED_BACK` y `.pending` eliminado |
| gates acumulados | GitHub Actions run `#754` | PASS | 36 gates previos y nuevos aprobados |
| compilación Android | GitHub Actions run `#754` | PASS | Gradle `assembleDebug` aprobado |
| cierre STEP/OCCT | GitHub Actions run `#754` | PASS | SHA-256 del AAR y 25 bibliotecas nativas aprobados |
| APK alpha40 | GitHub Actions run `#754` | PASS | artefacto `SKM-Polea-AI-CAD-STEP-v0.18.0-alpha40` |
| campaña Samsung A15 | hardware | NO EJECUTADA | dispositivo no conectado a CI |
| campaña Honor X5C | hardware | NO EJECUTADA | dispositivo no conectado a CI |
| metrología | instrumentos trazables | NO EJECUTADA | fuera de alcance de la iteración |

## Resultados

- La cancelación ya no depende únicamente de checkpoints entre fases completas de geometría.
- RANSAC, SVD, cheirality y DLT pueden abortar cooperativamente sin publicar una solución parcial.
- Una carpeta comprometida no se pierde si el proceso cae antes de promover el puntero.
- Una generación incompleta no puede reemplazar a la generación activa anterior.
- El store SQLite conserva el estado de recuperación sin mezclarse con la base de captura.
- Alpha40 compila con STEP/OCCT para `arm64-v8a`.
- No se declara atomicidad ACID entre SQLite y filesystem, calificación industrial ni exactitud metrológica.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Mitigación | Estado |
|---|---|---|---|---|
| RUNTIME-004 | media | `Bitmap` decode y algunas llamadas nativas no admiten checkpoint interno | límites preventivos y futura instrumentación | abierto |
| TX-002 | media | el protocolo es recuperable, pero no una transacción ACID única entre SQLite/filesystem | mantener journal, manifiesto e integridad fail-closed | mitigado |
| INTR-002 | alta | observabilidad focal validada sintéticamente, no en cámaras físicas | campañas A15/X5C | abierto |
| BA-003 | media | no existe BA global ni Schur complement | iteración posterior | abierto |
| STAT-001 | alta | sensibilidad e intervalos no representan incertidumbre física | mantener etiquetas y campaña trazable | abierto |
| SIGN-001 | alta | manifiestos no poseen firma corporativa o atestación | siguiente iteración criptográfica | abierto |
| DEVICE-001 | crítica | campaña Samsung A15 no ejecutada | prueba física | abierto |
| DEVICE-002 | crítica | campaña Honor X5C no ejecutada | prueba física | abierto |
| VALID-001 | crítica | metrología trazable ausente | campaña R8 | abierto |
| DATA-007 | media | hashes reales de todos los PDF pendientes | auditoría documental | abierto |
| ABI-001 | media | OCCT solo `arm64-v8a` | evaluar AAR universal | abierto |

## Estado del roadmap

- Avance integral anterior: **94 %**.
- Avance integral después de ITER-019: **96 %**.
- Madurez funcional alpha estimada: **99 %**.
- Preparación industrial/metrológica estimada: **25 %**.

El avance funcional aumenta por cancelación profunda y recuperación de publicación. La preparación industrial no aumenta porque no hubo ejecución en hardware objetivo, calibración física ni instrumentos trazables.

## Siguiente iteración obligatoria

### ITER-020 — Firma local verificable y evidencia automática de ejecución

- firmar criptográficamente el manifiesto canónico de cada generación;
- verificar firma, clave pública y hash antes de exportar;
- declarar de forma explícita si la clave es hardware-backed o software;
- mantener `corporateIdentity=false` mientras no exista identidad corporativa atestada;
- producir evidencia automática de ejecución para campañas device-alpha;
- conservar bloqueo industrial sin campañas físicas y metrología trazable.

## Criterios de entrada y salida

### Entrada

- conservar los 36 gates acumulados;
- mantener journal recuperable e integridad SHA-256 fail-closed;
- mantener cámara 0, principal point y límites BA invariantes;
- no atribuir identidad corporativa a una clave local;
- mantener PR en borrador y sin fusionar.

### Salida

- firma local de manifiesto implementada y verificada;
- payload, firma o clave alterados bloqueados;
- estado hardware/software de la clave declarado sin suposiciones;
- evidencia automática de ejecución persistida;
- nueva alpha compilada;
- CI de producto e historial exitosos;
- ITER-020 archivada.
