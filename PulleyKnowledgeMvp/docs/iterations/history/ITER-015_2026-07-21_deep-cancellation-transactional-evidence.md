# ITER-015 — Checkpoints profundos, recuperación transaccional y campaña reproducible

## Identificación

- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8` (borrador)
- **Versión:** `0.18.0-alpha33`
- **Commit funcional validado:** `19897e94be4deabdc93751c1696839adc961335e`
- **Fases:** R0, R4, R6 y R8

## Objetivo

Observar cancelación dentro del trabajo visual costoso, impedir que resultados parciales se presenten como vigentes, publicar evidencia runtime mediante generaciones comprometidas y producir un manifiesto reproducible que vincule APK, dispositivo, sesión, frames y archivos runtime por SHA-256.

## Alcance ejecutado

### Incluido

- `RuntimeCancellationBridge` con token `ThreadLocal` por ejecución;
- checkpoints opcionales dentro de `VisualFeatureCore.detect()` y `VisualFeatureCore.match()`;
- checkpoints durante filas Harris, selección de features y búsquedas de matching en ambos sentidos;
- integración del bridge antes de `SessionOverlapAnalyzer.analyze()` y limpieza garantizada en `finally`;
- compatibilidad de los gates Java históricos mediante enlace reflectivo opcional;
- `RuntimeEvidenceTransactionCore` con directorios `.pending` y `.committed`;
- escritura sincronizada a disco antes de promover una generación;
- manifiesto de generación con ruta, tamaño y SHA-256;
- puntero activo `runtime_active_generation.txt` promovido mediante archivo temporal;
- rollback que conserva la generación comprometida anterior;
- generación `ABORTED` separada sin ventana BA parcial;
- generación `BLOCKED` para fallos no controlados;
- `CampaignEvidenceManifestCore` canónico y determinista;
- vínculo entre versión de app, SHA-256 del APK instalado, dispositivo, SDK, sesión, código, OT, frames y evidencia runtime;
- `CampaignEvidenceManifestBuilder` Android;
- exportación exclusiva de la generación activa comprometida;
- compatibilidad de exportación para sesiones legacy sin puntero activo;
- esquema de paquete `skm-polea-capture/5`;
- gate v58 integrado a GitHub Actions;
- publicación de alpha33.

### Excluido

- checkpoints dentro de la decodificación `BitmapFactory`;
- checkpoints dentro de las iteraciones RANSAC de matriz fundamental;
- checkpoints internos de recuperación de pose y triangulación;
- transacción SQLite que abarque simultáneamente archivos y tablas;
- firma criptográfica con una clave privada o certificado corporativo;
- sellado de tiempo externo;
- validación física de cancelación, cierre forzado o recuperación en Samsung A15/Honor X5C;
- optimización de rotaciones o intrínsecos;
- BA global;
- metrología trazable;
- `armeabi-v7a`.

## Cambios y decisiones

`VisualFeatureCore` continúa siendo utilizable por los gates Java que solo compilan ese archivo. La instrumentación busca por reflexión `RuntimeCancellationBridge`; cuando la clase no está presente no existe dependencia obligatoria. Dentro de Android, la actividad instala el token en el thread que ejecuta el analizador, por lo que la detección y el matching observan cancelación y deadline sin cambiar sus firmas públicas.

La promoción transaccional no reemplaza cada archivo individual. Todos los archivos de una ejecución se escriben en `runtime-generations/<run>.pending`; se calculan hashes y se crea `generation_manifest.json`; el directorio se renombra a `.committed`; finalmente se reemplaza el puntero activo. El exportador solo lee el directorio indicado por ese puntero. Así, una caída previa al commit deja como vigente la generación anterior, no la parcial.

Una cancelación elimina la generación parcial y crea una nueva generación `ABORTED` con diagnóstico, telemetría, motivo, etapa y manifiesto de campaña. No contiene `runtime_ba_window.json` parcial ni puede declarar geometría optimizada aceptada.

El manifiesto de campaña es reproducible para el mismo contenido porque ordena los elementos y calcula su huella sobre JSON canónico. La huella demuestra identidad del paquete de evidencia, pero no constituye firma digital ni acredita autoría.

## Evidencia y validación

| Prueba o revisión | Entorno | Resultado | Evidencia |
|---|---|---|---|
| cancelación durante detección | JDK | PASS | `AbortedException` en etapa `FEATURE_DETECT_*` |
| cancelación durante matching | JDK | PASS | `AbortedException` en etapa `FEATURE_MATCH_*` |
| compatibilidad VisualFeatureCore histórica | JDK | PASS | gates v29 y dependientes permanecen verdes |
| pending no activo | JDK | PASS | `activeDirectory()` retorna null antes del commit |
| promoción de generación | JDK | PASS | directorio `.committed`, manifiesto y puntero activos |
| rollback | JDK | PASS | generación anterior continúa activa |
| aborto seguro | JDK | PASS | `runtime_abort.json` presente y ventana parcial ausente |
| manifiesto determinista | JDK | PASS | mismo JSON/huella para orden de entrada distinto |
| manifiesto incompleto | JDK | PASS | APK/frames/runtime ausentes producen brechas |
| gate producto | GitHub Actions run `#670` | PASS | 32 gates, Gradle, APK y cierre OCCT |
| historial previo al cierre | GitHub Actions run `#199` | PASS | estructura ITER-014/ITER-015 válida |
| AAR STEP | GitHub Actions run `#670` | PASS | SHA-256 esperado y 25 bibliotecas nativas |
| campaña Samsung A15 | hardware | NO EJECUTADA | no existe dispositivo conectado |
| campaña Honor X5C | hardware | NO EJECUTADA | no existe dispositivo conectado |
| metrología | instrumentos trazables | NO EJECUTADA | fuera de alcance |

