# Roadmap de ingeniería — SKM Polea AI

**Estado:** activo  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**Base de producto:** `product/single-device-photogrammetry-v1`  
**Actualizado:** 2026-07-22  
**Última iteración cerrada:** `ITER-021`  
**Siguiente iteración:** `ITER-022`  
**Versión:** `0.18.0-alpha42`  
**Avance integral estimado:** `98 %`

> El avance integral representa madurez funcional alpha. La preparación industrial/metrológica permanece en 27 % y no equivale a calificación para liberación dimensional.

## 1. Propósito del producto

SKM Polea AI debe operar completamente en Android y de forma local para:

1. capturar sesiones fotográficas trazables de poleas;
2. reconstruir experimentalmente su geometría visible;
3. estimar manto, eje y componentes con incertidumbre explícita;
4. identificar referencias históricas por código, OT, plano y revisión;
5. comparar reconstrucción, cotas aprobadas y componentes STEP;
6. impedir decisiones cuando la evidencia sea insuficiente o contradictoria;
7. conservar fotografías, hashes, parámetros, decisiones, diagnósticos y resultados reproducibles.

La aplicación sigue siendo una alpha de ingeniería. No está autorizada para liberación dimensional ni metrología industrial.

## 2. Principios no negociables

- El plano aprobado gobierna sobre nombres, informes, WIP y aliases.
- Una OT o revisión nunca sobrescribe a otra.
- Código de material no demuestra identidad geométrica por sí solo.
- `BARE_SHELL` y `OUTER_LAGGING` son superficies distintas.
- Una ausencia de evidencia produce `REVIEW` o `BLOCKED`, nunca una coincidencia inventada.
- Una contradicción crítica domina cualquier puntuación promedio.
- La IMU y la órbita guiada son priors, no verdad geométrica.
- Pruebas sintéticas y compilación no equivalen a validación física.
- Un perfil de cámara no es calibración hasta ser validado con patrón físico.
- Todo optimizador declara alcance, gauge, priors, límites y métricas antes/después.
- Cancelación, timeout o evidencia parcial nunca pueden publicar geometría optimizada.
- Solo una generación comprometida puede considerarse vigente.
- Una huella SHA-256 o firma local no equivale a aprobación técnica ni identidad corporativa.
- Un intervalo empírico de reproyección no equivale a incertidumbre metrológica.
- Una fotografía nítida no es automáticamente útil: debe aportar cobertura y diversidad geométrica.

## 3. Regla dimensional de identificación

```text
radio_observado = radio de la superficie realmente visible
radio_referencia = radio aprobado para OT/plano/revisión/superficie
error_radio_mm = abs(radio_observado - radio_referencia)
```

- `MATCH`: `error_radio_mm <= 30` y no existe contradicción crítica.
- `REVIEW`: el radio cumple, pero faltan cotas o existe ambigüedad recuperable.
- `BLOCKED`: el radio supera 30 mm, falta referencia primaria o hay evidencia incompatible.

La diferencia de 30 mm se evalúa en radio. También se consideran largo de cara, centros de soportes, largo de eje, diámetros funcionales, revestimiento, equipo, posición, OT, plano y revisión.

## 4. Arquitectura comprobada

### Evidencia dimensional

```text
MaterialFamily
└── WorkOrder
    └── Drawing
        └── DrawingRevision
            └── DimensionEvidence
```

Clave mínima:

```text
material_code + ot_number + drawing_number + revision + dimension_kind + surface_kind
```

### Captura, reconstrucción y admisión runtime

```text
CaptureActivity
→ GuidedCaptureAdmissionCore
→ CaptureStore
→ SessionOverlapAnalyzer
  → VisualFeatureCore
  → FundamentalMatrixCore
  → EssentialPoseCore
  → SparseTriangulationCore
→ RuntimeFramePreparationCache
├── RuntimeSupplementalMetricsBuilder
└── RuntimeBundleWindowBuilder
→ PhotogrammetrySafetyGate
→ Local / Rotational / Conditioned Focal BA
→ RuntimeEvidenceTransactionCore + PublicationJournalStore
→ generación .committed + puntero activo
→ LocalEvidenceSignatureCore
→ SessionPackageExporter
```

