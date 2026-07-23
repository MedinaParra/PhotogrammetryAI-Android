# Estado actual de iteraciones

**Actualizado:** 2026-07-23  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha47`  
**Variante:** `SKM Polea AI Lab` — paquete `cl.skm.pulleyai.lab`

## Estado acumulado

- Alpha47 Lab compila para `arm64-v8a` con STEP/OCCT y se instala junto a alpha44.
- Evidencia dimensional, identificación, safety gate, pose graph y reconstrucción conservan sus gates automáticos.
- La ventana BA permanece limitada a 8 cámaras, 120 puntos y 1500 observaciones con cámara global 0 fija.
- Las etapas base, rotacional y focal permanecen acotadas y con fallback fail-closed.
- RANSAC fundamental, SVD esencial, cheirality y triangulación DLT poseen checkpoints internos cooperativos.
- La publicación runtime usa journal SQLite recuperable y promoción protegida de puntero.
- Antes de exportar se verifican rutas, tamaños, archivos listados y SHA-256.
- Cada generación READY firma evidencia mediante Ed25519 local verificable.
- La captura exige bloquear una sola polea, completar dos anillos y verificar solape.
- La variante Lab importa paquetes `skm-polea-capture/*` mediante el selector de Android.
- Cada fotografía importada se valida contra su SHA-256 antes del análisis.
- Alpha47 conserva el diagnóstico alpha46 y ejecuta un segundo pipeline multiescala independiente.
- La pirámide usa escalas `1.0 / 0.80 / 0.64` y hasta 1400 características por fotograma.
- Cada característica posee orientación por centroide de intensidad y descriptor binario rotado de 256 bits.
- El matcher registra observaciones estrictas, expansión guiada, ajuste afín, cobertura ROI, escala y orientación.
- Cada par pasa después por matriz fundamental RANSAC y conserva inliers, relación de inliers, RMS, estado y causa.
- El grafo primario acepta únicamente `STRONG/USABLE`.
- Un grafo diagnóstico separado puede incorporar `BRIDGE`, sin habilitar BA, nube métrica, CAD ni uso industrial.
- La variante Lab usa una clave pública estable de prueba; no representa identidad corporativa.
- No existe todavía campaña física controlada ni calificación industrial o metrológica.

## Avance global estimado

- **Avance integral: 99 %.**
- **Madurez alpha: 99 %.**
- **Preparación industrial/metrológica: 28 %.**

El matcher y la trazabilidad mejoran con evidencia real, pero la preparación industrial no aumenta porque el ZIP importado todavía no crea tracks persistentes, pose global o nube métrica validada.

## Iteración actual o última cerrada

### ITER-024 — Reprocesamiento ZIP multiescala y grafo diagnóstico separado

Registro: `history/ITER-024_2026-07-23_multiscale-oriented-zip-reprocessing.md`

- detector FAST-like en tres escalas;
- orientación por centroide de intensidad;
- descriptor binario rotado de 256 bits;
- matching simétrico y expansión guiada por afín RANSAC;
- matriz fundamental como segundo gate;
- evidencia profunda por par;
- grafo primario separado de puentes diagnósticos;
- comparación alpha46/alpha47;
- exportación JSON y ZIP sin fotografías fuente;
- producto run `#875`: `success`;
- historial run `#339`: `success`;
- 42 gates aprobados;
- Gradle, firma Lab y cierre OCCT aprobados;
- APK inicial publicada desde `5f105ec4487662fb09213a720dc71277c74142f1`;
- APK SHA-256 inicial `7c65afe70192014aee7752a11ef12aa99dafed523abc164f8775541aeb344789`;
- uso industrial continúa bloqueado.

## Bloqueos activos

1. `TRACK-001`: el ZIP importado aún no produce tracks multivista persistentes;
2. `IMPORT-001`: el ZIP importado no se transforma todavía en sesión SQLite completa;
3. `IMPORT-002`: el diagnóstico no alimenta automáticamente BA y nube global del pipeline principal;
4. `BRIDGE-002`: las aristas `BRIDGE` demuestran continuidad probable, pero están prohibidas para geometría;
5. `ROI-001`: la cobertura relativa no es una segmentación semántica exacta de la polea;
6. `FIELD-001`: falta ejecutar físicamente `demo_3` dentro de alpha47 Lab;
7. `TARGET-001`: el bloqueo de captura sigue siendo heurístico y centrado;
8. `SIGN-003`: la clave Lab es pública y no representa identidad corporativa;
9. `RUNTIME-004`: Bitmap decode y algunas llamadas nativas no admiten checkpoint interno;
10. `TX-002`: journal recuperable, pero no transacción ACID única SQLite/filesystem;
11. `INTR-002`: observabilidad focal validada sintéticamente, no en cámaras físicas;
12. `BA-003`: no existe BA global ni Schur complement;
13. `STAT-001`: sensibilidad e intervalos no representan incertidumbre física o dimensional;
14. `DEVICE-001`: campaña Samsung A15 no ejecutada;
15. `DEVICE-002`: campaña Honor X5C no ejecutada;
16. `VALID-001`: metrología trazable ausente;
17. `DATA-007`: hashes reales de todos los PDF pendientes;
18. `ABI-001`: OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-025 — Tracks multivista persistentes desde ZIP importado

Construir tracks únicamente desde inliers de aristas primarias, impedir colisiones de dos observaciones del mismo fotograma, conservar procedencia por par, exportar histograma y tracks cross-ring, y mantener excluidas todas las aristas `BRIDGE` de cualquier reconstrucción geométrica.

## Criterios de entrada

- conservar los 42 gates acumulados;
- mantener importación ZIP segura y SHA-256 fail-closed;
- mantener comparación alpha46/alpha47;
- mantener grafo primario separado de puentes diagnósticos;
- mantener paquete Lab separado y firma de prueba estable;
- conservar safety gate, journal e integridad automática;
- no declarar reconstrucción métrica desde el diagnóstico;
- mantener PR en borrador.

## Criterios de salida

- tracks construidos solo desde inliers fundamentales de aristas primarias;
- ausencia de dos observaciones del mismo fotograma dentro de un track;
- procedencia por par persistida;
- histograma de longitudes y tracks cross-ring exportados;
- aristas `BRIDGE` excluidas del ensamblaje de tracks;
- nueva alpha Lab compilada;
- CI producto e historial exitosos;
- ITER-025 archivada.
