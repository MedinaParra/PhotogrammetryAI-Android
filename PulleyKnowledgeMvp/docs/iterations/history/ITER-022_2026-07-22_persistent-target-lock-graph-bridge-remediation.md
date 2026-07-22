# ITER-022 — Bloqueo persistente de objetivo y remediación por capturas puente

## Identificación

- Fecha: `2026-07-22`.
- Versión: `0.18.0-alpha44`.
- Rama: `agent/photogrammetry-validation-alpha20`.
- PR: `#8`, borrador y sin fusionar.
- Origen: dos pruebas reales de terreno con grafos desconectados, fondos industriales ambiguos y una toma obstruida.

## Objetivo

Impedir que fotografías de otros tambores, fondos ambiguos o contraluz severo ingresen silenciosamente a una sesión; mantener una referencia visual persistente de la polea centrada; y convertir un diagnóstico `DISCONNECTED` o `NO_CROSS_RING_LINKS` en una instrucción concreta de captura puente.

## Alcance ejecutado

- nuevo `PulleyTargetLockCore` Java puro;
- firma visual centrada con histograma de luminancia, distribución radial, cuadrantes y densidad de detalle;
- métricas de dominancia, ambigüedad, contraluz y continuidad;
- bloqueo explícito de la polea antes de habilitar CAPTURAR;
- referencia JPEG y firma persistidas atómicamente dentro de la sesión;
- nuevo `BridgeCapturePlanCore` Java puro;
- análisis de componentes y pares débiles para elegir banda y sector puente;
- guía de movimiento de 8–15°, aproximadamente 70 % de solape y exclusión de otros cilindros;
- integración Android, test Java puro, workflow y empaquetado alpha44.

## Cambios y decisiones

- El bloqueo de objetivo es obligatorio y fail-closed.
- No se declara detector semántico ni red neuronal: el método es una firma visual centrada y conservadora.
- El objetivo debe ocupar aproximadamente 60–80 % del encuadre y dominar visualmente el centro.
- Un cambio brusco de firma, dominancia insuficiente, ambigüedad alta o contraluz severo rechaza la fotografía.
- La referencia se guarda fuera de SQLite para evitar una migración de base de datos innecesaria; se publica mediante archivos temporales y `fsync`.
- Una verificación de solape fallida genera un sector puente concreto, pero no garantiza que una sola fotografía cierre el grafo.
- Fotografías históricas de alpha40/alpha42 no se reclasifican retroactivamente.

## Evidencia y validación

- Producto run `#817`: **PASS**.
- 39 gates Java: **APROBADOS**.
- Continuidad y serialización de firma: **APROBADO**.
- Cambio de escena y contraluz sintético: bloqueados correctamente.
- Selección de puente entre componentes: **APROBADA**.
- Selección de puente entre anillos: **APROBADA**.
- Gradle: **APROBADO**.
- AAR STEP verificado por SHA-256.
- Cierre OCCT y 25 bibliotecas nativas: **APROBADOS**.
- Artefacto `SKM-Polea-AI-CAD-STEP-v0.18.0-alpha44` publicado desde `1d86d576df4cbdf2a8c80ad81eeaacd2470e534c`.

## Resultados

- La aplicación exige una referencia de polea antes de contar fotografías.
- Cada fotografía informa continuidad, dominancia, ambigüedad y contraluz.
- Escenas con otros objetos dominantes se bloquean conservadoramente.
- El análisis de solape puede orientar al operador hacia una captura puente específica.
- Alpha44 fue compilada y publicada para `arm64-v8a` con STEP/OCCT.
- El safety gate y la prohibición de uso industrial permanecen activos.

## Fallos, riesgos y deuda

- El bloqueo es heurístico y centrado; no es detección semántica de poleas.
- Dos poleas casi idénticas pueden superar la firma si se mantiene un encuadre similar.
- Los umbrales requieren ajuste con campañas reales y fondos industriales diversos.
- Una sesión alpha40/alpha42 existente no obtiene clasificación retroactiva.
- El sector puente es una recomendación probable, no una garantía geométrica.
- Samsung A15 y Honor X5C no poseen todavía tres campañas controladas.
- Metrología trazable, `armeabi-v7a` e identidad corporativa continúan pendientes.

## Estado del roadmap

- ITER-022: cerrada funcionalmente.
- Avance integral estimado: `99 %`.
- Madurez alpha: `99 %`.
- Preparación industrial/metrológica: `28 %`.
- Uso industrial: bloqueado.

## Siguiente iteración obligatoria

### ITER-023 — Calificación device-alpha con continuidad de clave

Consumir únicamente generaciones automáticas con integridad, objetivo bloqueado y firma válidas; exigir tres ejecuciones calificantes para Samsung A15 y Honor X5C; detectar cambios de clave y excluir evidencia manual del conteo.

## Criterios de entrada y salida

### Entrada

- conservar los 39 gates acumulados;
- mantener bloqueo de objetivo y remediación de puente fail-closed;
- mantener journal, integridad SHA-256 y firma Ed25519 local;
- mantener `corporateIdentity=false`;
- no declarar campañas físicas no ejecutadas;
- mantener PR en borrador.

### Salida

- ingesta de generaciones firmadas implementada;
- objetivo bloqueado e integridad exigidos en el conteo;
- continuidad y rotación de clave auditables;
- tres ejecuciones automáticas por dispositivo exigidas;
- evidencia manual excluida;
- nueva alpha compilada;
- CI producto e historial exitosos;
- ITER-023 archivada.