## Resultados

- La cancelación puede interrumpir trabajo dentro de detección y matching, no solo antes o después del analizador.
- Una ejecución parcial nunca sustituye a la generación comprometida vigente.
- El exportador evita mezclar evidencia vieja y nueva.
- Una cancelación publica únicamente evidencia segura de aborto y fallback.
- El paquete vincula el APK instalado, dispositivo, sesión, frames y runtime mediante hashes reproducibles.
- Alpha33 compila con STEP/OCCT para `arm64-v8a`.
- Se publicó `SKM-Polea-AI-CAD-STEP-v0.18.0-alpha33`.
- No se declara firma digital, validación física, metrología ni uso industrial.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Mitigación | Estado |
|---|---|---|---|---|
| RUNTIME-003 | alta | fundamental, pose, triangulación y Bitmap decode no tienen checkpoints internos | instrumentar fases críticas | abierto |
| TX-001 | media | el commit es de archivos y puntero, no una transacción SQLite+filesystem | journal coordinado posterior | abierto |
| SIGN-001 | media | la huella no está firmada por una identidad corporativa | firma Ed25519/keystore posterior | abierto |
| DEVICE-001 | crítica | campaña Samsung A15 no ejecutada | prueba física | abierto |
| DEVICE-002 | crítica | campaña Honor X5C no ejecutada | prueba física | abierto |
| JNI-001 | alta | historial nativo no cubre todo JNI | registro nativo estructurado | abierto |
| BA-001 | alta | rotaciones e intrínsecos permanecen fijos | ITER-016 | abierto |
| BA-003 | media | no existe BA global | iteración posterior | abierto |
| VALID-001 | crítica | no existe metrología trazable | campaña R8 | abierto |
| DATA-007 | media | hashes reales de todos los PDF pendientes | auditoría documental | abierto |
| ABI-001 | media | OCCT solo `arm64-v8a` | evaluar nuevo AAR | abierto |

## Estado del roadmap

- Avance integral anterior: **86 %**.
- Avance integral después de ITER-015: **89 %**.
- Madurez funcional alpha estimada: **98 %**.
- Preparación industrial/metrológica estimada: **22 %**.

El incremento corresponde a integridad y recuperación del flujo runtime. La preparación industrial solo aumenta dos puntos porque no hubo ejecución en hardware ni comparación contra instrumentos trazables.

## Siguiente iteración obligatoria

### ITER-016 — Rotaciones BA acotadas e incertidumbre estadística

- agregar parámetros de rotación de pequeña amplitud para cámaras distintas de la cámara 0;
- mantener gauge fijo y límites 8/120/1500;
- utilizar damping y priors rotacionales para evitar deriva;
- rechazar soluciones que empeoren reproyección, profundidad o coherencia de poses;
- estimar dispersión e intervalos estadísticos de residuos sin llamarlos incertidumbre metrológica;
- conservar fallback para divergencia o mala condición;
- probar casos convergentes, degenerados y con outliers;
- publicar alpha34.

## Criterios de entrada y salida

### Entrada

- conservar los 32 gates;
- mantener generación transaccional y manifiesto reproducible;
- no mover cámara 0;
- no optimizar intrínsecos todavía;
- no declarar campañas físicas no ejecutadas;
- mantener PR en borrador.

### Salida

- rotaciones acotadas incorporadas al BA local;
- gauge y priors verificados;
- aceptación exige mejora geométrica integral;
- intervalos estadísticos claramente diferenciados de metrología;
- fallback por degeneración probado;
- alpha34 compilada;
- CI producto e historial exitosos;
- ITER-016 archivada;
- `CURRENT.md` actualizado con ITER-017.