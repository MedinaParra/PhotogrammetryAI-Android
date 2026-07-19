# Arquitectura v0.22 — identidad por código de material, historial OT y escaneo visual

## 1. Hallazgo principal

El archivo de Calidad demuestra que las tres entidades siguientes no son equivalentes:

1. **Código de material / Stock Code / Código SAP**: identifica una familia técnica o diseño reutilizado.
2. **OT**: identifica una intervención particular sobre una polea recibida, evaluada, reparada, armada o preservada.
3. **Posición/equipo/descripción**: identifica una instancia, ubicación o alias operacional.

Un mismo código puede aparecer en varias OTs, años, posiciones y etapas documentales. Por lo tanto, la base no debe usar la OT como identidad geométrica de la polea.

Ejemplos encontrados en Drive:

- `10415863`: OT-262 y OT-1702, además de varias descripciones en WIP.
- `10415860`: OT-243, OT-470, OT-471 y OT-1645.
- `4162054`: OT-651, OT-847 y OT-865.
- `4196111`: OT-867, OT-868, OT-920, OT-954, OT-963, OT-1043 y OT-1047.
- `1462827`: OT-1570 y OT-1691.

## 2. Regla obligatoria de identificación

Toda consulta de identificación exige:

```text
largo_manto_mm > 0
```

El largo del manto actúa como control de consistencia incluso cuando se conoce el código de material.

### Resultado según la comparación

| Código/OT | Largo histórico | Resultado |
|---|---:|---|
| exacto | coincide | `DIRECT_FAMILY_MATCH` |
| exacto | cercano | familia probable; verificar tolerancia/levantamiento |
| exacto | incompatible | `VERIFY_VARIANT`; no superponer automáticamente |
| exacto | no existe | `HISTORICAL_FAMILY_NO_LENGTH`; largo de terreno manda |
| desconocido | coincide con geometría/modelos | `VISUAL_ONLY` |
| evidencia insuficiente | — | `NO_MATCH` |

Un código exacto nunca invalida una contradicción métrica.

## 3. Modelo de datos

### 3.1 MaterialFamily

```text
material_code (PK lógico)
aliases[]
clients[]
role_hints[]
dimensions[]
components[]
events[]
sources[]
```

### 3.2 InterventionEvent

```text
ot
year
phase
client
component_description
purchase_order
source_ids[]
notes[]
```

Fases soportadas:

```text
RECEIPT
EVALUATION
FINDINGS
REPAIR
ASSEMBLY
PRESERVATION
DRAWING
WIP
OTHER
```

### 3.3 DimensionEvidence

Cada cota conserva:

```text
kind
value_mm
semantic_label
source_id
confidence
```

Tipos iniciales:

```text
SHELL_LENGTH
SHELL_DIAMETER
SHELL_THICKNESS
LAGGING_THICKNESS
BELT_FACE_WIDTH
SHAFT_TOTAL_LENGTH
BEARING_CENTRE_DISTANCE
SUPPORT_CENTRE_DISTANCE
SHAFT_BEARING_DIAMETER
SHAFT_LOCKING_DIAMETER
SUPPORT_BORE_DIAMETER
```

Las cotas sin etiqueta inequívoca no se convierten automáticamente en metrología. Por ejemplo, un `2032` aislado en un plano puede almacenarse como longitud candidata con menor confianza hasta confirmar su semántica.

### 3.4 ComponentEvidence

```text
kind
manufacturer
model
condition
note
source_id
confidence
```

Estados:

```text
REUSE
REPAIR
REPLACE
REJECT
UNKNOWN
```

El estado pertenece a una intervención y no debe convertirse en una propiedad permanente de la familia. El modelo de componente sí puede usarse como prior recurrente.

### 3.5 SourceDocument

```text
id
title
uri
phase
ot
material_code
document_date
content_hash (fase de persistencia)
revision_key (fase de ingesta)
```

## 4. Índices bidireccionales

La base mantiene:

```text
material_code -> MaterialFamily -> OTs/documentos/cotas/componentes
OT -> material_codes -> MaterialFamily
```

Una OT puede relacionarse con más de un código si el trabajo incluye varios componentes. El índice usa conjuntos, no una relación uno-a-uno rígida.

## 5. Evidencia y precedencia

Orden recomendado para decidir geometría:

1. Medición actual confirmada por operador.
2. Ajuste fotogramétrico actual con buena confianza.
3. STEP/plano exacto del mismo código y variante validada.
4. Informe dimensional de una OT histórica del mismo código.
5. Plano histórico con semántica parcialmente confirmada.
6. Caso similar por geometría/componentes.
7. Regla general de poleas.

Para mantenimiento/estado:

1. Informe de evaluación de la OT actual.
2. Hallazgo/END de la OT actual.
3. Informe final de la OT actual.
4. Historial de fallas del mismo código como prior, nunca como estado actual.

## 6. Flujo de identificación en terreno

```text
Operador ingresa largo de manto
          +
Código material o OT (si está disponible)
          +
Escaneo de 3 celulares
          ↓
Normalización de identidad
          ↓
Consulta código ↔ OT
          ↓
Comparación largo obligatorio
          ↓
Comparación diámetro, proporciones y modelos visibles
          ↓
Ranking de familias
          ↓
Carga del STEP/plano exacto o caso más cercano
          ↓
Alineación por eje de soportes + manto medido
          ↓
Overlay con geometría medida e inferida diferenciadas
```