Límites BA:

- 8 cámaras;
- 120 puntos;
- 1500 observaciones;
- cámara global 0 fija;
- puntos, traslaciones, pequeñas rotaciones y focal secundaria condicionada;
- principal point y distorsión fijos;
- pérdida Huber, damping, priors y fallback exacto.

## 5. Estado técnico comprobado

### Disponible

- captura Camera2 horizontal, sesiones SQLite, IMU, calidad y SHA-256;
- dos anillos de 12 sectores con guía visible;
- máximo de dos vistas aceptadas por anillo y sector;
- segunda vista de la celda exige 6° de separación angular;
- bloqueo de borde dominante y detalle central insuficiente;
- selección balanceada de hasta 48 frames y presupuesto de recursos;
- features, matching, fundamental, esencial, pose, triangulación y tracks;
- checkpoints dentro de features, RANSAC, SVD, cheirality y DLT;
- pose graph experimental, nube dispersa, cilindro y escala métrica condicionada;
- competencia homografía/fundamental y degradación visual por sesión;
- safety gate fail-closed `READY/REVIEW/BLOCKED` conectado al runtime;
- BA local, rotacional y focal acotado con fallback;
- deadline monotónico, cancelación visible y evidencia ABORTED/BLOCKED;
- generaciones `.pending`/`.committed`, journal recuperable y puntero protegido;
- manifiestos de generación y campaña con ruta, tamaño y SHA-256;
- firma Ed25519 local verificable y proveniencia automática;
- diagnóstico automático de batería, nivel térmico, PSS, heap y RAM;
- evidencia revisionada por OT/plano/revisión y auditoría append-only;
- STEP/OCCT para `arm64-v8a`, teselación y ensamblaje rígido;
- ZIP que exporta exclusivamente la generación comprometida activa;
- 38 gates Java y compilación alpha42.

### Deuda crítica actual

- los umbrales de captura guiada requieren más ajuste con datos reales;
- no existe detector semántico de polea;
- la clave local no representa identidad corporativa ni atestación remota;
- reinstalación o borrado de datos puede cambiar la clave local;
- no existe BA global ni Schur complement;
- los intervalos estadísticos no se propagan a dimensiones físicas;
- `Bitmap` decode y algunas llamadas nativas no admiten checkpoint interno;
- el journal no es una transacción ACID única SQLite/filesystem;
- no hay calibración física de Samsung A15 ni Honor X5C;
- no se ejecutaron tres campañas calificantes por dispositivo;
- faltan SHA-256 reales de algunos PDF;
- `armeabi-v7a` no está disponible;
- no existe comparación metrológica trazable.

## 6. Fases del roadmap

## R0 — Gobierno de evidencia y trazabilidad — 97 %

Completado: historial inmutable, auditoría append-only, generaciones comprometidas, journal recuperable, manifiestos SHA-256, firma local y exportación fail-closed.

Pendiente: identidad corporativa, atestación remota y hashes binarios reales de todos los documentos.

## R1 — Modelo dimensional revisionado — 88 %

Completado: familia, OT, plano, revisión, dimensión, superficie, unidades, aprobación, conflicto y consultas SQLite.

Pendiente: retirar completamente la interfaz heredada y ampliar tolerancias específicas de plano.

## R2 — Saneamiento de familias históricas — 50 %

Prioridades: resolver familias contradictorias, localizar radios aprobados, completar hashes y cargar tolerancias específicas.

## R3 — Identificación geométrica — 90 %

Completado: regla de radio de 30 mm, cotas axiales, estados por dimensión, bloqueo de cruces OT/revisión, puntuación y auditoría.

Pendiente: tolerancias por plano, migración visual completa y validación física.

## R4 — Fotogrametría robusta para taller — 98 %

Completado: captura guiada de dos anillos, admisión por calidad/diversidad, correspondencias, geometría multivista, degeneración, pose graph, reproyección, cancelación profunda, safety gate y evidencia exportada.

