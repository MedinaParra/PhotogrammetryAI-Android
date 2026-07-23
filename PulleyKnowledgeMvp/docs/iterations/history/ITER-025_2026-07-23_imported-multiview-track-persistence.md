# ITER-025 — Tracks multivista persistentes desde ZIP importado

## Identificación

- Fecha: `2026-07-23`.
- Versión: `0.18.0-alpha48`.
- Variante: `SKM Polea AI Lab`.
- Paquete Android: `cl.skm.pulleyai.lab`.
- Rama: `agent/photogrammetry-validation-alpha20`.
- PR: `#8`, abierto, borrador y sin fusionar.
- Origen: continuidad recuperable observada en `demo_3_00b90b75.zip` y necesidad de convertir inliers por par en evidencia multivista auditable.

## Objetivo

Formar y persistir tracks de tres o más vistas exclusivamente a partir de inliers fundamentales pertenecientes a pares primarios `STRONG/USABLE`, evitando que una unión introduzca dos observaciones del mismo fotograma y conservando la procedencia exacta de cada track.

## Alcance ejecutado

- nuevo `ImportedTrackAssemblerCore` Java puro;
- nodos identificados por `fotograma + índice de característica`;
- unión determinista por procedencia de par;
- detección y rechazo de colisiones de fotograma antes de fusionar componentes;
- detección de conflictos de coordenadas o banda para una misma observación;
- exclusión explícita de pares `BRIDGE` y `WEAK`;
- longitud mínima de track igual a tres vistas;
- procedencia por par persistida en cada track;
- tracks cross-ring identificados;
- histograma de longitudes, longitud mediana y máxima;
- nuevo `ImportedTrackZipAnalyzer` con segunda pasada acotada sobre el ZIP;
- verificación repetida de esquema, rutas, tamaño y SHA-256;
- exportación `imported_multiview_tracks.json`;
- paquete ZIP con tracks, diagnóstico alpha47, manifiesto fuente y procedencia, sin fotografías;
- integración Android, gate v68, workflow y APK alpha48.

## Cambios y decisiones

- Solo se usan inliers fundamentales de pares clasificados `STRONG/USABLE`.
- Las aristas `BRIDGE` permanecen excluidas del ensamblaje de tracks.
- Una unión se bloquea cuando los componentes ya contienen el mismo fotograma.
- Una observación con la misma clave pero coordenadas incompatibles se rechaza.
- Los tracks se persisten como evidencia diagnóstica portable, no como geometría métrica.
- Alpha48 ejecuta una segunda pasada acotada para mantener independencia y trazabilidad respecto de alpha47.
- El paquete de tracks no incorpora las fotografías fuente.

## Evidencia y validación

- Producto run `#892`: **PASS**.
- Historial run `#352`: **PASS**.
- 43 gates acumulados: **APROBADOS**.
- Gate v68: **APROBADO**.
- Prueba v68: dos tracks de cuatro vistas, ocho observaciones, dos tracks cross-ring y una colisión correctamente rechazada.
- Procedencia `01-02 / 02-03 / 03-04`: conservada.
- Evidencia `BRIDGE 04-05`: excluida del JSON geométrico.
- Gradle: **APROBADO**.
- AAR STEP verificado por SHA-256.
- Cierre OCCT y 25 bibliotecas nativas: **APROBADOS**.
- Firma estable Lab: **APROBADA**.
- Artefacto inicial publicado desde `31420f93c383f3c8ece2b6237a60b98281703802`.
- APK SHA-256 inicial: `afa3f498dae2969f68a0e53789abfae83587ba9836aff5b53e6b0aefaa689a5b`.
- Tamaño APK inicial: `50.823.509 bytes`.

## Resultados

- Alpha48 persiste tracks multivista con identidad de observación y procedencia por par.
- Ningún track aceptado puede contener dos observaciones del mismo fotograma.
- Los tracks cross-ring y el histograma quedan disponibles para la siguiente etapa geométrica.
- Si la pasada de tracks falla, alpha47 conserva su diagnóstico exportable como fallback.
- La variante Lab continúa instalándose junto a alpha44.

## Fallos, riesgos y deuda

- `demo_3` todavía no se ha ejecutado físicamente dentro de alpha48 en el teléfono.
- La segunda pasada aumenta tiempo, decodificaciones y consumo de batería respecto de alpha47.
- Los índices de características solo son estables dentro de la pasada actual; no se ha creado una base SQLite importada.
- Los tracks no incluyen aún poses globales, triangulación persistida ni reproyección multivista.
- No existe nube métrica, cilindro medido ni CAD publicado desde estos tracks.
- No se ha comprobado repetibilidad entre dos ejecuciones físicas del mismo ZIP.
- Campañas Samsung A15/Honor X5C, metrología trazable, identidad corporativa y `armeabi-v7a` siguen pendientes.
- Uso industrial continúa bloqueado.

## Estado del roadmap

- ITER-025: cerrada funcionalmente para ensamblaje y persistencia portable de tracks importados.
- Avance integral estimado: `99 %`.
- Madurez alpha: `99 %`.
- Preparación industrial/metrológica: `28 %`.

## Siguiente iteración obligatoria

### ITER-026 — Pose inicial y triangulación acotada desde tracks importados

Resolver poses solo cuando el grafo primario esté conectado, seleccionar una arista semilla fuerte, propagar poses esenciales calibradas, triangular tracks con cheirality y reproyección acotadas, excluir completamente `BRIDGE` y mantener bloqueada cualquier escala métrica hasta validar la referencia física.

## Criterios de entrada y salida

### Entrada

- conservar los 43 gates acumulados;
- usar únicamente tracks de inliers fundamentales primarios;
- mantener bloqueo de colisiones de fotograma;
- conservar procedencia de pares y bandas;
- mantener `bridgeEvidenceUsedForGeometry=false`;
- no declarar pose, nube o escala física desde los tracks actuales.

### Salida

- tracks persistidos con longitud mínima tres;
- ausencia de fotogramas duplicados por track;
- procedencia y tracks cross-ring exportados;
- fallback alpha47 conservado si falla alpha48;
- APK alpha48 Lab compilada con STEP/OCCT y firma verificada;
- siguiente iteración geométrica declarada;
- uso industrial bloqueado.
