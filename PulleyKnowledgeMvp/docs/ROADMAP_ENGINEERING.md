# Roadmap de ingeniería — SKM Polea AI

**Estado:** activo  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**Base de producto:** `product/single-device-photogrammetry-v1`  
**Actualizado:** 2026-07-21  
**Última iteración cerrada:** `ITER-016`  
**Siguiente iteración:** `ITER-017`  
**Versión:** `0.18.0-alpha34`  
**Avance integral estimado:** `92 %`

> El avance integral representa madurez del producto alpha respecto del roadmap funcional, no el promedio aritmético de las fases. La preparación industrial/metrológica permanece en 24 %.

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
- Una huella SHA-256 reproducible no equivale a firma digital ni aprobación técnica.
- Un intervalo empírico de reproyección no equivale a incertidumbre metrológica.
- Temperatura de batería y estado térmico Android son diagnósticos, no metrología térmica.

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

Cada decisión conserva observaciones, referencias, residuos, tolerancias, fuentes, estado, puntuación y huella reproducible.

### Reconstrucción y admisión runtime

```text
CaptureStore
→ SessionOverlapAnalyzer
  → VisualFeatureCore + checkpoints profundos
→ RuntimeFramePreparationCache
├── RuntimeSupplementalMetricsBuilder
└── RuntimeBundleWindowBuilder
→ PhotogrammetrySafetyGate
→ LocalBundleAdjustmentCore
→ RotationalBundleAdjustmentCore
→ RuntimeReconstructionDecision
→ RuntimeEvidenceTransactionCore
→ generación .committed + puntero activo
→ SessionPackageExporter
```

Límites BA:

- 8 cámaras;
- 120 puntos;
- 1500 observaciones;
- cámara global 0 fija;
- puntos, traslaciones y pequeñas rotaciones de cámaras secundarias;
- intrínsecos todavía fijos;
- pérdida Huber, damping y priors.

## 5. Estado técnico comprobado

### Disponible

- captura Camera2 horizontal, sesiones SQLite, IMU, calidad y SHA-256;
- selección balanceada de hasta 48 frames y presupuesto de recursos;
- features, matching, fundamental, esencial, pose, triangulación y tracks;
- checkpoints de cancelación dentro de detección Harris y matching;
- pose graph experimental, nube dispersa, cilindro y escala métrica condicionada;
- competencia homografía/fundamental y degradación visual por sesión;
- safety gate fail-closed `READY/REVIEW/BLOCKED` conectado al runtime;
- ventana BA desde cámaras, puntos, tracks y píxeles reales;
- BA local acotado con puntos y traslaciones;
- segunda etapa rotacional acotada con cámara 0 fija;
- límites rotacionales, prior, damping y fallback exacto al BA base;
- mediana, MAD, P90 e intervalo empírico 95 % de residuos etiquetado como no metrológico;
- preparación compartida entre métricas y ventana BA;
- deadline monotónico de 180 s y cancelación visible;
- generaciones runtime `.pending`/`.committed` con rollback y puntero activo;
- generación `ABORTED` sin ventana BA parcial;
- manifiesto de generación con ruta, tamaño y SHA-256;
- manifiesto de campaña que vincula APK instalado, dispositivo, sesión, frames y runtime;
- diagnóstico automático de batería, nivel térmico, PSS, heap, RAM y salidas nativas disponibles;
- evidencia revisionada por OT/plano/revisión y auditoría append-only;
- STEP/OCCT para `arm64-v8a`, teselación y ensamblaje rígido;
- ZIP de sesión que exporta exclusivamente la generación comprometida activa;
- 33 gates Java y compilación alpha34.

### Deuda crítica actual

- intrínsecos dependen de Camera2 o perfiles aún no validados físicamente;
- no existe BA global ni Schur complement;
- los intervalos estadísticos no se propagan a dimensiones físicas;
- fundamental, pose, triangulación y Bitmap decode no tienen checkpoints internos;
- la promoción transaccional cubre filesystem, no una transacción coordinada con SQLite;
- la huella de campaña no está firmada con una identidad corporativa;
- no hay calibración física de Samsung A15 ni Honor X5C;
- no se ejecutaron campañas reales de memoria, temperatura, JNI o repetibilidad;
- diagnóstico nativo es parcial en SDK antiguos;
- faltan SHA-256 reales de algunos PDF;
- tres familias históricas permanecen bloqueadas;
- `armeabi-v7a` no está disponible;
- no existe comparación metrológica trazable.

## 6. Fases del roadmap

## R0 — Gobierno de evidencia y trazabilidad — 92 %

