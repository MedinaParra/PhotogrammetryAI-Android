# ITER-002 — Auditoría Drive de código, OT, planos y cotas

## Identificación

- **Fecha de inicio:** 2026-07-21
- **Fecha de cierre:** 2026-07-21
- **Rama:** `agent/photogrammetry-validation-alpha20`
- **PR:** `#8`
- **Commit base:** `3b60aca1597ed9223c4f5267e17ad39d427f5266`
- **Commit de cierre:** cabeza final del PR #8 después de incorporar el sistema documental
- **Versión de aplicación:** `0.18.0-alpha20` — sin cambio de APK en esta iteración
- **Fases del roadmap:** `R0`, `R1`, `R2`, preparación de `R3`
- **Responsable técnico:** MedinaParra / asistencia de ingeniería

## Objetivo

Auditar exclusivamente los planos y documentos técnicos disponibles en Google Drive para comprobar la relación entre código de material, OT, plano, revisión y cotas. Definir una regla dimensional explícita de validación por radio y determinar si la base histórica actual puede aprobar identificaciones automáticas.

## Alcance ejecutado

### Incluido

- revisión de la carpeta `PLANOS SKM DESDE 19-05-2023` y familias relacionadas;
- relación entre códigos de material y múltiples OT;
- inspección de planos dimensionales disponibles;
- comparación con `DriveQualityKnowledgeSeed` de la rama de producto;
- identificación de cotas invariantes y cotas particulares por OT;
- definición de tolerancia máxima de 30 mm en radio;
- clasificación preliminar por familia en `REVIEW` o `BLOCKED`;
- diseño de un modelo futuro por OT, plano y revisión.

### Excluido

- modificación de la base Java/SQLite;
- extracción masiva automática de todos los archivos de Drive;
- medición física de poleas;
- prueba fotogramétrica en teléfono;
- nueva compilación APK;
- cambio del algoritmo de identificación.

## Cambios y decisiones

### Código y arquitectura

No se modificó todavía el motor de datos. La decisión arquitectónica es reemplazar la representación plana por una jerarquía explícita:

```text
MaterialFamily -> WorkOrder -> Drawing -> DrawingRevision -> DimensionEvidence
```

Las dimensiones no deben almacenarse como una propiedad única de la familia cuando provienen de OT o revisiones diferentes.

Se definieron dos superficies incompatibles entre sí:

- `BARE_SHELL`: manto metálico sin revestimiento;
- `OUTER_LAGGING`: superficie exterior visible con caucho/cerámica.

La puerta primaria será:

```text
error_radio_mm = abs(radio_observado - radio_referencia)
MATCH solo cuando error_radio_mm <= 30 y no existen contradicciones críticas
```

### Datos y evidencia

#### Código `10415863`

OT confirmadas: `OT-262`, `OT-1702`.

Referencia histórica OT-262:

- Ø manto: 1400 mm;
- largo manto: 1520 mm;
- centros soportes: 2080 mm;
- centros rodamientos: 2030 mm;
- largo eje: 2355 mm;
- revestimiento: 22 mm.

Plano OT-1702:

- Ø manto: 1400 mm;
- Ø exterior: 1440 mm;
- largo manto: 1520 mm;
- centros soportes: 2100 mm;
- centros rodamientos: 2054 mm;
- largo eje: 2388 mm.

Decisión: identidad de familia y radio coherentes, pero las cotas axiales deben conservarse por OT. Estado: `REVIEW` hasta migración.

#### Código `10415860`

OT confirmadas: `OT-243`, `OT-470`, `OT-471`, `OT-1645`.

Referencia histórica OT-243:

- Ø manto: 800 mm;
- largo manto: 1520 mm;
- centros soportes: 2087 mm;
- centros rodamientos: 2067 mm;
- largo eje: 2288 mm;
- revestimiento: 20 mm.

Plano OT-1645:

- Ø manto: 800 mm;
- Ø exterior aproximado: 840,5 mm;
- largo manto: 1520 mm;
- centros soportes: 2108 mm;
- centros rodamientos: 2084 mm;
- largo eje: 2292 mm;
- zona rodamiento: Ø220 mm;
- zona fijación: Ø260 mm.

