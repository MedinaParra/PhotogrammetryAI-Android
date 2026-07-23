# ITER-024 — Reprocesamiento ZIP multiescala y grafo diagnóstico separado

## Identificación

- Fecha: `2026-07-23`.
- Versión: `0.18.0-alpha47`.
- Variante: `SKM Polea AI Lab`.
- Paquete Android: `cl.skm.pulleyai.lab`.
- Rama: `agent/photogrammetry-validation-alpha20`.
- PR: `#8`, abierto, borrador y sin fusionar.
- Origen: evidencia real `demo_3_00b90b75.zip`, con 36 fotografías aceptadas pero solo 2 pares utilizables en el matcher anterior.

## Objetivo

Corregir el sesgo del descriptor fijo Harris/BRIEF frente a cambios de escala y orientación, conservar evidencia geométrica más profunda por par y separar estrictamente las aristas utilizables para reconstrucción de las aristas que solo demuestran continuidad probable.

## Alcance ejecutado

- pirámide de imagen `1.0 / 0.80 / 0.64`;
- detector FAST-like distribuido por celdas;
- orientación por centroide de intensidad;
- descriptor binario rotado de 256 bits;
- matching simétrico con razón segundo-mejor;
- expansión guiada por modelo afín RANSAC acotado;
- cobertura calculada respecto de celdas visualmente disponibles;
- matriz fundamental RANSAC como segundo gate geométrico;
- registro por par de observaciones estrictas, observaciones guiadas, ajuste afín, inliers fundamentales, RMS, cobertura, escala y orientación;
- grafo primario con aristas `STRONG/USABLE`;
- grafo diagnóstico separado que puede incorporar aristas `BRIDGE` de 9–13 inliers;
- comparación alpha46/alpha47 dentro del reprocesamiento;
- exportación JSON y ZIP sin incluir fotografías fuente;
- integración Android, gate v67, workflow y APK alpha47.

## Decisiones fail-closed

- Las aristas `BRIDGE` no ingresan al grafo primario.
- Las aristas `BRIDGE` no habilitan BA, nube métrica, CAD ni liberación industrial.
- El reprocesamiento continúa siendo diagnóstico y no publica geometría.
- La importación verifica esquema, rutas, tamaño y SHA-256 de cada JPEG aceptado.
- Las fotografías de `demo_3` no se incorporan al repositorio ni al APK.

## Evidencia y validación

- Producto run `#875`: **PASS**.
- Historial run `#339`: **PASS**.
- 42 gates acumulados: **APROBADOS**.
- Gate v67 de rotación, escala, propagación de evidencia y separación de puentes: **APROBADO**.
- Resultado sintético v67: `601/823` características, `198` matches y cobertura `0.7826`.
- Gradle: **APROBADO**.
- AAR STEP verificado por SHA-256.
- Cierre OCCT y 25 bibliotecas nativas: **APROBADOS**.
- Firma estable Lab: **APROBADA**.
- Artefacto inicial publicado desde `5f105ec4487662fb09213a720dc71277c74142f1`.
- APK SHA-256 inicial: `7c65afe70192014aee7752a11ef12aa99dafed523abc164f8775541aeb344789`.

## Resultados

- Alpha47 compara el resultado alpha46 con un pipeline multiescala independiente.
- Cada par conserva evidencia afín y fundamental suficiente para ajustar umbrales posteriores.
- El operador puede distinguir conectividad primaria de conectividad apoyada solo en puentes diagnósticos.
- La variante Lab continúa instalándose junto a alpha44.

## Fallos, riesgos y deuda

- La actividad Android fue compilada y validada por gates; `demo_3` aún no se ha ejecutado físicamente dentro de alpha47 en el teléfono.
- El descriptor es ORB-inspired en Java puro, no OpenCV ORB certificado ni SIFT.
- El modelo afín sirve para expansión local; no reemplaza pose esencial calibrada.
- El ZIP importado aún no produce tracks multivista persistentes ni una sesión SQLite completa.
- No existe nube métrica publicada desde el ZIP.
- Campañas Samsung A15/Honor X5C, metrología trazable, identidad corporativa y `armeabi-v7a` siguen pendientes.
- Uso industrial continúa bloqueado.

## Estado del roadmap

- ITER-024: cerrada funcionalmente para matching multiescala y diagnóstico de conectividad.
- Avance integral estimado: `99 %`.
- Madurez alpha: `99 %`.
- Preparación industrial/metrológica: `28 %`.

## Siguiente iteración obligatoria

### ITER-025 — Tracks multivista persistentes desde ZIP importado

Construir tracks únicamente desde inliers de aristas primarias, impedir colisiones de dos observaciones del mismo fotograma, conservar procedencia por par, exportar histograma y tracks cross-ring, y mantener excluidas todas las aristas `BRIDGE` de cualquier reconstrucción geométrica.
