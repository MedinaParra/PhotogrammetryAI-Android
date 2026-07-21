# Validación técnica 0.18.0-alpha24

## Alcance

Alpha24 consolida ITER-003 a ITER-006 sobre `agent/photogrammetry-validation-alpha20` sin fusionar el PR #8.

## Funciones incorporadas

- evidencia dimensional por código, OT, plano, revisión, dimensión y superficie;
- puerta de radio `error_radio_mm <= 30`;
- identificación multivariable y auditoría append-only;
- safety gate fotogramétrico fail-closed;
- bundle adjustment local acotado de puntos y traslaciones;
- cámara 0 fija, pérdida Huber y priors de traslación;
- perfiles Brown-Conrady versionados;
- almacenamiento SQLite de perfiles con selección automática solo para estado `VALIDATED` y RMS <= 1 px;
- STEP/OCCT para `arm64-v8a` conservado.

## Evidencia automática

GitHub Actions run #460 terminó en `success`:

- 23 gates Java previos a la compilación;
- build Android alpha24 exitoso;
- AAR STEP verificado por SHA-256;
- 25 bibliotecas nativas en la APK;
- cierre OCCT auditado;
- artefacto `SKM-Polea-AI-CAD-STEP-v0.18.0-alpha24` publicado.

## Límites

- el BA es local, no global;
- no optimiza rotaciones ni intrínsecos;
- el safety gate y BA no están integrados todavía en el pipeline runtime;
- los perfiles de cámara no provienen de calibraciones físicas;
- no existen pruebas en Samsung A15 u Honor X5C;
- no existe validación metrológica ni autorización de liberación dimensional;
- ABI disponible: `arm64-v8a`.

## Próximo paso

ITER-007 debe integrar el safety gate y el BA local al flujo real, persistir métricas antes/después y aplicar fallback seguro ante divergencia o recursos insuficientes.
