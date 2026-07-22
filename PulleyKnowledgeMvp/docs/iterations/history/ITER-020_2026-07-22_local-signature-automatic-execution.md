# ITER-020 — Firma local verificable y evidencia automática de ejecución

## Identificación

- **Fecha de cierre:** 2026-07-22
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8` (borrador)
- **Versión:** `0.18.0-alpha41`
- **Commit funcional validado:** `ac0717f284d14ef078f5588b81593ce8d4b20fed`
- **GitHub Actions:** producto run `#774`
- **Fases:** R5, R6 y R8

## Objetivo

Agregar una firma criptográfica local verificable sobre el manifiesto canónico de campaña, registrar de forma automática la ejecución runtime y bloquear la exportación cuando el payload, la firma o la clave pública no validen. La solución debe declarar el origen de la clave sin atribuir identidad corporativa, atestación remota ni validez metrológica.

## Alcance ejecutado

### Incluido

- núcleo Java puro de firma y verificación Ed25519;
- SHA-256 del payload firmado;
- clave pública X.509 y firma codificadas en Base64 sin depender de APIs recientes de Base64;
- intento preferente de StrongBox cuando Android lo permite;
- segundo intento mediante Android Keystore;
- fallback persistente `SOFTWARE_APP_PRIVATE` dentro del almacenamiento privado de la aplicación;
- estado explícito `UNAVAILABLE` cuando ningún proveedor Ed25519 está disponible;
- declaración `hardwareBacked` y `strongBoxBacked` basada en el proveedor realmente obtenido;
- invariante `corporateIdentity=false`;
- etiqueta `LOCAL_DEVICE_KEY_NOT_CORPORATE_IDENTITY`;
- autoverificación inmediata antes de aceptar una generación READY;
- evidencia `runtime_execution_evidence.json` generada por el runtime, con `automated=true` y `manualEntry=false`;
- firma incluida en generaciones READY y evidencia explícita disponible para ABORTED/BLOCKED;
- verificación de la firma nuevamente antes de crear el ZIP;
- archivo `export_signature_verification.json` dentro del paquete exportado;
- gate v62 para firma válida, payload alterado, clave incorrecta, estado no disponible y proveniencia automática;
- publicación Android `0.18.0-alpha41`.

### Excluido

- identidad corporativa, certificado de empresa o PKI;
- atestación remota de Android Keystore;
- garantía de que todos los teléfonos soporten Ed25519 en hardware;
- firma del APK de producción con certificado de distribución;
- sincronización de claves entre dispositivos;
- campaña física Samsung A15 u Honor X5C;
- validación metrológica trazable;
- `armeabi-v7a`.

## Cambios y decisiones

El payload firmado es el contenido exacto de `campaign_evidence_manifest.json`. Ese manifiesto ya vincula versión de aplicación, SHA-256 del APK instalado, dispositivo, sesión, fotogramas y archivos runtime. La firma no reemplaza la verificación de integridad: primero se valida `generation_manifest.json` y todos sus hashes; después se valida Ed25519 sobre el manifiesto de campaña.

La aplicación intenta crear o reutilizar una clave Ed25519 en StrongBox y luego en Android Keystore. Si esos proveedores no están disponibles, genera una clave software exportable y la conserva en `SharedPreferences` privados de la aplicación. El origen publicado describe el mecanismo realmente usado; no se infiere soporte hardware cuando `KeyInfo` no lo confirma.

Una ejecución READY exige firma local autoverificada. Si la firma no está disponible, la ejecución normal pasa a BLOCKED. Las rutas ABORTED/BLOCKED pueden conservar un archivo de firma con estado `UNAVAILABLE`, pero el exportador no permite crear un ZIP firmado desde ese estado.

`runtime_execution_evidence.json` se produce exclusivamente con datos del runtime: sesión, runId, tiempos, duración monotónica, cantidad de imágenes, tamaño de ventana BA, estado del gate, decisión y fallback. No existe campo editable por el operador para convertir una anotación manual en ejecución automática.

## Evidencia y validación

