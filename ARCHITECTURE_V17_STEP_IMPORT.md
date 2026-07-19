# Arquitectura v0.17 — importación STEP orientada a tracking

## Objetivo

Convertir un archivo STEP de soporte o componente industrial en una referencia geométrica ligera que pueda utilizarse durante el tracking multicámara, sin ejecutar OpenCASCADE dentro del ciclo de cada fotograma.

## Flujo

```text
STEP local
  ↓
NativeStepReferenceImporter
  ↓ JNI
OcctStepFeatureExtractor
  ↓
cilindros + planos exactos
  ↓ protocolo PGAI_STEP_V1
StepReferenceImportMapper
  ↓
CadReferenceModel
  ↓
PulleyReconstructionCore
```

## Separación de responsabilidades

### Java puro

- Valida que el archivo exista, sea legible y tenga extensión `.step` o `.stp`.
- Calcula SHA-256 para identificación y deduplicación.
- Decodifica el protocolo nativo sin bibliotecas JSON.
- Selecciona el cilindro que representa el alojamiento principal.
- Construye `CadReferenceModel` en milímetros.
- Devuelve errores tipados; no permite que una excepción JNI alcance la GUI.

### C++/JNI

- Abre el archivo con `STEPControl_Reader`.
- Transfiere sus raíces a un `TopoDS_Shape`.
- Recorre las caras mediante `TopExp_Explorer`.
- Reconoce superficies `GeomAbs_Cylinder` y `GeomAbs_Plane`.
- Extrae origen, dirección, radio y extensión aproximada.
- Fusiona caras duplicadas pertenecientes a la misma superficie geométrica.
- Funciona en modo degradado: si OCCT no está enlazado, devuelve `OCCT_MISSING`.

## Selección del alojamiento primario

El operador o el catálogo pueden entregar pistas en `ImportRequest.hints`:

| Clave | Uso |
|---|---|
| `referenceId` | Identificador estable del modelo |
| `primaryBoreFeatureId` | Fuerza un cilindro específico |
| `expectedBoreDiameterMm` | Selecciona el cilindro más cercano al diámetro nominal |
| `minimumBoreDiameterMm` | Descarta cilindros menores |
| `maximumBoreDiameterMm` | Descarta cilindros mayores |

Orden de prioridad:

1. `primaryBoreFeatureId` explícito.
2. Cercanía a `expectedBoreDiameterMm`.
3. Si no existen datos nominales, mayor combinación radio × extensión × peso de tracking.

En una versión posterior la interfaz deberá mostrar todos los cilindros candidatos para que el operador confirme visualmente el alojamiento correcto.

## Protocolo JNI

`PGAI_STEP_V1` es un protocolo tabulado y versionado:

```text
PGAI_STEP_V1
STATUS OK <error> <mensaje> <archivo> <mesh-key>
FEATURE <id> <nombre> CYLINDER ox oy oz dx dy dz radio extensión peso
```

Los campos de texto usan codificación porcentual UTF-8. El protocolo evita acoplar el core estructural a Gson, Moshi u otra dependencia Android.

## Compilación nativa

### 1. OpenCASCADE ARM64

```bash
export ANDROID_NDK_HOME=/ruta/al/android-ndk
scripts/build_occt_android_for_cadcore.sh /ruta/a/opencascade
```

Salida:

```text
third-party/opencascade/arm64-v8a/
├── include/opencascade/
└── lib/*.so
```

### 2. Cadcore JNI

```bash
scripts/build_cadcore_android.sh
```

Salida destinada al empaquetado Android:

```text
PhotogrammetryAI/app/src/main/jniLibs/arm64-v8a/
├── libphotogrammetry_cadcore.so
├── libc++_shared.so
└── libTK*.so
```

## Validación JVM

```bash
bash tools/run_core_foundation_v16_test.sh
bash tools/run_step_import_v17_test.sh
```

La prueba v0.17 usa un puente nativo simulado y comprueba:

- selección por diámetro nominal;
- selección explícita por ID de rasgo;
- persistencia del SHA-256;
- conservación de todos los rasgos CAD;
- nombres UTF-8;
- rechazo de archivos inexistentes;
- fallo seguro cuando no existe un cilindro de alojamiento.

## Estado actual

Implementado:

- contratos Java del importador;
- protocolo JNI;
- extractor OCCT de cilindros y planos;
- selección del alojamiento;
- scripts de compilación ARM64;
- pruebas JVM;
- workflow de CI del core.

Pendiente antes de usar un STEP real en el Galaxy A26:

1. Enlazar el `CMakeLists.txt` del cadcore desde Gradle.
2. Confirmar la variante exacta del proyecto Android y su archivo de build.
3. Compilar OCCT y empaquetar todas las dependencias `.so`.
4. Ejecutar una prueba instrumental en ARM64.
5. Añadir una pieza STEP de prueba con dimensiones conocidas.
6. Generar y almacenar una malla LOD además de los rasgos semánticos.
7. Cambiar a `STEPCAFControl_Reader` cuando necesitemos nombres y estructura de ensamblaje.

## Regla de rendimiento

OpenCASCADE se utiliza una vez al importar o actualizar el archivo. Durante el tracking en tiempo real se consumen solamente `CadReferenceModel`, la malla LOD y muestras de bordes preprocesadas.
