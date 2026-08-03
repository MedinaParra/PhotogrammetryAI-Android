# ITER-021 — Captura guiada de dos anillos y remediación de prueba de terreno

## Identificación

- **Fecha:** 2026-07-22
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8` — borrador, abierto y no fusionado
- **Versión:** `0.18.0-alpha42`
- **Origen:** evidencia real entregada por el usuario desde una sesión Android `demo2`

## Objetivo

Corregir el flujo que permitió aceptar 137 fotografías concentradas en un solo anillo, mostrar `Siguiente: completo` con el anillo alto vacío y permitir intentar finalizar una sesión cuyo solape y grafo multivista estaban bloqueados.

## Alcance ejecutado

- admisión fail-closed antes de contabilizar una fotografía como aceptada;
- máximo de dos vistas aceptadas por combinación `anillo + sector`;
- separación angular mínima de 6° para una segunda vista del mismo sector;
- rechazo de borde uniforme dominante compatible con lente parcialmente obstruida;
- rechazo conservador de encuadre central sin detalle suficiente;
- exposición en pantalla de obstrucción y detalle central;
- cambio automático inicial de `ALTURA: EJE` a `ALTURA: ALTA` al completar el primer anillo;
- guía explícita para completar anillos, alcanzar 30 vistas distintas y ejecutar solape;
- botón de solape bloqueado hasta completar ambos anillos;
- finalización bloqueada mientras falten cobertura, cantidad o solape aprobado;
- salida sin finalizar conserva la sesión abierta para continuarla después;
- protección redundante en `CaptureStore.finishSession`.

## Cambios y decisiones

1. Se agregó `GuidedCaptureAdmissionCore` como núcleo Java puro y determinista.
2. `ImageQualityAnalyzer` calcula `borderObstructionScore` y `centerDetailRatio` sobre la grilla de luminancia reducida.
3. `CaptureStore` aplica la decisión de diversidad dentro de la misma transacción SQLite que persiste el frame.
4. Las fotografías rechazadas se conservan como evidencia, pero no aumentan cobertura ni cantidad aceptada.
5. No se implementó reconocimiento semántico de poleas. El gate central evalúa detalle y encuadre, no afirma detectar el objeto.
6. La sesión existente puede continuar: las fotografías antiguas permanecen intactas y el selector balanceado sigue limitando la reconstrucción a dos vistas por celda.
7. La calificación `device-alpha` se desplaza a ITER-022 para priorizar la falla observada en terreno.

## Evidencia y validación

La prueba entregada mostró:

- `Eje 12/12` y `alta 0/12`;
- 137 fotografías aceptadas y 0 rechazadas;
- 24 seleccionadas y 113 descartadas;
- un único par utilizable;
- tracks y nube global vacíos;
- grafo de poses desconectado;
- repetición visual reportada en 100 %;
- una toma con obstrucción visible por dedo.

Validación automatizada:

- `GuidedCaptureAdmissionV62Test`: **APROBADO**;
- repetición con menos de 6°: **BLOQUEADA**;
- tercera vista de una misma celda: **BLOQUEADA**;
- borde uniforme dominante: **BLOQUEADO**;
- guía EJE → ALTA → vistas adicionales → solape: **APROBADA**;
- workflow de producto run `#792`: **success**;
- 38 gates Java: **APROBADOS**;
- Gradle: **APROBADO**;
- cierre OCCT con 25 bibliotecas nativas: **APROBADO**;
- artefacto alpha42: **PUBLICADO**.

## Resultados

- la app ya no puede acumular indefinidamente tomas aceptadas del mismo sector;
- la cobertura de un anillo no se presenta como recorrido completo;
- el usuario recibe una instrucción concreta para el segundo anillo;
- el solape no puede ejecutarse prematuramente desde el botón principal;
- una sesión bloqueada no puede marcarse `CAPTURED`;
- la evidencia de rechazo permanece auditable;
- STEP/OCCT y todas las capacidades acumuladas permanecen integradas.

## Fallos, riesgos y deuda

- los umbrales de obstrucción y detalle central fueron validados sintéticamente y requieren ajuste con más capturas reales;
- el gate no identifica semánticamente una polea ni sustituye un detector de objeto;
- fotografías aceptadas por versiones anteriores no se reclasifican retroactivamente;
- una sesión antigua con exceso de fotografías puede continuar, pero conviene crear una sesión nueva para comparar el comportamiento limpio;
- la reconstrucción todavía puede bloquearse en superficies repetitivas aun con captura guiada correcta;
- Samsung A15 y Honor X5C no tienen todavía tres ejecuciones calificantes;
- no existe validación metrológica trazable.

## Estado del roadmap

- **Avance integral:** 98 %.
- **Madurez funcional alpha:** 99 %.
- **Preparación industrial/metrológica:** 27 %.

El aumento industrial es limitado: existe evidencia de uso real y una remediación directa, pero no una campaña controlada con patrón, repetibilidad o instrumentos trazables.

## Siguiente iteración obligatoria

### ITER-022 — Calificación device-alpha con continuidad de clave

Consumir generaciones automáticas con integridad y firma válidas, contar ejecuciones calificantes por modelo, verificar continuidad o rotación explícita de clave y mantener la calificación de dispositivo separada de la validación metrológica.

## Criterios de entrada y salida

### Entrada

- conservar los 38 gates acumulados;
- mantener admisión guiada y finalización fail-closed;
- mantener firma Ed25519 local, journal e integridad SHA-256;
- conservar `corporateIdentity=false`;
- no reclasificar automáticamente evidencia histórica;
- mantener el PR en borrador.

### Salida

- ingesta de generaciones firmadas implementada;
- continuidad y rotación de clave auditables;
- evidencia manual excluida del conteo;
- tres ejecuciones automáticas por dispositivo exigidas;
- nueva alpha compilada;
- CI de producto e historial exitosos;
- ITER-022 archivada y siguiente etapa declarada.
