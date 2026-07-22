# Estado actual de iteraciones

**Actualizado:** 2026-07-22  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha42`

## Estado acumulado

- Alpha42 compila para `arm64-v8a` con STEP/OCCT.
- Evidencia dimensional, identificación, safety gate, pose graph y reconstrucción mantienen gates automáticos.
- La ventana BA permanece limitada a 8 cámaras, 120 puntos y 1500 observaciones con cámara global 0 fija.
- Las etapas base, rotacional y focal permanecen acotadas y con fallback fail-closed.
- RANSAC fundamental, SVD esencial, cheirality y triangulación DLT poseen checkpoints internos cooperativos.
- La publicación runtime usa journal SQLite recuperable y promoción protegida de puntero.
- Antes de exportar se verifican rutas, tamaños, archivos listados y SHA-256.
- Cada generación READY firma `campaign_evidence_manifest.json` mediante Ed25519 local verificable.
- `runtime_execution_evidence.json` se genera automáticamente con `manualEntry=false`.
- La captura aplica admisión fail-closed por nitidez, exposición, movimiento, obstrucción, detalle central y diversidad angular.
- Cada combinación `anillo + sector` admite como máximo dos vistas y exige 6° entre ellas.
- La guía conduce EJE → ALTA → 30 vistas distintas → verificación de solape.
- Solape y finalización permanecen bloqueados mientras los dos anillos estén incompletos.
- No existe todavía campaña física controlada ni calificación industrial o metrológica.

## Avance global estimado

- **Avance integral: 98 %.**
- **Madurez alpha: 99 %.**
- **Preparación industrial/metrológica: 27 %.**

El incremento corresponde a una remediación derivada de evidencia de uso real. La preparación industrial aumenta de forma limitada porque la prueba no fue una campaña controlada con patrón, repetibilidad o instrumentos trazables.

## Iteración actual o última cerrada

### ITER-021 — Captura guiada de dos anillos y remediación de prueba de terreno

Registro: `history/ITER-021_2026-07-22_guided-two-ring-capture-field-remediation.md`

- máximo de dos vistas por anillo y sector;
- segunda vista exige al menos 6° de separación;
- borde uniforme dominante y detalle central insuficiente bloquean admisión;
- fotografías rechazadas permanecen auditables sin aumentar cobertura;
- cambio inicial automático a `ALTURA: ALTA` después del anillo EJE;
- `Siguiente: completo` reemplazado por una instrucción de flujo completa;
- solape deshabilitado hasta completar ambos anillos;
- una sesión incompleta no puede marcarse `CAPTURED`;
- salida sin finalizar conserva la sesión para continuar;
- producto run `#792`: `success`;
- 38 gates Java;
- Gradle y cierre OCCT aprobados;
- APK alpha42 publicada;
- uso industrial continúa bloqueado.

## Bloqueos activos

1. `CAPTURE-001`: umbrales de obstrucción y detalle central requieren ajuste con más capturas reales;
2. `CAPTURE-002`: no existe detector semántico de polea; el gate solo evalúa calidad y encuadre;
3. `SIGN-002`: la clave local no representa identidad corporativa ni atestación remota;
4. `KEY-001`: reinstalación o borrado de datos puede cambiar la identidad local de clave;
5. `RUNTIME-004`: `Bitmap` decode y algunas llamadas nativas no admiten checkpoint interno;
6. `TX-002`: journal recuperable, pero no transacción ACID única SQLite/filesystem;
7. `INTR-002`: observabilidad focal validada sintéticamente, no en cámaras físicas;
8. `BA-003`: no existe BA global ni Schur complement;
9. `STAT-001`: sensibilidad e intervalos no representan incertidumbre física o dimensional;
10. `DEVICE-001`: campaña Samsung A15 no ejecutada;
11. `DEVICE-002`: campaña Honor X5C no ejecutada;
12. `VALID-001`: metrología trazable ausente;
13. `DATA-007`: hashes reales de todos los PDF pendientes;
14. `ABI-001`: OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-022 — Calificación device-alpha con continuidad de clave

Consumir únicamente generaciones automáticas con integridad y firma válidas; contar ejecuciones por modelo objetivo; exigir tres ejecuciones calificantes para Samsung A15 y Honor X5C; detectar cambios de clave y exigir rotación explícita; excluir registros manuales del conteo; mantener READY separado de calificación metrológica.

## Criterios de entrada

- conservar los 38 gates acumulados;
- mantener admisión guiada y finalización fail-closed;
- mantener journal, integridad SHA-256 y firma Ed25519 fail-closed;
- mantener `corporateIdentity=false`;
- conservar límites 8/120/1500 y cámara 0 fija;
- no declarar campañas físicas no ejecutadas;
- mantener PR en borrador.

## Criterios de salida

- ingesta de generaciones firmadas implementada;
- continuidad de clave verificada;
- rotación de clave explícita y auditable;
- tres ejecuciones automáticas por dispositivo exigidas;
- evidencia manual excluida del conteo;
- nueva alpha compilada;
- CI producto e historial exitosos;
- ITER-022 archivada.
