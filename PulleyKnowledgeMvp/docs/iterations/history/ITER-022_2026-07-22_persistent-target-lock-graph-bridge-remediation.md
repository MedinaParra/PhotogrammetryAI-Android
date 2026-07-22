# ITER-022 — Bloqueo persistente de objetivo y remediación por capturas puente

**Fecha:** 2026-07-22  
**Versión:** `0.18.0-alpha44`  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR:** `#8` — borrador, sin fusionar

## Problema de terreno

Una segunda prueba real con alpha40 completó los anillos EJE `12/12` y ALTA `12/12`, pero produjo únicamente `6/17` pares utilizables, grafo de poses desconectado, repetición visual de 100 %, cero puntos globales y una fotografía completamente obstruida por un dedo. La escena contenía varios tambores y componentes circulares competidores, además de contraluz severo.

Alpha42 corrigió la admisión de dedo, diversidad y flujo de dos anillos, pero no mantenía una identidad visual persistente de la polea ni proponía una captura específica para conectar componentes del grafo.

## Objetivo

Impedir que fotografías de otros tambores, fondos ambiguos o contraluz severo ingresen silenciosamente a una sesión; mantener una referencia visual persistente de la polea centrada; y convertir un diagnóstico `DISCONNECTED` o `NO_CROSS_RING_LINKS` en una instrucción concreta de captura puente.

## Implementación

- nuevo `PulleyTargetLockCore` Java puro;
- firma visual centrada con histograma de luminancia, distribución radial, distribución por cuadrantes y densidad de detalle;
- métricas de dominancia del objetivo, ambigüedad de escena, contraluz y continuidad;
- bloqueo explícito de la polea antes de habilitar el botón CAPTURAR;
- referencia JPEG y firma persistidas atómicamente dentro de la sesión;
- rechazo fail-closed cuando cambia el objetivo, la polea deja de dominar, aparece contraluz severo o compiten otros objetos;
- métricas visibles después de cada captura;
- nuevo `BridgeCapturePlanCore` Java puro;
- análisis de componentes y pares débiles para elegir banda y sector puente;
- guía de movimiento de 8–15°, aproximadamente 70 % de solape y exclusión de otros cilindros;
- verificación de solape repetible después de aceptar el puente;
- ninguna afirmación de detector semántico o red neuronal.

## Criterios de entrada

- conservar los 38 gates de alpha42;
- mantener admisión guiada de dos anillos;
- mantener máximo de dos vistas normales por celda;
- mantener journal, integridad SHA-256 y firma local;
- mantener safety gate fail-closed;
- no reclasificar silenciosamente fotografías históricas;
- mantener PR en borrador y sin fusionar.

## Criterios de salida

- bloqueo de objetivo obligatorio antes de capturar;
- firma visual serializable y persistente;
- cambio brusco de objetivo bloqueado;
- contraluz severo bloqueado;
- escena con objetos competidores bloqueada conservadoramente;
- plan de captura puente para componentes desconectados;
- recomendación de enlace entre anillos cuando faltan cruces;
- gate Java puro incorporado;
- Gradle aprobado;
- cierre OCCT y 25 bibliotecas nativas aprobados;
- APK alpha44 publicada;
- uso industrial continúa bloqueado.

## Validación

- producto run `#817`: `success`;
- 39 gates Java aprobados;
- prueba de continuidad y serialización de firma aprobada;
- cambio de escena y contraluz sintético bloqueados;
- selección de puente entre componentes aprobada;
- selección de puente entre anillos aprobada;
- Gradle aprobado;
- AAR STEP validado por SHA-256;
- cierre OCCT verificado;
- artefacto `SKM-Polea-AI-CAD-STEP-v0.18.0-alpha44` publicado desde `1d86d576df4cbdf2a8c80ad81eeaacd2470e534c`.

## Límites honestos

- el bloqueo es heurístico y centrado; no es detección semántica de poleas;
- dos poleas visualmente muy similares todavía pueden superar la firma si el operador cambia de objetivo manteniendo un encuadre casi idéntico;
- los umbrales necesitan ajuste con campañas reales y fondos industriales diversos;
- una sesión alpha40/alpha42 existente no obtiene clasificación retroactiva;
- el plan puente selecciona un sector probable, pero no garantiza que una sola fotografía conecte el grafo;
- Samsung A15 y Honor X5C no poseen todavía tres campañas controladas;
- metrología trazable no ejecutada;
- uso industrial y liberación metrológica permanecen bloqueados.

## Resultado

ITER-022 queda cerrada funcionalmente en alpha44. La siguiente iteración vuelve a la calificación device-alpha con continuidad de clave y exige evidencia automática real por dispositivo.
