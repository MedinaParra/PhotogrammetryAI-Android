# Estado actual de iteraciones

**Actualizado:** 2026-07-23  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha48`  
**Variante:** `SKM Polea AI Lab` — paquete `cl.skm.pulleyai.lab`

## Estado acumulado

- Alpha48 Lab compila para `arm64-v8a` con STEP/OCCT y se instala junto a alpha44.
- Evidencia dimensional, identificación, safety gate, pose graph y reconstrucción conservan sus gates automáticos.
- La ventana BA permanece limitada a 8 cámaras, 120 puntos y 1500 observaciones con cámara global 0 fija.
- Las etapas base, rotacional y focal permanecen acotadas y con fallback fail-closed.
- RANSAC fundamental, SVD esencial, cheirality y triangulación DLT poseen checkpoints internos cooperativos.
- La publicación runtime usa journal SQLite recuperable y promoción protegida de puntero.
- Antes de exportar se verifican rutas, tamaños, archivos listados y SHA-256.
- Cada generación READY firma evidencia mediante Ed25519 local verificable.
- La captura exige bloquear una sola polea, completar dos anillos y verificar solape.
- La variante Lab importa paquetes `skm-polea-capture/*` mediante el selector de Android.
- Cada fotografía importada se valida contra su SHA-256 antes de cada pasada.
- Alpha47 mantiene comparación alpha46, pirámide `1.0 / 0.80 / 0.64`, orientación y descriptor binario rotado de 256 bits.
- El matcher conserva evidencia estricta, expansión guiada afín, cobertura ROI y matriz fundamental.
- El grafo primario acepta únicamente pares `STRONG/USABLE`; `BRIDGE` permanece en un grafo diagnóstico separado.
- Alpha48 ejecuta una segunda pasada acotada para formar tracks desde inliers fundamentales primarios.
- Cada observación de track se identifica por fotograma e índice de característica.
- Una unión se rechaza si produciría dos observaciones del mismo fotograma.
- Conflictos de coordenadas o banda para una misma observación se bloquean.
- Solo se conservan tracks de tres o más vistas.
- Cada track persiste sus pares de procedencia, longitud, observaciones y condición cross-ring.
- Se exporta histograma de longitudes, longitud mediana y máxima.
- El paquete de tracks incluye trazabilidad y diagnóstico alpha47, pero no incluye fotografías fuente.
- Las aristas `BRIDGE` se excluyen del ensamblaje de tracks y de toda geometría posterior.
- La variante Lab usa una clave pública estable de prueba; no representa identidad corporativa.
- No existe todavía campaña física controlada ni calificación industrial o metrológica.

## Avance global estimado

- **Avance integral: 99 %.**
- **Madurez alpha: 99 %.**
- **Preparación industrial/metrológica: 28 %.**

La trazabilidad multivista mejora, pero la preparación industrial no aumenta porque los tracks aún no tienen pose global, triangulación persistida, escala física validada ni nube métrica.

## Iteración actual o última cerrada

### ITER-025 — Tracks multivista persistentes desde ZIP importado

Registro: `history/ITER-025_2026-07-23_imported-multiview-track-persistence.md`

- tracks construidos solo desde inliers fundamentales `STRONG/USABLE`;
- pares `BRIDGE/WEAK` excluidos;
- unión determinista y procedencia por par;
- colisiones de fotograma rechazadas;
- conflictos de coordenadas o banda rechazados;
- longitud mínima tres vistas;
- tracks cross-ring e histograma exportados;
- fallback alpha47 conservado;
- producto run `#892`: `success`;
- historial run `#352`: `success`;
- 43 gates aprobados;
- Gradle, firma Lab y cierre OCCT aprobados;
- APK inicial publicada desde `31420f93c383f3c8ece2b6237a60b98281703802`;
- APK SHA-256 inicial `afa3f498dae2969f68a0e53789abfae83587ba9836aff5b53e6b0aefaa689a5b`;
- uso industrial continúa bloqueado.

## Bloqueos activos

1. `POSE-001`: los tracks importados aún no poseen pose global persistida;
2. `TRIANG-001`: falta triangular tracks con cheirality y reproyección multivista;
3. `SCALE-001`: no existe escala física validada para el ZIP reprocesado;
4. `IMPORT-001`: el ZIP importado no se transforma todavía en sesión SQLite completa;
5. `IMPORT-002`: el diagnóstico no alimenta automáticamente BA y nube global del pipeline principal;
6. `BRIDGE-002`: las aristas `BRIDGE` demuestran continuidad probable, pero están prohibidas para geometría;
7. `TRACK-002`: los índices de características son estables solo dentro de cada pasada;
8. `PERF-001`: alpha48 repite decodificación y matching después de alpha47;
9. `ROI-001`: la cobertura relativa no es una segmentación semántica exacta de la polea;
10. `FIELD-001`: falta ejecutar físicamente `demo_3` dentro de alpha48 Lab;
11. `TARGET-001`: el bloqueo de captura sigue siendo heurístico y centrado;
12. `SIGN-003`: la clave Lab es pública y no representa identidad corporativa;
13. `RUNTIME-004`: Bitmap decode y algunas llamadas nativas no admiten checkpoint interno;
14. `TX-002`: journal recuperable, pero no transacción ACID única SQLite/filesystem;
15. `INTR-002`: observabilidad focal validada sintéticamente, no en cámaras físicas;
16. `BA-003`: no existe BA global ni Schur complement;
17. `STAT-001`: sensibilidad e intervalos no representan incertidumbre física o dimensional;
18. `DEVICE-001`: campaña Samsung A15 no ejecutada;
19. `DEVICE-002`: campaña Honor X5C no ejecutada;
20. `VALID-001`: metrología trazable ausente;
21. `DATA-007`: hashes reales de todos los PDF pendientes;
22. `ABI-001`: OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-026 — Pose inicial y triangulación acotada desde tracks importados

Resolver poses solo cuando el grafo primario esté conectado, seleccionar una arista semilla fuerte, propagar poses esenciales calibradas, triangular tracks con cheirality y reproyección acotadas, excluir completamente `BRIDGE` y mantener bloqueada cualquier escala métrica hasta validar la referencia física.

## Criterios de entrada

- conservar los 43 gates acumulados;
- mantener importación ZIP y SHA-256 fail-closed;
- usar exclusivamente tracks formados desde inliers primarios;
- mantener una observación como máximo por fotograma y track;
- conservar procedencia por par y banda;
- mantener `bridgeEvidenceUsedForGeometry=false`;
- conservar paquete Lab separado y firma de prueba estable;
- no declarar escala, nube o CAD validado;
- mantener PR en borrador.

## Criterios de salida

- arista semilla seleccionada por soporte geométrico y no por orden incidental;
- poses relativas calibradas propagadas únicamente por el grafo primario;
- tracks triangulados con cheirality positiva y error de reproyección acotado;
- tracks no observables o degenerados rechazados;
- escala métrica bloqueada sin referencia física válida;
- nueva alpha Lab compilada;
- CI producto e historial exitosos;
- ITER-026 archivada.
