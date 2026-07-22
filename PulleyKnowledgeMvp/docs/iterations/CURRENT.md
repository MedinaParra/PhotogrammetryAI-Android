# Estado actual de iteraciones

**Actualizado:** 2026-07-22  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha46`  
**Variante:** `SKM Polea AI Lab` — paquete `cl.skm.pulleyai.lab`

## Estado acumulado

- Alpha46 Lab compila para `arm64-v8a` con STEP/OCCT y se instala junto a alpha44.
- Evidencia dimensional, identificación, safety gate, pose graph y reconstrucción conservan sus gates automáticos.
- La ventana BA permanece limitada a 8 cámaras, 120 puntos y 1500 observaciones con cámara global 0 fija.
- Las etapas base, rotacional y focal permanecen acotadas y con fallback fail-closed.
- RANSAC fundamental, SVD esencial, cheirality y triangulación DLT poseen checkpoints internos cooperativos.
- La publicación runtime usa journal SQLite recuperable y promoción protegida de puntero.
- Antes de exportar se verifican rutas, tamaños, archivos listados y SHA-256.
- Cada generación READY firma evidencia mediante Ed25519 local verificable.
- La captura exige bloquear una sola polea, completar dos anillos y verificar solape.
- Alpha46 añade importación segura de paquetes `skm-polea-capture/*` mediante el selector de Android.
- Cada fotografía importada se valida contra su SHA-256 antes del análisis.
- El reprocesamiento compara el matcher histórico con un matcher diagnóstico adaptativo.
- Cada par conserva matches, cobertura ROI, razón segundo-mejor, reciprocidad, inliers, RMS, estado y causa de rechazo.
- La ambigüedad repetitiva exige evidencia combinada; baja cobertura aislada ya no equivale automáticamente a repetición.
- El grafo diagnóstico informa componentes, aristas aceptadas y tamaño del componente principal.
- El resultado importado continúa etiquetado como diagnóstico y no publica geometría métrica.
- La variante Lab usa una clave pública estable de prueba; no representa identidad corporativa.
- No existe todavía campaña física controlada ni calificación industrial o metrológica.

## Avance global estimado

- **Avance integral: 99 %.**
- **Madurez alpha: 99 %.**
- **Preparación industrial/metrológica: 28 %.**

El reprocesamiento mejora trazabilidad y capacidad de aprendizaje con evidencia real, pero no aumenta la preparación industrial porque todavía no crea una reconstrucción métrica validada desde el ZIP ni reemplaza el pipeline principal.

## Iteración actual o última cerrada

### ITER-023 — Reprocesamiento portable de ZIP y diagnóstico por par

Registro: `history/ITER-023_2026-07-22_portable-zip-reprocessing-pair-diagnostics.md`

- paquete Lab separado para evitar conflicto con alpha44/45;
- importación ZIP mediante `ACTION_OPEN_DOCUMENT`;
- validación de esquema, límites, rutas y SHA-256;
- reprocesamiento hasta 960 px y 700 características por fotograma;
- comparación baseline/adaptativa por par;
- cobertura relativa a regiones con información visual;
- repetición condicionada por razón, reciprocidad y cobertura;
- matriz fundamental RANSAC y causas de rechazo explícitas;
- exportación JSON y ZIP de diagnóstico;
- producto run `#855`: `success`;
- 41 gates aprobados;
- Gradle, firma Lab y cierre OCCT aprobados;
- APK publicada desde `7e9f1987bd770d2f18c091d9858fde378f7ace93`;
- APK SHA-256 `24f870ac29056dba56244a86d82377cfec275f48c39b67ac19fb36923beafa70`;
- uso industrial continúa bloqueado.

## Bloqueos activos

1. `MATCH-001`: el matcher adaptativo continúa basado en Harris/BRIEF; falta descriptor orientado o multiescala;
2. `IMPORT-001`: el ZIP importado no se transforma todavía en sesión SQLite completa;
3. `IMPORT-002`: el diagnóstico no alimenta automáticamente tracks, BA y nube global del pipeline principal;
4. `ROI-001`: la cobertura relativa no es una segmentación semántica exacta de la polea;
5. `FIELD-001`: falta ejecutar físicamente `demo_3` dentro de alpha46 Lab;
6. `TARGET-001`: el bloqueo de captura sigue siendo heurístico y centrado;
7. `SIGN-003`: la clave Lab es pública y no representa identidad corporativa;
8. `RUNTIME-004`: Bitmap decode y algunas llamadas nativas no admiten checkpoint interno;
9. `TX-002`: journal recuperable, pero no transacción ACID única SQLite/filesystem;
10. `INTR-002`: observabilidad focal validada sintéticamente, no en cámaras físicas;
11. `BA-003`: no existe BA global ni Schur complement;
12. `STAT-001`: sensibilidad e intervalos no representan incertidumbre física o dimensional;
13. `DEVICE-001`: campaña Samsung A15 no ejecutada;
14. `DEVICE-002`: campaña Honor X5C no ejecutada;
15. `VALID-001`: metrología trazable ausente;
16. `DATA-007`: hashes reales de todos los PDF pendientes;
17. `ABI-001`: OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-024 — Reprocesamiento geométrico multiescala sobre evidencia real

Consumir el diagnóstico de `demo_3`, incorporar un descriptor orientado o multiescala con fallback, construir tracks persistentes desde pares vecinos y cross-ring, conservar evidencia por par y permitir que una importación ZIP alimente una reconstrucción separada sin declarar resultado métrico mientras no pase todos los gates geométricos.

## Criterios de entrada

- conservar los 41 gates acumulados;
- mantener importación ZIP segura y SHA-256 fail-closed;
- mantener comparación entre matcher histórico y experimental;
- mantener paquete Lab separado y firma de prueba estable;
- conservar safety gate, journal e integridad automática;
- no declarar reconstrucción métrica desde el diagnóstico;
- mantener PR en borrador.

## Criterios de salida

- descriptor orientado o multiescala integrado con fallback;
- reproducción controlada de `demo_3` documentada;
- tracks multivista persistentes construidos desde importación;
- grafo importado conectado o causas residuales identificadas por par;
- publicación geométrica separada y fail-closed;
- nueva alpha Lab compilada;
- CI producto e historial exitosos;
- ITER-024 archivada.
