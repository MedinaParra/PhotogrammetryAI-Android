# ITER-012 — Ventana BA runtime y telemetría automática

## Identificación

- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8` (borrador)
- **Versión:** `0.18.0-alpha30`
- **Commit funcional validado:** `d51615037636cf70ec0f7413ead6ba051a3a6095`
- **Fases:** R4, R5, R6 y R8

## Objetivo

Construir un problema de bundle adjustment local desde cámaras, poses, tracks, puntos fusionados y coordenadas de píxel producidos por una sesión real; medir automáticamente recursos y duración; y exportar la evidencia runtime sin permitir que una ventana incompleta o un safety gate en revisión sea presentado como optimización aprobada.

## Alcance ejecutado

### Incluido

- `RuntimeBundleWindowCore` determinista y fail-closed;
- límite de 8 cámaras, 120 puntos y 1500 observaciones;
- cámara global 0 preservada como gauge fijo;
- reconstrucción de coordenadas de píxel mediante la misma selección de frames y el mismo detector determinista del pipeline;
- asociación `trackId` entre tracks y puntos fusionados;
- construcción de `LocalBundleAdjustmentCore.Problem` desde observaciones runtime;
- serialización completa de matrices, traslaciones, intrínsecos, puntos y observaciones;
- huella SHA-256 determinista de la ventana;
- telemetría automática de duración, heap, PSS, memoria disponible y almacenamiento;
- archivos `runtime_ba_window.json`, `runtime_telemetry.json` y `runtime_audit.json`;
- inclusión de las tres evidencias dentro del ZIP de sesión;
- manifest de captura actualizado a `skm-polea-capture/2`;
- gate v55 incorporado a GitHub Actions;
- publicación de alpha30.

### Excluido

- campaña física Samsung A15;
- campaña física Honor X5C;
- temperatura automática del dispositivo;
- logcat/JNI automático;
- métricas suplementarias runtime completas de homografía, reflejos y repetición;
- optimización de rotaciones o intrínsecos;
- BA global;
- calificación metrológica;
- `armeabi-v7a`.

## Cambios y decisiones

La ventana BA se construye solo cuando el reporte global contiene poses, tracks y nube fusionada utilizables. Los frames se vuelven a seleccionar con la misma política 2/48 y las features se regeneran de forma determinista para recuperar las coordenadas de píxel referenciadas por cada `featureIndex`.

El constructor prioriza cámaras con mayor soporte, pero siempre inserta primero la cámara global 0. Los puntos se priorizan por número de vistas, soporte y reproyección, respetando los límites de memoria. Cualquier ausencia de gauge, intrínsecos, puntos, tracks u observaciones suficientes genera un estado bloqueado y no un problema parcial.

La ventana se entrega al coordinador runtime. Sin embargo, la aplicación todavía entrega métricas suplementarias desconocidas al safety gate. Por diseño, esas brechas mantienen el estado en `REVIEW` y evitan ejecutar el BA, aunque el problema esté correctamente construido. Esto conserva la política fail-closed y evita una falsa optimización.

La telemetría registra métricas del proceso Android y sirve para estabilidad técnica; no constituye una medición térmica ni una validación metrológica del teléfono.

## Evidencia y validación

| Prueba o revisión | Entorno | Resultado | Evidencia |
|---|---|---|---|
| límite de cámaras | JDK | PASS | 10 cámaras de entrada se reducen a 8 |
| límite de puntos | JDK | PASS | 150 puntos se reducen a 120 |
| observaciones reales acotadas | JDK | PASS | 960 observaciones conservadas dentro del límite |
| gauge cámara 0 | JDK | PASS | cámara global 0 permanece primera y fija |
| serialización completa | JDK | PASS | matrices, intrínsecos, puntos y observaciones presentes |
| huella reproducible | JDK | PASS | SHA-256 determinista |
| evidencia insuficiente | JDK | PASS | gauge o puntos faltantes bloquean |
| telemetría READY/REVIEW | JDK | PASS | ventana, interrupción y decisión evaluadas fail-closed |
| gate producto completo | GitHub Actions run `#579` | PASS | 29 gates, Gradle, APK y cierre OCCT |
| AAR STEP | GitHub Actions run `#579` | PASS | SHA-256 esperado verificado |
| campaña Samsung A15 | hardware | NO EJECUTADA | no existe dispositivo conectado |
| campaña Honor X5C | hardware | NO EJECUTADA | no existe dispositivo conectado |
| metrología | instrumentos trazables | NO EJECUTADA | fuera de alcance |

