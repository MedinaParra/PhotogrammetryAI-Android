# ITER-NNN — Título de la iteración

## Identificación

- **Fecha de inicio:** YYYY-MM-DD
- **Fecha de cierre:** YYYY-MM-DD o `ABIERTA`
- **Rama:** `agent/...`
- **PR:** `#...`
- **Commit base:** `<sha>`
- **Commit de cierre:** `<sha o PENDIENTE>`
- **Versión de aplicación:** `<versión o SIN CAMBIO>`
- **Fases del roadmap:** `R0`, `R1`, etc.
- **Responsable técnico:** `<nombre o equipo>`

## Objetivo

Describir un objetivo verificable. Evitar formulaciones como “mejorar la app” sin una condición medible.

## Alcance ejecutado

### Incluido

- ...

### Excluido

- ...

## Cambios y decisiones

### Código y arquitectura

- archivo/módulo;
- decisión tomada;
- razón técnica;
- alternativa descartada.

### Datos y evidencia

- código de material;
- OT;
- plano/revisión;
- dimensiones incorporadas o bloqueadas;
- conflictos detectados.

## Evidencia y validación

| Prueba o revisión | Entorno | Resultado | Evidencia |
|---|---|---|---|
| Prueba sintética | GitHub Actions/local | PASS/FAIL/NO EJECUTADA | run/log |
| Compilación Android | GitHub Actions/local | PASS/FAIL/NO EJECUTADA | run/artifact |
| Prueba en dispositivo | modelo/Android | PASS/FAIL/NO EJECUTADA | sesión/log |
| Verificación STEP | dispositivo/CI | PASS/FAIL/NO EJECUTADA | archivo/hash |
| Medición física | instrumento | PASS/FAIL/NO EJECUTADA | protocolo |

## Resultados

- métricas obtenidas;
- archivos generados;
- comportamiento confirmado;
- comportamiento no confirmado;
- cambios de estado `MATCH`, `REVIEW` o `BLOCKED`.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Mitigación | Estado |
|---|---|---|---|---|
| RISK-... | crítica/alta/media/baja | ... | ... | abierto/cerrado |

No omitir fallos del CI, advertencias JNI, diferencias dimensionales ni datos contradictorios.

## Estado del roadmap

| Fase | Antes | Después | Puerta de salida |
|---|---|---|---|
| R0–R8 | pendiente/parcial/completa | pendiente/parcial/completa | abierta/cerrada |

## Siguiente iteración obligatoria

### ITER-NNN — Nombre

Objetivos concretos:

1. ...
2. ...
3. ...

## Criterios de entrada y salida

### Entrada

- evidencia o dependencias requeridas;
- rama/commit de partida;
- riesgos que deben aceptarse.

### Salida

- pruebas que deben aprobar;
- datos que deben quedar versionados;
- artefactos esperados;
- decisión explícita sobre lo que continuará después.