# v0.16 — Core estructural para tracking STEP multicámara

## Objetivo de esta etapa

Construir primero un núcleo comprobable, sin GUI ni dependencias Android, que defina cómo se conectarán:

- referencias CAD extraídas desde STEP;
- poses de cámara obtenidas con targets;
- alineación modelo–imagen por cada celular;
- fusión del Galaxy A26 5G, HONOR X5c e Infinix Smart 9;
- eje común de los alojamientos y de la polea;
- estados de sesión, tracking y congelamiento.

La GUI, CameraX, ARCore, Nearby Connections y OpenCASCADE serán adaptadores externos. Ninguno debe contaminar las reglas geométricas del core.

## Principio arquitectónico

```text
OpenCASCADE / STEP              CameraX / targets / visión
         │                                  │
         ▼                                  ▼
CadReferenceModel                 DeviceObservation
         │                                  │
         └──────────────┬───────────────────┘
                        ▼
              PulleyReconstructionCore
                        │
                        ▼
        PulleyEstimate + eventos + snapshot
```

## Componentes incorporados

### `CoreMath`

- vector 3D inmutable;
- transformaciones rígidas SE(3);
- composición e inversión de poses;
- validación de matrices de rotación;
- unidades internas en milímetros.

### `CadReferenceModel`

Representación ligera generada una vez al importar un STEP:

- cilindro principal del alojamiento;
- planos de apoyo;
- círculos, ejes y puntos de referencia futuros;
- peso de cada rasgo para tracking;
- hash del STEP y clave de malla de visualización.

El `TopoDS_Shape` y la malla pesada no entran en el bucle de tiempo real.

### `CorePorts`

Contratos para implementar más adelante:

- `StepReferenceImporter`: adaptador JNI/OpenCASCADE;
- `CadReferenceRepository`: Room/archivos privados;
- `NanoClock`: tiempo monotónico;
- `EventSink`: telemetría, logs y ViewModel.

### `TrackingFrame`

Cada celular entrega una observación con:

- pose `worldFromCamera` obtenida mediante targets/ARCore;
- pose `cameraFromReference` obtenida al alinear el STEP con la imagen;
- rol del soporte izquierdo, derecho o no asignado;
- confianza de targets y del modelo;
- error de reproyección.

La pose global del soporte se obtiene mediante:

```text
worldFromReference = worldFromCamera × cameraFromReference
```

### `PulleyReconstructionCore`

- registra referencias CAD;
- inicia una sesión con la lista de celulares esperados;
- rechaza dispositivos, modelos o observaciones inválidas;
- conserva la observación más reciente de cada teléfono;
- transforma el eje exacto del alojamiento STEP al sistema mundial;
- fusiona varias observaciones ponderadas;
- usa ambos centros de soporte para obtener el eje común;
- produce confianza, estabilidad y distancia entre centros;
- permite congelar el resultado antes del refinamiento final.

## Estado actual y límites

Esta etapa todavía no:

- abre STEP directamente;
- extrae caras mediante OpenCASCADE;
- detecta targets;
- transmite datos entre celulares;
- alinea bordes CAD con imágenes;
- calcula diámetro o longitud del manto;
- renderiza realidad aumentada.

Esas funciones se conectarán sobre contratos estables, evitando rehacer la lógica central cuando se construya la GUI.

## Prueba

```bash
chmod +x tools/run_core_foundation_v16_test.sh
./tools/run_core_foundation_v16_test.sh
```

La prueba verifica:

1. composición correcta de poses;
2. fusión de tres celulares;
3. cálculo del eje entre soporte izquierdo y derecho;
4. distancia entre centros;
5. transición `SESSION_READY → TRACKING → FROZEN`;
6. rechazo de observaciones con baja confianza.

## Próxima etapa del core

1. Crear `OcctStepReferenceImporter` en C++/JNI reutilizando `FreeCAD-Android`.
2. Extraer cilindros, planos y círculos desde `TopoDS_Shape`.
3. Serializar `CadReferenceModel` y malla LOD en almacenamiento privado.
4. Definir el protocolo binario/JSON de `DeviceObservation`.
5. Implementar un simulador de tres celulares y latencia antes de usar cámaras reales.
6. Incorporar el ajuste del manto, eje y discos con restricciones coaxiales.
