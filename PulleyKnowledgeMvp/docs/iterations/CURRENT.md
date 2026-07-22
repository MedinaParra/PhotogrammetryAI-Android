# Estado actual de iteraciones

**Actualizado:** 2026-07-22  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha44`

## Estado acumulado

- Alpha44 compila para `arm64-v8a` con STEP/OCCT.
- Evidencia dimensional, identificación, safety gate, pose graph y reconstrucción mantienen gates automáticos.
- La ventana BA permanece limitada a 8 cámaras, 120 puntos y 1500 observaciones con cámara global 0 fija.
- Las etapas base, rotacional y focal permanecen acotadas y con fallback fail-closed.
- RANSAC fundamental, SVD esencial, cheirality y triangulación DLT poseen checkpoints internos cooperativos.
- La publicación runtime usa journal SQLite recuperable y promoción protegida de puntero.
- Antes de exportar se verifican rutas, tamaños, archivos listados y SHA-256.
- Cada generación READY firma `campaign_evidence_manifest.json` mediante Ed25519 local verificable.
- `runtime_execution_evidence.json` se genera automáticamente con `manualEntry=false`.
- La captura exige bloquear explícitamente una sola polea antes de habilitar fotografías.
- La firma visual centrada combina histograma, detalle radial, cuadrantes y densidad de textura.
- Cada captura evalúa continuidad del objetivo, dominancia, ambigüedad, obstrucción, contraluz, nitidez y movimiento.
- Cada combinación `anillo + sector` admite como máximo dos vistas normales y exige 6° entre ellas.
- La guía conduce EJE → ALTA → verificación de solape y, si falla, propone una captura puente concreta.
- Solape y finalización permanecen bloqueados mientras los anillos, el objetivo o el grafo no estén listos.
- No existe todavía campaña física controlada ni calificación industrial o metrológica.

## Avance global estimado

- **Avance integral: 99 %.**
- **Madurez alpha: 99 %.**
- **Preparación industrial/metrológica: 28 %.**

El incremento corresponde a remediación derivada de dos pruebas reales de terreno. La preparación industrial aumenta de forma limitada porque todavía no existe campaña controlada con patrón, repetibilidad o instrumentos trazables.

## Iteración actual o última cerrada

### ITER-022 — Bloqueo persistente de objetivo y remediación por capturas puente

Registro: `history/ITER-022_2026-07-22_persistent-target-lock-graph-bridge-remediation.md`

- bloqueo obligatorio de polea objetivo antes de capturar;
- referencia JPEG y firma visual persistidas atómicamente;
- cambio brusco de objetivo bloqueado;
- dominancia insuficiente y objetos competidores bloqueados conservadoramente;
- contraluz severo bloqueado;
- continuidad y métricas visibles por fotografía;
- plan de puente para grafo desconectado o falta de cruces entre anillos;
- producto run `#817`: `success`;
- 39 gates Java;
- Gradle y cierre OCCT aprobados;
- APK alpha44 publicada;
- uso industrial continúa bloqueado.

## Bloqueos activos

1. `TARGET-001`: el bloqueo es heurístico y centrado; no existe detector semántico de poleas;
2. `TARGET-002`: poleas casi idénticas pueden superar la firma si el operador cambia de objetivo con encuadre similar;
3. `CAPTURE-001`: umbrales de obstrucción, detalle, dominancia, ambigüedad y contraluz requieren ajuste real;
4. `BRIDGE-001`: una recomendación de sector no garantiza que una sola captura conecte el grafo;
5. `SIGN-002`: la clave local no representa identidad corporativa ni atestación remota;
6. `KEY-001`: reinstalación o borrado de datos puede cambiar la identidad local de clave;
7. `RUNTIME-004`: `Bitmap` decode y algunas llamadas nativas no admiten checkpoint interno;
8. `TX-002`: journal recuperable, pero no transacción ACID única SQLite/filesystem;
9. `INTR-002`: observabilidad focal validada sintéticamente, no en cámaras físicas;
10. `BA-003`: no existe BA global ni Schur complement;
11. `STAT-001`: sensibilidad e intervalos no representan incertidumbre física o dimensional;
12. `DEVICE-001`: campaña Samsung A15 no ejecutada;
13. `DEVICE-002`: campaña Honor X5C no ejecutada;
14. `VALID-001`: metrología trazable ausente;
15. `DATA-007`: hashes reales de todos los PDF pendientes;
16. `ABI-001`: OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-023 — Calificación device-alpha con continuidad de clave

Consumir únicamente generaciones automáticas con integridad, objetivo bloqueado y firma válidas; contar ejecuciones por modelo objetivo; exigir tres ejecuciones calificantes para Samsung A15 y Honor X5C; detectar cambios de clave y exigir rotación explícita; excluir registros manuales del conteo; mantener READY separado de calificación metrológica.

## Criterios de entrada

- conservar los 39 gates acumulados;
- mantener bloqueo de objetivo y remediación de puente fail-closed;
- mantener admisión guiada y finalización fail-closed;
- mantener journal, integridad SHA-256 y firma Ed25519 fail-closed;
- mantener `corporateIdentity=false`;
- conservar límites 8/120/1500 y cámara 0 fija;
- no declarar campañas físicas no ejecutadas;
- mantener PR en borrador.

## Criterios de salida

- ingesta de generaciones firmadas implementada;
- objetivo bloqueado e integridad exigidos en el conteo;
- continuidad de clave verificada;
- rotación de clave explícita y auditable;
- tres ejecuciones automáticas por dispositivo exigidas;
- evidencia manual excluida del conteo;
- nueva alpha compilada;
- CI producto e historial exitosos;
- ITER-023 archivada.