## 7. Integración con el core existente

### Entradas del escaneo

```text
PulleyCylinder:
- centreWorldMm
- axisWorld
- diameterMm
- lengthMm
- confidence
```

`lengthMm` alimenta directamente la consulta v0.22.

### Salidas de v0.22

```text
Candidate:
- material family
- score
- length status
- historical shell length
- action
- reasons
- warnings
```

### Uso por el motor de superposición

- `DIRECT_FAMILY_MATCH`: habilita búsqueda del STEP exacto.
- `VERIFY_VARIANT`: bloquea overlay metrológico hasta confirmación.
- `HISTORICAL_FAMILY_NO_LENGTH`: conserva componentes/OTs, pero reconstruye manto con la medida actual.
- `VISUAL_ONLY`: STEP parecido solo como ayuda visual.
- `NO_MATCH`: reconstrucción paramétrica desde cero.

## 8. Semilla v0.22

La primera semilla contiene ocho familias verificadas:

```text
10415863
10415860
4162054
4196111
4196149
10510386
1462827
4162045
```

No representa todavía todo el archivo de Calidad. Sirve para validar el modelo y el algoritmo antes de automatizar la ingesta masiva.

## 9. Roadmap mejorado

### Fase A — Ingesta y catálogo maestro

Objetivo: convertir Drive en un índice consultable, sin modificar los documentos originales.

1. Inventariar carpetas `Control de Calidad SKM`, `Informes Por OT`, WIP y carpetas por código.
2. Clasificar PDF/DOCX/XLSX/planos por fase.
3. Calcular hash y detectar duplicados/revisiones.
4. Extraer código, OT, cliente, componente, OC y fecha.
5. Crear cola de documentos con campos ambiguos.

**Puerta de salida:** 95 % de documentos piloto con código u OT correctamente indexados.

### Fase B — Grafo código↔OT

1. Consolidar alias por código.
2. Relacionar todas las OTs históricas.
3. Extraer cotas etiquetadas.
4. Extraer modelos de soportes, rodamientos, manguitos, sellos, acoplamientos y backstops.
5. Separar estado por OT de configuración recurrente.

**Puerta de salida:** consulta por código y OT devuelve historial trazable y fuentes.

### Fase C — Persistencia móvil

1. Room/SQLite con tablas normalizadas.
2. Paquete de conocimiento versionado y actualizable.
3. Índice de texto local para códigos, OTs, posiciones y modelos.
4. Sincronización diferencial desde un paquete exportado por el proceso de ingesta.
5. La app de terreno no necesita acceso completo a Drive.

**Puerta de salida:** identificación local sin conexión en menos de 200 ms para miles de familias.

### Fase D — Fusión con visión

1. Largo de manto obligatorio.
2. Diámetro, eje y centros obtenidos por few-view/STEP.
3. Clasificación visual de motriz/cola/deflectora/tensora.
4. OCR opcional de placas/códigos visibles.
5. Modelos de soporte y patrones de lagging como evidencia secundaria.

**Puerta de salida:** top-3 contiene la familia correcta en ≥95 % del conjunto validado.

### Fase E — Superposición CAD

1. Registrar ancla de eje y origen para cada STEP.
2. Ajuste rígido cuando código y largo coinciden.
3. Reconstrucción paramétrica cuando falta STEP.
4. Preview escalado solo con advertencia.
5. Mostrar measured/inferred/historical/conflict con estilos distintos.

**Puerta de salida:** ninguna geometría escalada se presenta como cota real.

### Fase F — Ingeniería Viva controlada

1. El operador confirma código, OT y variante.
2. La sesión guarda diferencias entre predicción y realidad.
3. Solo sesiones verificadas actualizan estadísticas.
4. Los documentos históricos permanecen inmutables.
5. Nuevas reglas se versionan y pueden revertirse.

**Puerta de salida:** aprendizaje acumulativo auditable, sin contaminación automática.

## 10. Riesgos y controles

### Un código usado en varias geometrías

Control: largo obligatorio, variante, posición, diámetro y modelos recurrentes.

### Revisiones duplicadas

Control: hash, número de revisión y preferencia por revisión final sin borrar anteriores.

### Informes con campos vacíos

Control: conservar `NO_HISTORY`; no completar con estimaciones silenciosas.

### OCR o extracción incorrecta

Control: confianza por evidencia, fuente navegable y cola de revisión humana.

### Confundir falla histórica con falla actual

Control: condición ligada a `InterventionEvent`, no a `MaterialFamily` como verdad permanente.

### STEP similar pero no exacto

Control: `VERIFY_VARIANT` o `VISUAL_ONLY`; medición actual siempre prevalece.

## 11. Prueba

```bash
bash tools/run_material_history_v22_test.sh
```

La prueba verifica:

- código → OTs;
- OT → código;
- normalización SC/SAP/Código de Material;
- largo obligatorio coincidente;
- conflicto de largo;
- familia sin largo histórico;
- diferenciación visual entre dos códigos con largo igual;
- extracción de campos y separadores numéricos desde texto de informe.