## Resultados

- Existe una ventana BA reproducible construida desde evidencia de sesión y no desde datos inventados.
- La aplicación puede entregar el problema real al coordinador cuando la ventana es admisible.
- El safety gate continúa gobernando la ejecución: una métrica faltante impide la optimización.
- La duración, heap, PSS, memoria disponible y almacenamiento se registran automáticamente.
- El paquete exportado conserva informe, ventana, telemetría, auditoría y fotografías.
- Alpha30 compila con STEP/OCCT para `arm64-v8a`.
- El artefacto `SKM-Polea-AI-CAD-STEP-v0.18.0-alpha30` fue publicado.
- No se declara campaña física ni autorización industrial.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Mitigación | Estado |
|---|---|---|---|---|
| VISION-003 | alta | métricas suplementarias todavía no se producen en el flujo runtime | ITER-013 | abierto |
| BA-001 | alta | rotaciones e intrínsecos permanecen fijos | iteración posterior | abierto |
| BA-003 | media | no existe BA global ni Schur | iteración posterior | abierto |
| DEVICE-001 | crítica | campaña Samsung A15 no ejecutada | prueba física | abierto |
| DEVICE-002 | crítica | campaña Honor X5C no ejecutada | prueba física | abierto |
| DEVICE-004 | alta | temperatura y fallos JNI no se capturan automáticamente | instrumentación Android | abierto |
| VALID-001 | crítica | no existe metrología trazable | campaña R8 | abierto |
| DATA-007 | media | hashes reales de todos los PDF pendientes | auditoría documental | abierto |
| ABI-001 | media | OCCT solo `arm64-v8a` | evaluar nuevo AAR | abierto |

## Estado del roadmap

- Avance integral anterior: **74 %**.
- Avance integral después de ITER-012: **78 %**.
- Madurez funcional alpha estimada: **95 %**.
- Preparación industrial/metrológica estimada: **17 %**.

El aumento corresponde a una integración runtime verificable, evidencia completa y telemetría automática. La preparación industrial solo aumenta un punto porque no hubo hardware físico, temperatura automática ni instrumentos trazables.

## Siguiente iteración obligatoria

### ITER-013 — Métricas suplementarias runtime y ejecución BA admitida

- calcular competencia homografía/fundamental desde pares reales;
- calcular desenfoque, reflejos y ambigüedad repetitiva por sesión;
- alimentar métricas suplementarias reales al safety gate;
- ejecutar BA únicamente cuando el gate completo sea `READY`;
- persistir claramente métricas antes/después y fallback;
- probar escenas planas, reflectantes, repetitivas y volumétricas;
- publicar alpha31.

## Criterios de entrada y salida

### Entrada

- conservar los 29 gates;
- mantener límites 8/120/1500 y cámara 0 fija;
- no reemplazar métricas desconocidas por valores favorables;
- no declarar campañas físicas no ejecutadas;
- mantener PR en borrador.

### Salida

- productores suplementarios conectados a pares y frames runtime;
- safety gate sin brechas cuando existe evidencia suficiente;
- BA ejecutado solo en un caso completamente `READY`;
- degeneraciones mantienen `REVIEW` o `BLOCKED`;
- métricas antes/después y fallback exportados;
- alpha31 compilada;
- CI producto e historial exitosos;
- ITER-013 archivada;
- `CURRENT.md` actualizado con ITER-014.
