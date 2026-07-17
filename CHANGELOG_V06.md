# Versión 0.6 — reconstrucción cartesiana multivista

- Tracks persistentes de características entre tres o más imágenes.
- Triangulación por intersección de rayos usando poses completas 6DoF.
- Filtrado por paralaje mínimo y error de reproyección.
- Nube 3D incremental en milímetros.
- Vectores X/Y/Z para cada cámara en un sistema cartesiano de mano derecha.
- Exportación JSON de poses, puntos y vectores.
- Macro FreeCAD para visualizar trayectoria, ejes y nube dispersa.
- Prueba sintética de tres cámaras y 35 puntos, con error medio numérico inferior a 0,01 mm.

## Pendiente

- Asociación robusta de tracks cuando un punto reaparece tras varias capturas.
- Bundle adjustment local y global.
- Entrenamiento real del modelo de movimiento.
- Segmentación IA y ajuste robusto de cilindros/planos sobre nube real.
- Integración completa de CameraX en la interfaz de captura.