Pendiente: ajuste de umbrales con datasets reales, descriptor más robusto a superficies repetitivas y ground truth controlado.

## R5 — Optimización y calibración — 90 %

Completado: BA base, rotacional y focal condicionado, límites 8/120/1500, cámara 0 fija, Huber, priors, damping, métricas y fallback.

Pendiente: BA global/Schur, calibración física, principal point/distorsión bajo protocolo e incertidumbre dimensional.

## R6 — Robustez Android y dispositivos — 58 %

Completado en software: deadline, cancelación, fallback, telemetría, recuperación, firma local, continuidad auditable preparada y experiencia de captura remediada con evidencia real.

Pendiente físico:

- Samsung A15;
- Honor X5C;
- tres ejecuciones calificantes por modelo;
- cierre forzado y recuperación real;
- STEP/JNI y BA reales;
- Android 10–15;
- medición térmica externa.

## R7 — STEP, componentes y ensamblaje — 48 %

Disponible: importación OCCT/JNI, BRep, teselación, bounding box, normales, transformaciones rígidas, autoprueba, gates de empaquetado y comparación cuantitativa experimental.

Pendiente: grandes conjuntos reales, unidades/orientaciones diversas, memoria/JNI en dispositivo, componentes de taller y factibilidad `armeabi-v7a`.

## R8 — Calificación de ingeniería — 14 %

Disponible: estructura fail-closed, esquema de campaña, diagnóstico automático, manifiesto reproducible, firma local y evidencia de una prueba de uso real.

Pendiente: repetibilidad, reproducibilidad, instrumentos trazables, incertidumbre dimensional, criterios por uso, revisión de calidad y versión calificada.

### Iteraciones cerradas recientes

- ITER-016: rotaciones BA acotadas e intervalos estadísticos.
- ITER-018: focal condicionada y observabilidad.
- ITER-019: cancelación geométrica y journal recuperable.
- ITER-020: firma local y proveniencia automática.
- ITER-021: captura guiada y remediación de prueba de terreno.

Los registros completos están en `docs/iterations/history/`.

## 7. Protocolo obligatorio de iteraciones

Cada iteración debe:

1. leer `docs/iterations/CURRENT.md` y la última cerrada;
2. ejecutar un objetivo medible;
3. conservar archivos, commits, pruebas y resultados;
4. registrar fallos, riesgos y deuda sin ocultarlos;
5. crear un archivo inmutable en `docs/iterations/history/`;
6. actualizar `CURRENT.md` y este roadmap;
7. declarar la siguiente iteración con criterios de entrada/salida;
8. pasar `validate-iteration-history.yml` y el workflow de producto.

Una iteración no se considera cerrada sin CI, historial y siguiente paso.

## 8. Estrategia de versiones

- `alpha`: implementación y pruebas sintéticas.
- `device-alpha`: ejecución real controlada en teléfonos objetivo.
- `engineering-beta`: campañas físicas parciales y métricas comparativas.
- `validation-candidate`: protocolo metrológico ejecutado y en revisión.
- `qualified`: aprobación técnica y de calidad para un uso delimitado.

## 9. Próxima iteración obligatoria

### ITER-022 — Calificación device-alpha con continuidad de clave

Objetivos:

1. consumir únicamente generaciones automáticas con integridad y firma válidas;
2. contar ejecuciones por modelo objetivo;
3. exigir tres ejecuciones calificantes para Samsung A15 y Honor X5C;
4. verificar continuidad de clave entre ejecuciones;
5. exigir rotación explícita y auditable ante cambio de clave;
6. excluir evidencia manual del conteo;
7. mantener READY separado de calificación metrológica;
8. publicar una nueva alpha con gates de continuidad y dispositivo.

Criterios de cierre:

- ingesta de generaciones firmadas incorporada;
- continuidad y rotación de clave verificadas;
- evidencia manual excluida;
- tres ejecuciones automáticas exigidas por dispositivo;
- 38 gates o más en `success`;
- Gradle, APK y cierre OCCT aprobados;
- ITER-022 archivada y siguiente etapa declarada.