Decisión: familia dimensionalmente coherente, pero los centros y el eje siguen siendo evidencia por OT. Estado: `REVIEW` hasta migración.

#### Código `4162054`

OT confirmadas: `OT-651`, `OT-847`, `OT-865`.

Plano OT-651:

- Ø manto: 609,60 mm;
- Ø exterior: 673,10 mm;
- cara: 1524 mm;
- largo del cuerpo: 2032 mm;
- largo del eje: 3051,18 mm;
- zona rodamiento: Ø150,81 mm;
- zona fijación: Ø170 mm.

Decisión: la semilla contiene varias cotas del eje, pero omite los radios del manto y exterior. Estado: `BLOCKED` hasta cargarlos.

#### Código `4196111`

OT confirmadas: `OT-867`, `OT-868`, `OT-920`, `OT-954`, `OT-963`, `OT-1047`; existe además evidencia WIP asociada a `OT-1043`.

Decisión: el historial OT es útil, pero no se confirmó un plano dimensional suficiente dentro de esta iteración. La carpeta OT-954 menciona también `4162038`, lo que se registra como conflicto/alias pendiente y no como equivalencia. Estado: `BLOCKED`.

#### Código `4196149`

OT confirmada: `OT-781`.

Plano original en pulgadas:

- diámetro: 30 in = 762 mm;
- radio exterior: 381 mm;
- cara: 90 in = 2286 mm.

Decisión: conservar unidad original y valor convertido. Estado: `BLOCKED` hasta incorporar evidencia dimensional a la base.

#### Código `10510386`

OT confirmada: `OT-270`.

El informe disponible contiene componentes y cotas del eje, pero no consigna largo ni diámetro del manto.

Decisión: no inferir el radio. Estado: `BLOCKED`.

#### Código `1462827`

OT con planos: `OT-1570`, `OT-1691`. Se detectaron además documentos de `OT-1632` y `OT-1712` que aún no están incorporados en la semilla.

Plano OT-1570:

- Ø manto: 1016 mm;
- Ø exterior: 1046 mm;
- largo cuerpo: 2100 mm;
- centros soportes: 2630 mm;
- centros rodamientos: 2630 mm;
- largo eje: 2870 mm;
- zona rodamiento: Ø266,70 mm;
- zona fijación: Ø280 mm.

Decisión: cargar dimensiones y ampliar historial OT. Estado: `BLOCKED` hasta migración.

#### Código `4162045`

La semilla solo conserva `OT-343` y el alias genérico `Polea de Cola`. Drive muestra el mismo código en OT-366, OT-425, OT-445, OT-1436, OT-1512 y nombres de equipo distintos, incluyendo Polea SNAP 145CV012 y referencias a otras posiciones.

Un plano histórico contiene:

- centros soportes: 2718 mm;
- centros rodamientos: 2708 mm;
- largo total: 2962 mm.

Decisión: no aceptar identificación automática hasta determinar si existe reutilización de código, error documental o códigos de componente mezclados. Estado: `BLOCKED` crítico.

## Evidencia y validación

| Prueba o revisión | Entorno | Resultado | Evidencia |
|---|---|---|---|
| Relación código–OT | Google Drive + semilla Java | PARCIALMENTE APROBADA | planos e informes asociados por código/OT |
| Comparación dimensional `10415863` | planos OT-262/1702 | APROBADA PARA FAMILIA, NO PARA COTA ÚNICA | Ø1400 y largo 1520 coinciden; centros/eje cambian |
| Comparación dimensional `10415860` | planos OT-243/1645 | APROBADA PARA FAMILIA, NO PARA COTA ÚNICA | Ø800 y largo 1520 coinciden; centros/eje cambian |
| Radio `4162054` | plano OT-651 | EVIDENCIA ENCONTRADA, BASE INCOMPLETA | Ø609,60/Ø673,10 |
| Radio `4196149` | plano OT-781 | EVIDENCIA ENCONTRADA, BASE INCOMPLETA | Ø30 in |
| Radio `1462827` | plano OT-1570 | EVIDENCIA ENCONTRADA, BASE INCOMPLETA | Ø1016/Ø1046 |
| Consistencia `4162045` | múltiples carpetas/planos | FALLA / CONFLICTO | mismo código asociado a descripciones distintas |
| Compilación Android | no ejecutada | NO EJECUTADA | iteración documental y de auditoría |
| Prueba en dispositivo | no ejecutada | NO EJECUTADA | fuera de alcance |
| Medición física | no ejecutada | NO EJECUTADA | fuera de alcance |

