# Validación técnica 0.18.0-alpha28

## Alcance

Alpha28 consolida ITER-003 a ITER-010 sobre `agent/photogrammetry-validation-alpha20` sin fusionar el PR #8.

## Capacidades comprobadas automáticamente

- evidencia dimensional versionada por código, OT, plano y revisión;
- puerta de radio de 30 mm;
- identificación multivariable y auditoría;
- safety gate fotogramétrico fail-closed;
- BA local acotado y perfiles de cámara versionados;
- política runtime de aceptación y fallback;
- competencia homografía/fundamental y degradación visual agregada;
- refinamiento acotado del pose graph con gauge fijo;
- puerta profesional de campañas, corpus STEP y metrología;
- manifiesto de evidencia con SHA-256 determinista;
- STEP/OCCT arm64-v8a.

## Evidencia CI

GitHub Actions run #530 terminó en `success`:

- 27 gates Java;
- compilación Android alpha28;
- AAR STEP verificado por SHA-256;
- 25 bibliotecas nativas;
- cierre OCCT aprobado;
- artefacto `SKM-Polea-AI-CAD-STEP-v0.18.0-alpha28` publicado.

El validador del historial run #104 también terminó en `success`.

## Estado real de calificación

- avance integral del roadmap: 70 %;
- madurez funcional alpha estimada: 91 %;
- preparación industrial/metrológica estimada: 15 %;
- campañas Samsung A15 y Honor X5C: no ejecutadas;
- metrología trazable: no ejecutada;
- corpus STEP real de calificación: pendiente;
- calificación industrial: bloqueada.

## Límites

La aplicación continúa siendo una alpha de ingeniería. Las pruebas sintéticas y la compilación no demuestran precisión metrológica, estabilidad térmica real, ausencia de fallos JNI en teléfono ni compatibilidad universal con archivos STEP de taller.