| Prueba o revisión | Entorno | Resultado | Evidencia |
|---|---|---|---|
| firma Ed25519 y autoverificación | JDK 17 | PASS | firma válida sobre payload canónico |
| payload alterado | JDK 17 | PASS | `PAYLOAD_SHA256_MISMATCH` o firma inválida |
| clave pública diferente | JDK 17 | PASS | verificación rechazada |
| proveedor no disponible | JDK 17 | PASS | estado `UNAVAILABLE`, nunca válido |
| identidad corporativa | JDK 17 | PASS | `corporateIdentity=false` invariable |
| proveniencia automática | JDK 17 | PASS | `AUTOMATIC_RUNTIME`, `automated=true`, `manualEntry=false` |
| huella de ejecución determinística | JDK 17 | PASS | mismo input produce mismo fingerprint |
| gates acumulados | GitHub Actions run `#774` | PASS | 37 gates aprobados |
| compilación Android | GitHub Actions run `#774` | PASS | Gradle `assembleDebug` aprobado |
| integración Keystore/StrongBox | compileSdk 35 | PASS de compilación | implementación Android incluida; hardware no ejecutado |
| cierre STEP/OCCT | GitHub Actions run `#774` | PASS | SHA-256 del AAR y 25 bibliotecas nativas aprobados |
| APK alpha41 | GitHub Actions run `#774` | PASS | artefacto `SKM-Polea-AI-CAD-STEP-v0.18.0-alpha41` |
| campaña Samsung A15 | hardware | NO EJECUTADA | dispositivo no conectado a CI |
| campaña Honor X5C | hardware | NO EJECUTADA | dispositivo no conectado a CI |
| metrología | instrumentos trazables | NO EJECUTADA | fuera de alcance de la iteración |

## Resultados

- Cada generación READY contiene un manifiesto de campaña y su evidencia de firma local.
- El exportador vuelve a verificar integridad y firma antes de escribir el ZIP.
- Alterar el manifiesto, sustituir la clave o eliminar la firma bloquea la exportación.
- El origen de la clave queda distinguido entre StrongBox, Android Keystore, software app-private y no disponible.
- La evidencia automática no puede confundirse con un registro manual.
- Alpha41 compila con STEP/OCCT para `arm64-v8a`.
- No se declara identidad corporativa, atestación, campaña física completada ni exactitud metrológica.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Mitigación | Estado |
|---|---|---|---|---|
| SIGN-002 | alta | la clave local no representa identidad corporativa ni está atestada remotamente | mantener etiqueta explícita y futura PKI/attestation | mitigado |
| SIGN-003 | media | algunos Android pueden no ofrecer Ed25519 en Keystore | fallback software y estado fail-closed `UNAVAILABLE` | mitigado |
| KEY-001 | media | una reinstalación puede eliminar la clave app-private y cambiar identidad local | futura continuidad/rotación de clave en device-alpha | abierto |
| RUNTIME-004 | media | `Bitmap` decode y algunas llamadas nativas no admiten checkpoint interno | límites preventivos | abierto |
| TX-002 | media | journal recuperable, no transacción ACID única | mantener recuperación e integridad | mitigado |
| INTR-002 | alta | observabilidad focal solo sintética | campañas A15/X5C | abierto |
| BA-003 | media | no existe BA global ni Schur complement | iteración posterior | abierto |
| DEVICE-001 | crítica | campaña Samsung A15 no ejecutada | prueba física | abierto |
| DEVICE-002 | crítica | campaña Honor X5C no ejecutada | prueba física | abierto |
| VALID-001 | crítica | metrología trazable ausente | campaña R8 | abierto |
| ABI-001 | media | OCCT solo `arm64-v8a` | evaluar AAR universal | abierto |

## Estado del roadmap

- Avance integral anterior: **96 %**.
- Avance integral después de ITER-020: **97 %**.
- Madurez funcional alpha estimada: **99 %**.
- Preparación industrial/metrológica estimada: **26 %**.

El avance aumenta por autenticidad local y proveniencia automática. La preparación industrial solo aumenta de forma marginal porque el protocolo ya puede consumir evidencia real, pero todavía no hubo ejecuciones físicas ni metrología trazable.

## Siguiente iteración obligatoria

### ITER-021 — Calificación device-alpha con continuidad de clave

- consumir únicamente generaciones con integridad y firma verificadas;
- contar ejecuciones automáticas reales por modelo de dispositivo;
- exigir al menos tres ejecuciones válidas por Samsung A15 y Honor X5C;
- detectar cambios de clave y exigir un evento explícito de rotación;
- rechazar registros manuales como evidencia calificante;
- mantener READY separado de calificación metrológica.

## Criterios de entrada y salida

### Entrada

- conservar los 37 gates acumulados;
- mantener firma Ed25519, journal e integridad fail-closed;
- mantener `corporateIdentity=false`;
- mantener límites BA y cámara 0 invariantes;
- no declarar campañas físicas no ejecutadas;
- mantener PR en borrador y sin fusionar.

### Salida

- ingesta automática de generaciones firmadas implementada;
- continuidad y rotación de clave verificadas;
- tres ejecuciones por dispositivo exigidas por protocolo;
- evidencia manual excluida del conteo;
- nueva alpha compilada;
- CI de producto e historial exitosos;
- ITER-021 archivada.
