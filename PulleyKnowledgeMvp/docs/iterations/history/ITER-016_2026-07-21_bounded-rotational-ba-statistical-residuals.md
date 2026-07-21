# ITER-016 — Rotaciones BA acotadas e intervalos estadísticos de residuos

## Identificación

- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8` (borrador)
- **Versión:** `0.18.0-alpha34`
- **Commit funcional validado:** `a98dec5693b934268910e8018d05cb25a55f05c1`
- **Fases:** R4, R5 y R8

## Objetivo

Incorporar una segunda etapa conservadora de bundle adjustment que permita pequeñas correcciones rotacionales de las cámaras distintas de la cámara 0, mantenga intrínsecos y gauge fijos, rechace degradaciones geométricas y publique estadística empírica de residuos sin presentarla como incertidumbre metrológica.

## Alcance ejecutado

### Incluido

- `RotationalBundleAdjustmentCore` como segunda etapa posterior al BA estable de puntos y traslaciones;
- cámara global 0 totalmente fija en rotación y traslación;
- centros de cámara preservados durante las propuestas rotacionales;
- incrementos rotacionales mediante mapa exponencial SO(3);
- máximo configurable de 0,35° por paso y 3° acumulados por cámara;
- damping adaptativo y prior rotacional;
- pérdida Huber sobre residuos de reproyección;
- reequilibrio posterior de puntos y traslaciones usando el BA base;
- aceptación únicamente con mejora de costo robusto, RMS y profundidad positiva;
- fallback exacto al objeto de resultado del BA base cuando la etapa rotacional no es admisible;
- estados `ROTATION_ACCEPTED`, `ROTATION_STALLED`, `ROTATION_REJECTED_DEPTH` y `ROTATION_REJECTED_GAIN`;
- mediana, MAD, P90 e intervalo empírico 2,5–97,5 % de residuos en píxeles;
- etiqueta obligatoria `EMPIRICAL_RESIDUAL_INTERVAL_NOT_METROLOGICAL`;
- integración en `RuntimeReconstructionCoordinator`;
- persistencia `runtime_rotational_ba.json` dentro de la generación transaccional;
- auditoría runtime esquema 5 con estado, aplicación, rotación máxima y etiqueta estadística;
- gate v59 con convergencia, prior fuerte, gauge fijo, outliers y bloqueo por safety gate;
- publicación de alpha34.

### Excluido

- optimización de `fx`, `fy`, `cx`, `cy` o distorsión;
- rotación de la cámara 0;
- BA global o Schur complement;
- covarianza formal de parámetros;
- propagación de incertidumbre desde cámara a dimensiones físicas;
- interpretación metrológica de percentiles de reproyección;
- validación física en Samsung A15 u Honor X5C;
- comparación contra patrón calibrado;
- `armeabi-v7a`.

## Cambios y decisiones

El optimizador estable `LocalBundleAdjustmentCore` continúa ejecutándose primero. La nueva etapa usa su resultado como prior y no modifica su implementación. Esto permite conservar un fallback probado si la corrección rotacional no aporta una mejora suficiente.

La cámara 0 conserva exactamente su matriz de rotación y vector de traslación. Para las demás cámaras se modela un vector de rotación pequeño, se limita su norma y se recompone la traslación para preservar el centro óptico durante la propuesta. Luego se permite un reequilibrio acotado de puntos y traslaciones.

La etapa rotacional solo se acepta cuando:

- existió al menos una iteración rotacional aceptada;
- el costo robusto disminuyó como mínimo de forma material;
- el RMS disminuyó respecto del BA base;
- la profundidad positiva permanece sobre 80 % y no cae materialmente;
- la rotación máxima respeta el límite configurado;
- la cámara 0 permanece idéntica.

Si cualquiera de esos criterios falla, `bundleAdjustment` apunta al resultado base exacto. El coordinador nunca necesita publicar una solución rotacional degradada.

Los intervalos de residuos son percentiles empíricos de errores de reproyección en píxeles. No incorporan trazabilidad, calibración física, modelo de error del instrumento ni propagación dimensional; por ello se etiquetan explícitamente como no metrológicos.

## Evidencia y validación

| Prueba o revisión | Entorno | Resultado | Evidencia |
|---|---|---|---|
| cámaras con sesgo rotacional 0,9–1,6° | JDK | PASS | etapa rotacional aceptada y RMS reducido |
| límite de rotación | JDK | PASS | rotación máxima <= 3° |
| cámara 0 fija | JDK | PASS | matriz y traslación idénticas a 1e-12 |
| profundidad positiva | JDK | PASS | > 98 % en escenario convergente |
| outliers de imagen | JDK | PASS | pérdida robusta conserva convergencia |
| prior rotacional fuerte | JDK | PASS | `rotationApplied=false` y resultado base exacto |
| safety gate bloqueado | JDK | PASS | no se ejecuta BA rotacional |
| intervalo estadístico | JDK | PASS | percentiles ordenados y etiqueta no metrológica |
| gate producto | GitHub Actions run `#688` | PASS | 33 gates, Gradle, APK y cierre OCCT |
| historial previo al cierre | GitHub Actions run `#213` | PASS | estructura de iteraciones válida |
| AAR STEP | GitHub Actions run `#688` | PASS | SHA-256 esperado y 25 bibliotecas nativas |
| campaña Samsung A15 | hardware | NO EJECUTADA | no existe dispositivo conectado |
| campaña Honor X5C | hardware | NO EJECUTADA | no existe dispositivo conectado |
| metrología | instrumentos trazables | NO EJECUTADA | fuera de alcance |

