# Changelog

## 0.2.0
- Sesiones de múltiples componentes.
- Coordenadas XYZ por primitiva.
- Soporte para conos.
- Analizador geométrico local de imágenes.
- Exportación de ensamblajes FreeCAD agrupados.
- Campo de confianza IA y advertencias de validación.

## 0.1.0
- Captura básica de imagen.
- Cilindro, caja y esfera.
- Exportación individual a FCMacro.

## v0.5
- Módulo IA local dependency-free para corrección de escala y confianza.
- Fusión visual-inercial usando flujo visual, giroscopio, aceleración lineal y vector de rotación.
- Cuaterniones, vectores 3D y acumulación de poses.
- Sistema cartesiano de mano derecha con origen y ejes X/Y/Z por fotografía.
- Colector Android de IMU.
- Exportador JSON de trayectoria espacial.
- Prueba automática `tools/core_v5_test.sh`.

## v0.7
- PCA 3D con descomposición simétrica Jacobi.
- Ajuste robusto iterativo de cilindros y planos.
- Estimación de eje, radio, longitud, extensión, residual y confianza.
- Generador de sólidos paramétricos FreeCAD desde primitivas detectadas.
