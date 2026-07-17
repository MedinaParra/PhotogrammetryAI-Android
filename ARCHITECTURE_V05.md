# Arquitectura v0.5 — IA local y reconstrucción cartesiana

## Objetivo
Estimar el movimiento del teléfono entre capturas y expresar cada cámara dentro de un sistema cartesiano 3D de mano derecha, íntegramente en el dispositivo.

## Tubería
1. La cámara obtiene dos imágenes consecutivas.
2. El módulo visual calcula flujo mediano, dispersión, coincidencias e inliers.
3. AndroidImuCollector registra giroscopio, aceleración lineal y GAME_ROTATION_VECTOR.
4. LocalMotionAiModel ejecuta una red neuronal MLP pequeña en CPU sin servidor ni librerías externas.
5. VisualInertialPoseEstimator fusiona la salida visual e inercial.
6. Se genera una transformación rígida relativa: rotación + traslación.
7. Las transformaciones se acumulan en CameraPose.
8. CartesianFrame expone origen y vectores unitarios X/Y/Z.
9. TrajectoryJsonWriter exporta la trayectoria para reconstrucción, DXF y FreeCAD.

## Salidas
- translationMm: desplazamiento relativo estimado.
- rotation: cuaternión relativo.
- confidence: confianza combinada visual/IMU/IA.
- CameraPose: matriz R 3x3 y vector t 3x1.
- CartesianFrame: origen y ejes espaciales.
- JSON de trayectoria.

## Estado de la IA
La infraestructura de inferencia es completamente funcional y local. Los pesos incluidos son pesos semilla conservadores para probar integración, ejecución y formato de salida. Antes de usarla como instrumento métrico deben reemplazarse por pesos entrenados con secuencias reales que contengan verdad terreno de pose.

## Entrenamiento requerido
Cada muestra debe incluir:
- Estadísticas de flujo óptico.
- Inliers y cantidad de coincidencias.
- Ventana sincronizada de giroscopio y aceleración.
- Intervalo temporal.
- Pose relativa real obtenida con marcador, brazo robot, ARCore o sistema óptico.

El modelo final puede exportarse como pesos Java, LiteRT/TFLite o ONNX Runtime Mobile.
