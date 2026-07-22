# ITER-023 — Reprocesamiento portable de ZIP y diagnóstico por par

## Identificación

- Fecha: `2026-07-22`.
- Versión: `0.18.0-alpha46`.
- Variante: `SKM Polea AI Lab`.
- Paquete Android: `cl.skm.pulleyai.lab`.
- Rama: `agent/photogrammetry-validation-alpha20`.
- PR: `#8`, borrador y sin fusionar.
- Origen: análisis real del paquete `demo_3_00b90b75.zip`, cuya captura íntegra produjo `2/18`, grafo desconectado y repetición 100 % en alpha44, mientras una comprobación independiente encontró continuidad visual recuperable.

## Objetivo

Permitir que una captura exportada sea importada y reprocesada localmente sin repetir las fotografías; conservar evidencia detallada de cada par candidato; separar ambigüedad repetitiva, baja cobertura y debilidad geométrica; y generar un paquete diagnóstico que permita calibrar el algoritmo con nuevas campañas reales.

## Alcance ejecutado

- aplicación Lab instalable en paralelo mediante `applicationId cl.skm.pulleyai.lab`;
- selector Android `ACTION_OPEN_DOCUMENT` para importar ZIP de captura;
- validación de esquema `skm-polea-capture/*`;
- límites de tamaño y protección frente a rutas ZIP no seguras;
- verificación SHA-256 individual de fotografías aceptadas;
- reprocesamiento a 960 px de lado mayor y hasta 700 características por fotograma;
- comparación del matcher histórico alpha44 contra un matcher diagnóstico adaptativo;
- cobertura relativa a las celdas visualmente disponibles, no únicamente a toda la imagen;
- repetición declarada solo mediante evidencia combinada de razón, reciprocidad y cobertura;
- estimación fundamental RANSAC por par;
- registro de matches, razón segundo-mejor, reciprocidad, cobertura ROI, inliers, relación de inliers, RMS, estado y causa de rechazo;
- análisis de componentes del grafo diagnóstico;
- exportación de `zip_reprocess_diagnostics.json` y paquete ZIP de diagnóstico;
- firma APK estable de laboratorio para actualizaciones posteriores de la variante Lab;
- test Java puro v66 y workflow completo alpha46.

## Cambios y decisiones

- Alpha46 Lab no reemplaza ni desinstala alpha44 porque usa un paquete Android distinto.
- La clave de firma estable es pública y solo sirve para continuidad de builds de laboratorio; no representa identidad corporativa, secreto de producción ni atestación remota.
- Las fotografías del usuario no se incorporan al repositorio ni al APK.
- El ZIP se procesa localmente en el teléfono y las imágenes temporales permanecen en caché privada.
- La variante Lab no publica una geometría métrica importada como READY.
- El resultado es diagnóstico: informa si el grafo puede conectarse con el matcher experimental y explica cada rechazo.
- El paquete de diagnóstico no vuelve a incluir las fotografías originales para evitar duplicación innecesaria; conserva procedencia, hash del ZIP y reportes relevantes.
- La métrica de repetición deja de usar una condición OR única que convertía baja cobertura en ambigüedad automática.

## Evidencia y validación

- Producto run `#855`: **PASS**.
- Historial previo run `#326`: **PASS**.
- 41 gates acumulados: **APROBADOS**.
- Gate v66 de matching adaptativo, clasificación y conectividad: **APROBADO**.
- Importador Android y actividad de reprocesamiento: **COMPILADOS**.
- Gradle: **APROBADO**.
- AAR STEP verificado por SHA-256.
- Cierre OCCT y 25 bibliotecas nativas: **APROBADOS**.
- Firma de laboratorio estable: **VERIFICADA**.
- Certificado SHA-256: `F6:DA:96:43:77:F0:02:E1:92:5C:B0:9F:4D:42:ED:E4:4B:76:E7:7A:53:DC:E5:57:70:6A:5A:E9:4D:C8:3B:93`.
- Artefacto `SKM-Polea-AI-Lab-CAD-STEP-v0.18.0-alpha46` publicado desde `7e9f1987bd770d2f18c091d9858fde378f7ace93`.
- APK SHA-256: `24f870ac29056dba56244a86d82377cfec275f48c39b67ac19fb36923beafa70`.

## Resultados

- El operador puede seleccionar el ZIP `demo_3_00b90b75.zip` desde Android y reprocesarlo sin repetir la captura.
- Cada par candidato genera evidencia explícita en lugar de quedar reducido a `WEAK` o repetición 100 %.
- El informe compara directamente el matcher histórico y el matcher adaptativo.
- El grafo diagnóstico informa número de componentes, aristas aceptadas y tamaño del componente principal.
- Los reportes JSON y ZIP pueden compartirse para ajustar futuras versiones.
- Alpha46 Lab se instala junto a alpha44 y no requiere eliminar sus datos.

## Fallos, riesgos y deuda

- El matcher adaptativo continúa basado en Harris/BRIEF y matriz fundamental; aún no integra ORB, AKAZE, SIFT ni flujo óptico nativo.
- La cobertura ROI es una normalización por celdas con características, no una segmentación semántica exacta de la polea.
- Reprocesar el ZIP no crea todavía una sesión completa dentro de SQLite ni alimenta automáticamente el pipeline BA principal.
- El grafo diagnóstico no equivale a una reconstrucción métrica, nube global o CAD validado.
- La actividad se probó por compilación y gates sintéticos; falta ejecutar `demo_3` físicamente en el teléfono.
- La clave estable está publicada en el repositorio y no debe emplearse para una distribución de producción.
- Samsung A15 y Honor X5C no poseen todavía tres campañas controladas.
- Metrología trazable, `armeabi-v7a` e identidad corporativa continúan pendientes.

## Estado del roadmap

- ITER-023: cerrada funcionalmente.
- Avance integral estimado: `99 %`.
- Madurez alpha: `99 %`.
- Preparación industrial/metrológica: `28 %`.
- Uso industrial: bloqueado.

## Siguiente iteración obligatoria

### ITER-024 — Reprocesamiento geométrico multiescala sobre evidencia real

Consumir el diagnóstico de `demo_3`, incorporar un descriptor orientado o multiescala con fallback, construir tracks persistentes desde pares vecinos y cross-ring, conservar `pair_diagnostics.json`, y permitir que una importación ZIP alimente una reconstrucción separada sin declarar resultado métrico mientras no pase todos los gates geométricos.

## Criterios de entrada y salida

### Entrada

- conservar los 41 gates acumulados;
- mantener importación ZIP segura y SHA-256 fail-closed;
- mantener comparación entre matcher histórico y experimental;
- mantener paquete Lab separado y firma de prueba estable;
- conservar safety gate, journal y evidencia automática;
- no declarar reconstrucción métrica desde el diagnóstico;
- mantener PR en borrador.

### Salida

- descriptor orientado o multiescala integrado con fallback;
- reproducción controlada del ZIP `demo_3` documentada;
- tracks multivista persistentes construidos desde importación;
- grafo importado conectado o causas residuales identificadas por par;
- publicación geométrica separada y fail-closed;
- nueva alpha Lab compilada;
- CI producto e historial exitosos;
- ITER-024 archivada.
