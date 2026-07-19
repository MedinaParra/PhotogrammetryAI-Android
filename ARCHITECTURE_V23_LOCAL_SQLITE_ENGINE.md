# v0.23 — Motor local SQLite de conocimiento de poleas

## Objetivo

Convertir la experiencia acumulada en Calidad en un motor local, auditable y utilizable sin conexión durante el escaneo. La GUI, CameraX y el renderizado 3D quedan fuera de esta fase.

## Principios no negociables

1. **SQLite local es la fuente operativa en terreno.** Google Drive se utiliza para construir o actualizar paquetes de conocimiento, no para responder cada consulta durante un escaneo.
2. **Código de material/SAP/Stock Code identifica una familia técnica.**
3. **OT identifica una intervención.** No reemplaza la identidad de la familia.
4. **El largo del manto es obligatorio.** Ninguna identificación se ejecuta sin un largo positivo.
5. **La evidencia mantiene su fuente.** Cotas, componentes y decisiones no se guardan como datos anónimos.
6. **Una coincidencia histórica no autoriza automáticamente una superposición metrológica.** Los conflictos de largo obligan a verificar variante.
7. **El sistema no aprende de sí mismo.** Solo una confirmación humana explícita puede convertir una sesión en candidata a experiencia nueva.
8. **Las migraciones son explícitas y no destructivas.** La aplicación no borra la base automáticamente cuando cambia la versión.

## Capas

### 1. Dominio

Paquete:

```text
core/materialhistory
```

Responsabilidades:

- familias de material;
- alias y roles;
- OTs e intervenciones;
- documentos fuente;
- evidencia dimensional;
- evidencia de componentes;
- ranking de identificación;
- auditoría y confirmación.

El dominio no importa clases Android ni ejecuta SQL.

### 2. Persistencia

Paquete:

```text
core/materialhistory/sqlite
```

Implementación productiva:

- `AndroidPulleyKnowledgeOpenHelper`;
- `AndroidSqliteMaterialKnowledgeStore`;
- `SqlitePulleyKnowledgeSchema`.

Base:

```text
pulley_knowledge.db
```

Versión inicial:

```text
1
```

### 3. Aplicación

Entrada principal:

```text
LocalPulleyKnowledgeEngine
```

Flujo:

```text
semilla o informe
        ↓
ingesta normalizada
        ↓
MaterialKnowledgeStore / SQLite
        ↓
PulleyIdentificationRequest
        ↓
ranking por código + OT + largo + diámetro + texto + componentes
        ↓
auditoría local
        ↓
confirmación humana opcional
```

## Esquema SQLite v1

### Identidad e historial

- `material_family`
- `family_alias`
- `family_client`
- `family_role_hint`
- `source_document`
- `intervention`
- `intervention_source`
- `intervention_note`

### Evidencia técnica

- `dimension_evidence`
- `component_evidence`

### Control de ingesta

- `ingestion_run`

### Identificación en terreno

- `identification_session`
- `identification_candidate`
- `knowledge_confirmation`

### Metadatos

- `schema_meta`

## Claves e índices principales

```text
material_family.material_code                       PRIMARY KEY
source_document.source_id                           PRIMARY KEY
intervention(material_code, OT, año, fase, ...)    UNIQUE
family_alias(material_code, alias)                  UNIQUE
dimension_evidence(material_code, tipo, valor, fuente) UNIQUE
component_evidence(material_code, modelo, estado, fuente) UNIQUE
identification_candidate(session_id, rank)          PRIMARY KEY
```

Índices prioritarios:

- material por OT;
- documento por código;
- documento por hash;
- cota por código, tipo y valor;
- componente por modelo;
- sesiones por fecha.

## Instalación inicial

```java
AndroidPulleyKnowledgeOpenHelper helper =
        new AndroidPulleyKnowledgeOpenHelper(context);

MaterialKnowledgeStore store =
        new AndroidSqliteMaterialKnowledgeStore(helper);

LocalPulleyKnowledgeEngine engine =
        new LocalPulleyKnowledgeEngine(store);

engine.initializeSeedIfEmpty();
```

La semilla solo se instala cuando no existe ninguna familia. Actualizar una semilla no debe borrar las auditorías de terreno.

## Ingesta de un informe

