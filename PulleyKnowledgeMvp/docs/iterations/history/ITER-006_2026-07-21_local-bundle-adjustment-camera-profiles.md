# ITER-006 — Bundle adjustment local acotado y perfiles de cámara

## Identificación

- **Fecha de inicio:** 2026-07-21
- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8`
- **Commit base:** `5e56b746266228c4ebeae6f6c0a1ec924132a0bc`
- **Commit funcional de cierre:** cabeza posterior a `5aeb098813a761942db137123606fc944b8157a4`
- **Versión de aplicación:** `0.18.0-alpha24`
- **Fase principal:** `R5`
- **Responsable técnico:** MedinaParra / asistencia de ingeniería

## Objetivo

Implementar una primera optimización multivista local, robusta y limitada para Android, fijar correctamente el gauge geométrico, exigir la aprobación previa de la puerta fotogramétrica y preparar perfiles versionados de intrínsecos y distorsión sin presentarlos como calibraciones físicas todavía.

## Alcance ejecutado

### Incluido

- `LocalBundleAdjustmentCore` con ventana máxima de 8 cámaras, 120 puntos y 1500 observaciones;
- optimización alternada de puntos 3D y traslaciones de cámara;
- cámara 0 fija como referencia de gauge;
- rotaciones e intrínsecos fijos durante esta primera versión;
- pérdida Huber para reducir el efecto de outliers;
- priors de traslación para preservar la escala local y evitar deriva excesiva;
- admisión obligatoria mediante safety gate en estado `READY`;
- medición de RMS, mediana, P90, profundidad positiva e iteraciones aceptadas;
- perfiles Brown-Conrady versionados para intrínsecos y distorsión;
- distorsión, desdistorsión y escalado de perfil a otra resolución;
- almacenamiento SQLite no destructivo de perfiles;
- selección automática únicamente de perfiles `VALIDATED` con RMS <= 1 px;
- pruebas sintéticas de convergencia, outliers, gauge fijo, problema insuficiente, safety gate y distorsión;
- integración del gate en GitHub Actions.

### Excluido

- bundle adjustment global;
- optimización conjunta de rotaciones;
- optimización de intrínsecos o coeficientes de distorsión;
- integración del BA dentro de `SessionOverlapAnalyzer`;
- calibración real con tablero o patrón;
- perfiles reales para Samsung A15 u Honor X5C;
- pruebas térmicas, de memoria o tiempo en teléfono;
- validación metrológica.

## Cambios y decisiones

### Optimización local

Se agregó `LocalBundleAdjustmentCore.java`. La implementación aplica refinamiento por bloques:

1. valida que el safety gate sea `READY`;
2. limita el tamaño del problema para evitar consumo no acotado en Android;
3. fija por completo la cámara 0;
4. refina cada punto con observaciones de al menos dos vistas;
5. refina las traslaciones de las demás cámaras;
6. aplica pérdida Huber a residuos de reproyección;
7. agrega priors hacia las traslaciones iniciales;
8. acepta únicamente iteraciones que disminuyen el costo robusto;
9. informa `CONVERGED`, `IMPROVED`, `STALLED` o `DIVERGED`.

Esta implementación se denomina explícitamente **bundle adjustment local acotado**. No es un BA global, no optimiza rotaciones y no reemplaza una implementación Schur completa.

### Perfiles de cámara

Se agregaron:

- `CameraCalibrationProfileCore.java`;
- `CameraCalibrationProfileStore.java`.

Cada perfil conserva:

- identificador, dispositivo, cámara y versión;
- resolución de referencia;
- `fx`, `fy`, `cx`, `cy`;
- `k1`, `k2`, `p1`, `p2`, `k3`;
- estado `UNCALIBRATED`, `LAB_ESTIMATED` o `VALIDATED`;
- RMS de reproyección;
- fuente y huella determinista.

Un perfil `UNCALIBRATED` o `LAB_ESTIMATED` no puede seleccionarse para corrección automática. El almacenamiento es versionado y no destructivo.

## Evidencia y validación

| Prueba o revisión | Entorno | Resultado | Evidencia |
|---|---|---|---|
| BA local con 4 cámaras, 24 puntos y outliers | JDK | PASS | RMS final inferior al inicial |
| Gauge de cámara 0 | JDK | PASS | traslación permanece invariable |
| Profundidad positiva | JDK | PASS | relación superior a 0,98 |
| Safety gate no aprobado | JDK | PASS / BLOCKED | `SAFETY_GATE_NOT_READY` |
| Problema con puntos insuficientes | JDK | PASS / BLOCKED | `INSUFFICIENT_POINTS` |
| Distorsión/desdistorsión Brown-Conrady | JDK | PASS | retorno dentro de 0,03 px |
| Escalado de perfil 1280x960 a 640x480 | JDK | PASS | focal y centro principal escalados |
| Perfil no calibrado | JDK | PASS / no seleccionable | fail-closed |
| Persistencia de perfiles | compilación Android | PASS | SQLite versionado integrado |
| GitHub Actions producto | run `#460` | PASS al cierre | gates, APK y cierre OCCT |
| Historial de iteraciones | GitHub Actions | PASS | validador independiente |
| BA con fotografías reales | Android | NO EJECUTADA | fuera de alcance |
| Calibración de Samsung A15 | patrón físico | NO EJECUTADA | fuera de alcance |
| Calibración de Honor X5C | patrón físico | NO EJECUTADA | fuera de alcance |
| Validación metrológica | instrumentos trazables | NO EJECUTADA | fuera de alcance |