## Resultados

- La relación código–OT debe ser uno-a-muchos.
- La base actual no está validada para identificación automática industrial.
- `10415863` y `10415860` son coherentes a nivel de familia, pero requieren datos por OT/revisión.
- `4162054`, `4196149` y `1462827` poseen planos suficientes para cargar radios.
- `4196111` y `10510386` no tienen aún evidencia dimensional suficiente en la base revisada.
- `4162045` queda bloqueado por conflicto de identidad.
- La tolerancia solicitada de ±30 mm en radio se adopta como puerta dura, no como única condición de coincidencia.
- Se definió el siguiente trabajo como migración de modelo y motor de validación, no como expansión indiscriminada de aliases.

## Fallos, riesgos y deuda

| ID | Severidad | Descripción | Mitigación | Estado |
|---|---|---|---|---|
| DATA-001 | crítica | Dimensiones de distintas OT pueden aparecer como una sola verdad de familia | migrar a OT/plano/revisión | abierto |
| DATA-002 | crítica | `4162045` presenta asociaciones incompatibles | bloquear y auditar fuente por fuente | abierto |
| DATA-003 | alta | Confusión posible entre radio, diámetro y espesor radial | tipos explícitos y pruebas 29/30/31 mm | abierto |
| DATA-004 | alta | Manto desnudo y revestimiento exterior se mezclan | `surfaceKind` obligatorio | abierto |
| DATA-005 | alta | Planos en pulgadas pueden interpretarse como milímetros | conservar unidad original y convertir con pruebas | abierto |
| DATA-006 | media | `4196111` contiene posible alias `4162038` | registrar conflicto, no fusionar automáticamente | abierto |
| DATA-007 | alta | Faltan SHA-256 de fuentes Drive en la semilla | incorporar hash al importar evidencia | abierto |
| VALID-001 | crítica | No existe campaña física de radio/repetibilidad | ejecutar fases R6/R8 antes de uso industrial | abierto |

## Estado del roadmap

| Fase | Antes | Después | Puerta de salida |
|---|---|---|---|
| R0 Gobierno | parcial | definida documentalmente | abierta: falta implementación |
| R1 Modelo dimensional | no iniciado | arquitectura especificada | abierta |
| R2 Saneamiento | inicial | ocho familias clasificadas | abierta |
| R3 Identificación | concepto parcial | regla de radio definida | abierta |
| R4–R8 | pendiente/parcial según área | sin cambio | abierta |

## Siguiente iteración obligatoria

### ITER-003 — Modelo OT/plano/revisión y puerta de radio

Objetivos concretos:

1. crear entidades y persistencia por OT, plano, revisión, dimensión y superficie;
2. migrar la semilla sin pérdida ni sobrescritura;
3. cargar radios y cotas auditadas de `10415863`, `10415860`, `4162054`, `4196149` y `1462827`;
4. mantener `4162045`, `4196111` y `10510386` bloqueados;
5. implementar `error_radio_mm <= 30` como condición necesaria de `MATCH`;
6. agregar pruebas de frontera, unidades, revisiones y revestimientos;
7. emitir explicación y fuentes para cada decisión.

## Criterios de entrada y salida

### Entrada

- partir de la cabeza actual de PR #8;
- conservar la APK alpha20 y las pruebas existentes;
- usar los planos auditados como evidencia inicial;
- no incorporar nuevas familias hasta estabilizar el modelo;
- aceptar que la base actual es experimental y requiere migración.

### Salida

- migración idempotente probada;
- datos por OT/revisión consultables;
- pruebas de 29, 30 y 31 mm exitosas;
- pruebas de pulgadas/milímetros exitosas;
- manto y revestimiento tratados como superficies distintas;
- conflictos producen `REVIEW` o `BLOCKED`;
- CI exitoso;
- historial de ITER-003 creado y `CURRENT.md` actualizado con ITER-004.