# Estado actual de iteraciones

**Actualizado:** 2026-07-22  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha41`

## Estado acumulado

- Alpha41 compila para `arm64-v8a` con STEP/OCCT.
- Evidencia dimensional, identificación, safety gate, pose graph y reconstrucción mantienen gates automáticos.
- La ventana BA permanece limitada a 8 cámaras, 120 puntos y 1500 observaciones con cámara global 0 fija.
- Las etapas base, rotacional y focal permanecen acotadas y con fallback fail-closed.
- RANSAC fundamental, SVD esencial, cheirality y triangulación DLT poseen checkpoints internos cooperativos.
- La publicación runtime usa journal SQLite recuperable y promoción protegida de puntero.
- Antes de exportar se verifican rutas, tamaños, archivos listados y SHA-256.
- Cada generación READY firma `campaign_evidence_manifest.json` mediante Ed25519.
- StrongBox se intenta primero, luego Android Keystore y finalmente `SOFTWARE_APP_PRIVATE`.
- El origen hardware/software se declara según el proveedor realmente obtenido.
- Una ejecución READY exige autoverificación de firma local.
- El exportador verifica nuevamente payload, firma y clave pública antes de crear el ZIP.
- La firma usa `corporateIdentity=false` y `LOCAL_DEVICE_KEY_NOT_CORPORATE_IDENTITY`.
- `runtime_execution_evidence.json` se genera automáticamente con `manualEntry=false`.
- No existe todavía campaña física completada ni calificación industrial o metrológica.

## Avance global estimado

- **Avance integral: 97 %.**
- **Madurez alpha: 99 %.**
- **Preparación industrial/metrológica: 26 %.**

El incremento corresponde a autenticidad local, verificación fail-closed y proveniencia automática. La preparación industrial aumenta solo marginalmente porque Samsung A15, Honor X5C, calibración física e instrumentos trazables todavía no fueron ejecutados.

## Iteración actual o última cerrada

### ITER-020 — Firma local verificable y evidencia automática de ejecución

Registro: `history/ITER-020_2026-07-22_local-signature-automatic-execution.md`

- firma Ed25519 sobre el manifiesto canónico de campaña;
- SHA-256 del payload y clave pública X.509 incluidos;
- intento StrongBox y Android Keystore;
- fallback software app-private;
- estado `UNAVAILABLE` explícito y no verificable;
- `corporateIdentity=false` invariable;
- evidencia automática de ejecución persistida;
- exportación bloqueada ante payload, firma o clave incorrectos;
- archivo `export_signature_verification.json` incluido en ZIP válido;
- producto run `#774`: `success`;
- 37 gates Java;
- Gradle y cierre OCCT aprobados;
- APK alpha41 publicada;
- uso industrial continúa bloqueado.

## Bloqueos activos

1. `SIGN-002`: la clave local no representa identidad corporativa ni atestación remota;
2. `KEY-001`: reinstalación o borrado de datos puede cambiar la identidad local de clave;
3. `RUNTIME-004`: `Bitmap` decode y algunas llamadas nativas no admiten checkpoint interno;
4. `TX-002`: journal recuperable, pero no transacción ACID única SQLite/filesystem;
5. `INTR-002`: observabilidad focal validada sintéticamente, no en cámaras físicas;
6. `BA-003`: no existe BA global ni Schur complement;
7. `STAT-001`: sensibilidad e intervalos no representan incertidumbre física o dimensional;
8. `DEVICE-001`: campaña Samsung A15 no ejecutada;
9. `DEVICE-002`: campaña Honor X5C no ejecutada;
10. `VALID-001`: metrología trazable ausente;
11. `DATA-007`: hashes reales de todos los PDF pendientes;
12. `ABI-001`: OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-021 — Calificación device-alpha con continuidad de clave

Consumir únicamente generaciones automáticas con integridad y firma válidas; contar ejecuciones por modelo objetivo; exigir tres ejecuciones calificantes para Samsung A15 y Honor X5C; detectar cambios de clave y exigir rotación explícita; excluir registros manuales del conteo; mantener READY separado de calificación metrológica.

## Criterios de entrada

- conservar los 37 gates acumulados;
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
- ITER-021 archivada.