```java
QualityKnowledgeIngestionService.ReportInput input =
        new QualityKnowledgeIngestionService.ReportInput(
                titulo,
                uriDriveOArchivo,
                textoExtraido,
                fechaDocumento
        );

QualityKnowledgeIngestionService.Result result =
        engine.ingestQualityReport(input);
```

### Reglas actuales

- se calcula SHA-256 determinista del contenido;
- se extrae código, OT, año, cliente, componente, OC y cotas etiquetadas;
- un informe sin código queda rechazado;
- un informe con OT pero sin año conserva el vínculo de fuente, pero no crea una intervención fechada;
- reingresar el mismo documento no duplica fuentes ni cotas;
- una revisión distinta crea una fuente distinta y mantiene ambas evidencias.

## Identificación

Entrada recomendada:

```java
PulleyIdentificationRequest request =
        new PulleyIdentificationRequest(
                largoMantoMm,
                codigoIngresado,
                otIngresada,
                diametroEscaneadoMm,
                descripcionVisual,
                modelosObservados
        );

LocalPulleyKnowledgeEngine.IdentificationOutcome outcome =
        engine.identify(request, 5);
```

`PulleyIdentificationRequest` acepta formatos humanos:

```text
10415863
SC 10415863
SAP 10415863
Stock Code: 10415863
Código de Material: 10415863
Código SAP 10415863
```

Todos se transforman en:

```text
10415863
```

## Decisiones

### `DIRECT_FAMILY_MATCH`

Código u OT coincidente y largo compatible. Es candidato a alineación STEP, pero todavía requiere verificar calidad del ancla y del tracking.

### `VERIFY_VARIANT`

El código u OT coincide, pero el largo contradice la evidencia histórica. Se bloquea la superposición automática.

### `HISTORICAL_FAMILY_NO_LENGTH`

La familia existe, pero no hay largo histórico consolidado. La medición de terreno manda.

### `VISUAL_ONLY`

La similitud geométrica o de componentes es suficiente para sugerir una familia, pero no para uso metrológico.

### `NO_MATCH`

No hay candidato confiable. Se continúa con reconstrucción paramétrica y revisión manual.

## Auditoría

Cada identificación guarda:

- identificador de sesión;
- fecha;
- largo obligatorio;
- código y OT ingresados;
- diámetro medido;
- descripción;
- candidatos ordenados;
- puntaje;
- estado del largo;
- acción propuesta;
- razones y advertencias;
- selección;
- confirmación del operador.

La auditoría se preserva cuando se actualiza el paquete de conocimiento.

## Confirmación

```java
engine.confirm(sessionId, "Código SAP 10415863");
```

Confirmar no modifica automáticamente la familia histórica. Solo marca la sesión como evidencia humana candidata para una futura promoción controlada.

## Validación disponible

### SQL

El esquema v1 fue ejecutado contra SQLite en memoria, verificando creación de tablas, índices, restricciones y claves foráneas.

### Java puro

```bash
bash tools/run_local_knowledge_engine_v23_test.sh
```

La prueba verifica:

- instalación idempotente de la semilla;
- normalización SC/SAP;
- identificación directa;
- auditoría;
- confirmación humana;
- ingesta de una nueva OT;
- consulta OT → código;
- reingesta idempotente;
- cuarentena de informe sin código;
- codificación de razones y advertencias;
- presencia de tablas críticas del esquema.

GitHub Actions actualmente crea el job pero termina antes de ejecutar pasos y no entrega logs. Por ello la validación Java permanece pendiente de un runner operativo.

## Próxima fase recomendada

### v0.24 — Ingesta masiva y calidad de datos

1. inventariar recursivamente `Control de Calidad SKM` e `Informes Por OT`;
2. calcular hash por archivo y distinguir duplicado de revisión;
3. extraer texto en un proceso fuera del teléfono;
4. ingerir lotes y registrar aceptados, rechazados y ambiguos;
5. detectar códigos con largos incompatibles;
6. detectar OTs asociadas a más de un código;
7. generar un paquete SQLite firmado y versionado;
8. instalar el paquete localmente sin borrar auditorías.

### v0.25 — Unión con escaneo

1. alimentar `shellLengthMm` desde medición confirmada;
2. alimentar diámetro y eje desde few-view;
3. reconocer soportes, rodamientos y lagging;
4. restringir candidatos antes de cargar STEP;
5. entregar anclas geométricas al motor de superposición.
