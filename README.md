# Photogrammetry AI Android Portable v0.2

Prototipo Android para levantamiento geométrico rápido, reconocimiento inicial de primitivas y generación de macros FreeCAD.

## Funciones v0.2

- Captura guiada de fotografías desde el teléfono.
- Contador de imágenes por sesión (12 sugeridas como mínimo inicial).
- Formas: cilindro, caja, esfera y cono.
- Estimación visual local sin conexión mediante bordes y proporciones.
- Escala conocida configurable en milímetros.
- Entrada manual de cotas para corregir el resultado.
- Posición XYZ de cada componente.
- Sesiones con múltiples primitivas.
- Exportación de un conjunto agrupado como `LevantamientoIA.FCMacro`.
- Propiedad `ConfianzaIA` en cada objeto generado.

## Advertencia técnica

El analizador visual de esta versión es una heurística geométrica ligera. No es todavía fotogrametría calibrada ni metrología industrial. Las cotas deben verificarse antes de fabricar.

## Compilación portable Linux/WSL

```bash
chmod +x install_portable_linux.sh build_app.sh
./install_portable_linux.sh
./build_app.sh
```

APK esperado:

`PhotogrammetryAI/app/build/outputs/apk/debug/app-debug.apk`

## Instalación por USB

Active Depuración USB en Android y ejecute:

```bash
./install_on_phone.sh
```

## Flujo recomendado de captura

1. Colocar una regla, marcador ArUco o pieza de longitud conocida junto al componente.
2. Tomar al menos 12 fotografías alrededor de la pieza.
3. Evitar reflejos intensos y desenfoque.
4. Analizar una imagen y corregir dimensiones con las cotas conocidas.
5. Añadir cada primitiva al conjunto usando coordenadas XYZ.
6. Exportar la macro y ejecutarla en FreeCAD.

## Próximas etapas

- CameraX con captura de resolución completa.
- Marcadores ArUco/AprilTag para escala y orientación.
- TensorFlow Lite para segmentación de poleas, ejes y soportes.
- Ajuste RANSAC de cilindros y planos.
- Reconstrucción multivista y nube de puntos.
- Exportación STEP además de macro FreeCAD.

## Versión 0.4 — reconstrucción local operativa

Esta iteración agrega un núcleo Java ejecutable íntegramente en Android, sin servidor:

- `ImageVectorizer`: transforma gradientes de una foto en entidades DXF 2D.
- `PureJavaPhotogrammetryPipeline`: detecta esquinas, calcula descriptores locales, empareja dos vistas y triangula puntos.
- `OnDeviceReconstructor`: servicio de reconstrucción estéreo de alto nivel.
- `PlyWriter`: exporta la nube 3D como PLY ASCII.
- `ReconstructionReport`: informa características, coincidencias y puntos reconstruidos.

La triangulación usa una línea base conocida entre las dos capturas. En la interfaz final el usuario deberá indicar la separación aproximada entre posiciones de cámara o usar un marcador métrico. Esta implementación es una base verificable; la pose libre alrededor del objeto será incorporada con OpenCV/NDK en una etapa posterior.

### Flujo v0.4

1. Capturar foto A.
2. Desplazar el teléfono una distancia conocida, manteniendo solape superior a 70 %.
3. Capturar foto B.
4. Vectorizar cada vista a DXF.
5. Detectar y emparejar puntos locales.
6. Triangular nube dispersa y exportar PLY.
7. Ajustar primitivas y generar la macro FreeCAD.

### Prueba del núcleo

```bash
cd android_photogrammetry_ai
rm -rf .core-test-v4
mkdir .core-test-v4
javac -d .core-test-v4 PhotogrammetryAI/app/src/main/java/cl/ingenieria/photogrammetryai/core/*.java
javac -cp .core-test-v4 -d .core-test-v4 tools/TestV4.java
java -cp .core-test-v4 TestV4
```

## IA local y trayectoria cartesiana (v0.5)
Ejecute `tools/core_v5_test.sh` para validar el estimador visual-inercial sin Android. El archivo `example_camera_trajectory_v5.json` muestra la posición y los vectores espaciales de tres cámaras simuladas.

La red local incluida no requiere internet. Sus pesos son de integración y deben entrenarse con datos reales antes de afirmar precisión metrológica.

## Versión 0.6: sistema cartesiano multivista

`IncrementalSpatialReconstructor` mantiene poses y tracks entre capturas. Cada observación se convierte en un rayo espacial mediante la calibración y la pose visual-inercial. Los rayos se intersectan por mínimos cuadrados, se filtran por paralaje y error de reproyección y se exportan en milímetros.

Salidas nuevas:

- `example_spatial_reconstruction_v6.json`
- `example_spatial_cloud_v6.ply`
- `example_spatial_reconstruction_v6.FCMacro`

Ejecutar la prueba del núcleo:

```bash
./tools/run_spatial_v6_test.sh
```

## Versión 0.9 — herramientas integradas

- `PureJavaHoughLineDetector`: gradientes Sobel + acumulador Hough + segmentos dominantes.
- `PureJavaEllipseDetector`: componentes de borde + ajuste elíptico por covarianza.
- `IntegratedGeometryToolkit`: imagen → líneas → puntos de fuga → ejes → candidatos CAD.
- `MultiViewPerspectiveFusion`: fusiona marcos perspectivos de varias poses en ejes globales.
- `GeometryFrameAnalyzer`: adaptador CameraX RGBA ejecutado íntegramente en el teléfono.
- `CameraXGeometryController`: Preview + ImageAnalysis con descarte de cuadros.

CameraX está fijado en 1.6.1. El análisis se ejecuta cada 6 cuadros a 960×720 para limitar temperatura y consumo.

## v0.13 geometric vocabulary
The same pulley photographs now teach the local recognizer to decompose components into cylinders, discs, annuli, plates, rectangular prisms, arched housings, holes, bolt patterns, gear discs/teeth, fillets, chamfers and repeated lagging tiles. `IndustrialGeometryGrammar` also enforces physically meaningful relations such as coaxiality and support.

## v0.14 — Internet-assisted training pipeline
Adds a dataset/license registry, industrial geometry taxonomy, manifest auditor, compact TensorFlow training skeleton, LiteRT exporter and Android inference contract. No unverified web scraping and no falsely claimed trained weights are included.

## v0.15 — Ingeniería Viva y memoria de experiencia

El dispositivo ahora dispone de una capa local de aprendizaje supervisado. Cada levantamiento confirmado puede convertirse en un `ExperienceRecord`; las correcciones del operador actualizan un perfil de confiabilidad por geometría y las detecciones futuras se reordenan con un ajuste limitado. Las sesiones no verificadas no contaminan la memoria.

`DeviceExperiencePassport` resume sesiones, geometrías confirmadas, correcciones, calidad media y un puntaje de madurez exportable. El pasaporte acompaña al propietario y puede respaldarse o migrarse, por lo que el conocimiento no queda atado físicamente al teléfono. El puntaje representa experiencia documentada, no precio de reventa ni exactitud certificada.

Validación:

```bash
./tools/run_experience_v15_test.sh
```
