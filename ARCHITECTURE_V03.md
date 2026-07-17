# Arquitectura móvil v0.3

## Flujo correcto
1. CameraX captura imágenes completas y metadatos.
2. Se corrige distorsión con calibración intrínseca.
3. OpenCV/NDK detecta líneas, círculos, elipses, contornos y puntos ORB/AKAZE.
4. Cada foto produce un DXF 2D de diagnóstico, conservando además los descriptores originales.
5. Entre pares de fotos se emparejan puntos, se calcula matriz esencial, pose relativa y triangulación.
6. Bundle adjustment local refina cámaras y nube dispersa.
7. Una referencia medida, marcador ArUco o profundidad ARCore fija la escala métrica.
8. IA TFLite segmenta clases: manto, eje, soporte, rodamiento, placa y fondo.
9. RANSAC ajusta cilindros, planos, cajas, conos y esferas sobre la nube segmentada.
10. Se exportan DXF por vista, PLY/OBJ de nube o malla, JSON de sesión y FCMacro paramétrica.

## Regla importante
El DXF no sustituye las correspondencias multivista. Es una salida vectorial y una ayuda de depuración. La reconstrucción 3D usa píxeles calibrados, descriptores, poses y triangulación.

## Operación íntegra en teléfono
- Kotlin/Java: interfaz, sesiones, archivos y orquestación.
- CameraX: captura y análisis.
- OpenCV Android + C++ NDK: geometría epipolar, calibración, ORB/AKAZE, PnP, triangulación y RANSAC.
- TensorFlow Lite: segmentación y clasificación local, sin nube.
- NNAPI/GPU delegate: aceleración cuando el equipo la permita.
- Room: persistencia de observaciones y poses.

## Captura mínima propuesta
- 18 a 36 fotografías alrededor de una polea.
- Solape mayor a 70 %.
- Dos alturas y acercamientos a extremos/eje.
- Exposición y foco bloqueados.
- Regla, marcador ArUco o distancia conocida visible en 3 o más tomas.

## Salidas
- `view_0001.dxf`, `view_0002.dxf`...
- `session.json`
- `sparse_cloud.ply`
- `recognized_primitives.json`
- `assembly.FCMacro`