## Resultados

- El proyecto ya contiene una optimización local real y determinista, no solo refinamiento independiente de un punto.
- La optimización está limitada para evitar problemas de tamaño arbitrario en Android.
- Una sesión insegura no puede ingresar al BA.
- El problema sintético con ruido y outliers reduce el error de reproyección y conserva el gauge.
- Los perfiles de cámara pueden versionarse, escalarse y almacenarse sin activar datos no validados.
- Alpha24 compila con el motor STEP/OCCT para `arm64-v8a`.
- Todavía no existe BA global, calibración física ni integración runtime del optimizador.
- No se declara precisión metrológica.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Mitigación | Estado |
|---|---|---|---|---|
| BA-001 | alta | solo se optimizan puntos y traslaciones | incorporar rotaciones en etapa posterior | abierto |
| BA-002 | alta | el BA no está conectado al pipeline real | ITER-007 | abierto |
| BA-003 | media | solución por bloques no usa eliminación Schur | comparar rendimiento antes de ampliar ventana | abierto |
| CAL-001 | crítica | no existen perfiles físicos Samsung/Honor | campaña con patrón | abierto |
| CAL-002 | alta | coeficientes no se optimizan en la app | herramienta de calibración separada | abierto |
| UI-001 | alta | safety gate y BA no gobiernan todavía el flujo visual | ITER-007 | abierto |
| VALID-001 | crítica | no hay prueba real ni metrológica | campañas R6/R8 | abierto |
| ABI-001 | media | OCCT solo para `arm64-v8a` | estudio posterior | abierto |

## Estado del roadmap

| Fase | Antes | Después | Puerta de salida |
|---|---|---|---|
| R4 — Fotogrametría robusta | 65 % | 68 % | abierta: integración y banco real pendientes |
| R5 — Optimización y calibración | 5 % | 35 % | abierta: BA global y calibración física pendientes |
| R6–R8 | sin cambio | sin cambio | abiertas |

Avance ponderado estimado después de ITER-006: **55 %**.

## Siguiente iteración obligatoria

### ITER-007 — Integración runtime del safety gate y BA local

Objetivos concretos:

1. integrar el safety gate en el flujo real de reconstrucción;
2. impedir ejecución automática cuando el estado sea `REVIEW` o `BLOCKED`;
3. construir ventanas BA desde tracks, poses, intrínsecos y observaciones reales;
4. ejecutar BA local únicamente en problemas admitidos y acotados;
5. persistir resultados antes/después, parámetros, duración y estado;
6. conservar un fallback seguro hacia la geometría sin optimizar;
7. integrar perfiles validados cuando existan y mantener Camera2 físico como fuente provisional;
8. probar recuperación ante divergencia, interrupción y falta de memoria;
9. publicar `0.18.0-alpha25`.

## Criterios de entrada y salida

### Entrada

- partir de la cabeza final de ITER-006;
- no ampliar los límites del BA sin mediciones de memoria/tiempo;
- mantener la cámara 0 fija y los priors de escala;
- no activar perfiles no validados;
- no afirmar BA global ni calibración física.

### Salida

- safety gate conectado al flujo real;
- BA local alimentado por observaciones reales del pipeline;
- persistencia de métricas antes/después;
- fallo o divergencia produce fallback y `REVIEW`/`BLOCKED`;
- pruebas de interrupción y recursos;
- alpha25 y CI exitosos;
- ITER-007 archivada;
- `CURRENT.md` actualizado con ITER-008.