## Resultados

- El BA local puede corregir pequeños errores rotacionales sin mover la cámara de referencia.
- La solución rotacional se descarta cuando no supera de forma segura al BA base.
- La decisión runtime recibe siempre el mejor resultado admitido, no una propuesta intermedia.
- La generación comprometida conserva la evidencia rotacional y su estadística.
- Alpha34 compila con STEP/OCCT para `arm64-v8a`.
- Se publicó `SKM-Polea-AI-CAD-STEP-v0.18.0-alpha34`.
- No se declara calibración, incertidumbre dimensional, metrología ni uso industrial.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Mitigación | Estado |
|---|---|---|---|---|
| INTR-001 | alta | intrínsecos permanecen fijos aunque Camera2 pueda tener error | ITER-017 | abierto |
| BA-003 | media | no existe BA global ni Schur complement | etapa posterior | abierto |
| STAT-001 | alta | intervalos son empíricos y no representan incertidumbre física | mantener etiqueta y campaña trazable | abierto |
| RUNTIME-003 | alta | fundamental, pose, triangulación y Bitmap decode no tienen checkpoints internos | instrumentar fases críticas | abierto |
| TX-001 | media | transacción filesystem no está coordinada con SQLite | journal posterior | abierto |
| DEVICE-001 | crítica | campaña Samsung A15 no ejecutada | prueba física | abierto |
| DEVICE-002 | crítica | campaña Honor X5C no ejecutada | prueba física | abierto |
| VALID-001 | crítica | no existe metrología trazable | campaña R8 | abierto |
| DATA-007 | media | hashes reales de todos los PDF pendientes | auditoría documental | abierto |
| ABI-001 | media | OCCT solo `arm64-v8a` | evaluar nuevo AAR | abierto |

## Estado del roadmap

- Avance integral anterior: **89 %**.
- Avance integral después de ITER-016: **92 %**.
- Madurez funcional alpha estimada: **99 %**.
- Preparación industrial/metrológica estimada: **24 %**.

El incremento corresponde a una mejora geométrica acotada y auditable. La preparación industrial aumenta poco porque no hubo ejecución en hardware objetivo, calibración física ni instrumentos trazables.

## Siguiente iteración obligatoria

### ITER-017 — Intrínsecos focales condicionados y observabilidad

- permitir una corrección focal pequeña y fuertemente priorizada;
- mantener `cx`, `cy` y coeficientes de distorsión fijos;
- aplicar límites porcentuales por cámara y perfiles validados;
- estimar observabilidad y bloquear ajustes focales mal condicionados;
- aceptar intrínsecos solo si mejoran reproyección, profundidad y coherencia sin absorber errores de pose;
- conservar fallback al BA rotacional/base;
- producir estadística de sensibilidad claramente no metrológica;
- publicar alpha35.

## Criterios de entrada y salida

### Entrada

- conservar los 33 gates;
- mantener cámara 0 fija y límites 8/120/1500;
- conservar generaciones transaccionales y manifiestos;
- no optimizar principal point ni distorsión;
- no declarar perfiles físicos no validados;
- mantener PR en borrador.

### Salida

- corrección focal acotada implementada;
- observabilidad y límites verificados;
- fallback por mala condición probado;
- sensibilidad etiquetada como estadística no metrológica;
- alpha35 compilada;
- CI producto e historial exitosos;
- ITER-017 archivada;
- `CURRENT.md` actualizado con ITER-018.