# Versión 0.8 — geometría proyectiva y puntos de fuga

## Incorporado
- Segmentos 2D con confianza y representación homogénea.
- RANSAC local para encontrar hasta tres puntos de fuga.
- Refinamiento por mínimos cuadrados de cada punto de fuga.
- Conversión de puntos de fuga a direcciones 3D mediante la matriz intrínseca de cámara.
- Ortogonalización de las direcciones para crear un marco cartesiano de mano derecha.
- Reconocimiento orientado por perspectiva de eje de extrusión, plano y prisma cartesiano.
- Exportación JSON del marco, puntos de fuga y candidatos geométricos.
- Macro FreeCAD para visualizar ejes y direcciones detectadas.

## Uso previsto
Las líneas se extraen desde la fotografía mediante el vectorizador/Hough. El detector agrupa líneas que corresponden a paralelas físicas y convergen por perspectiva. Estas familias sirven como prior geométrico para orientar nubes, planos, cajas, vigas, ejes y cilindros antes del ajuste métrico multivista.

## Limitación
Un solo punto de fuga define dirección, no distancia ni posición absoluta. Las dimensiones y centros deben obtenerse mediante escala conocida, triangulación multivista o profundidad del dispositivo.