Completado: jerarquía de fuentes, historial inmutable, auditoría append-only, generaciones comprometidas, manifiestos SHA-256 y exportación sin mezcla de ejecuciones.

Pendiente: hashes binarios reales de todos los documentos, firma corporativa y journal coordinado SQLite/filesystem.

## R1 — Modelo dimensional revisionado — 88 %

Completado: familia, OT, plano, revisión, dimensión, superficie, unidades, aprobación, conflicto y consultas SQLite.

Pendiente: retirar completamente la interfaz heredada y ampliar tolerancias específicas de plano.

## R2 — Saneamiento de familias históricas — 50 %

Prioridades:

1. resolver `4162045`;
2. resolver `4196111` frente a `4162038`;
3. localizar radio aprobado de `10510386`;
4. completar hashes reales y OT adicionales;
5. cargar tolerancias específicas.

## R3 — Identificación geométrica — 90 %

Completado: radio de 30 mm, cotas axiales, estados por dimensión, bloqueo de cruces OT/revisión, puntuación y auditoría.

Pendiente: tolerancias por plano, migración visual completa y validación física.

## R4 — Fotogrametría robusta para taller — 95 %

Completado: cobertura, correspondencias, geometría multivista, planaridad, degradación, pose graph, reproyección, gate runtime, cancelación en features/matching y evidencia exportada.

Pendiente: checkpoints dentro de fundamental/pose/triangulación, banco real con ground truth y descriptor más robusto a rotación/escala/superficies industriales.

## R5 — Optimización y calibración — 82 %

Completado: ventana BA real, límites 8/120/1500, cámara 0 fija, puntos/traslaciones, pequeñas rotaciones, Huber, priors, damping, métricas antes/después y perfiles Brown-Conrady versionados.

Pendiente: corrección focal condicionada, principal point/distorsión bajo validación física, BA global/Schur, calibración física e incertidumbre dimensional.

## R6 — Robustez Android y dispositivos — 46 %

Completado en software: deadline, cancelación profunda parcial, fallback, telemetría, generaciones transaccionales, recuperación, PSS, memoria, diagnóstico térmico abstracto e historial nativo disponible.

Pendiente físico:

- Samsung A15;
- Honor X5C;
- sesiones de 30–50 fotos;
- cierre forzado y recuperación real;
- STEP/JNI y BA reales;
- Android 10–15;
- medición térmica externa.

## R7 — STEP, componentes y ensamblaje — 48 %

Disponible: importación OCCT/JNI, BRep, teselación, bounding box, normales, transformaciones rígidas, autoprueba, gates de empaquetado y comparación cuantitativa experimental.

Pendiente: grandes conjuntos reales, unidades/orientaciones diversas, memoria/JNI en dispositivo, componentes de taller y factibilidad `armeabi-v7a`.

## R8 — Calificación de ingeniería — 10 %

Disponible: estructura fail-closed, esquema de campaña, diagnóstico automático, manifiesto reproducible y estadística empírica explícitamente no metrológica.

Pendiente: repetibilidad, reproducibilidad, instrumentos trazables, incertidumbre dimensional, criterios por uso, revisión de calidad y versión calificada.

### Iteraciones cerradas recientes

- ITER-009: refinamiento acotado del pose graph.
- ITER-010: gate de calificación profesional.
- ITER-011: revisión runtime visible y campaña inicial.
- ITER-012: ventana BA y telemetría automática.
- ITER-013: métricas suplementarias y BA admitido.
- ITER-014: caché compartida, cancelación y diagnóstico automático.
- ITER-015: cancelación profunda parcial, recuperación transaccional y manifiesto reproducible.
- ITER-016: rotaciones BA acotadas e intervalos estadísticos de residuos.

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

### ITER-017 — Intrínsecos focales condicionados y observabilidad

Objetivos:

1. permitir una corrección pequeña de `fx`/`fy` manteniendo su relación y principal point fijo;
2. usar priors fuertes y límites porcentuales por cámara;
3. exigir suficiente distribución de puntos, profundidad y diversidad de cámaras;
4. bloquear ajustes focales mal condicionados o correlacionados con pose;
5. aceptar únicamente cuando mejora reproyección y profundidad sin superar límites;
6. conservar fallback al resultado rotacional/base;
7. publicar sensibilidad y condición como estadística no metrológica;
8. publicar `0.18.0-alpha35`.

Criterios de cierre:

- corrección focal acotada incorporada;
- observabilidad y límites verificados;
- principal point y distorsión permanecen fijos;
- fallback por mala condición probado;
- sensibilidad no se presenta como calibración física;
- 34 gates o más en `success`;
- Gradle, APK y cierre OCCT aprobados;
- ITER-017 archivada e ITER-018 declarada.