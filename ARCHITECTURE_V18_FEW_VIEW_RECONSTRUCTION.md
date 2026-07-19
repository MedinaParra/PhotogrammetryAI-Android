# Arquitectura v0.18 — Fotogrametría con pocas vistas y referencias STEP

## Objetivo

Reconstruir geometría industrial útil con entre 2 y 8 vistas, priorizando tres celulares que observan targets métricos comunes y modelos STEP conocidos de los soportes.

El objetivo no es producir inicialmente una malla fotorealista. El objetivo es recuperar:

- puntos 3D consistentes;
- eje de la polea;
- radio y longitud del manto;
- posición de soportes STEP;
- errores de reproyección y confianza;
- datos suficientes para tracking y reconstrucción paramétrica.

## Decisión principal: Structure from Known Motion

Los targets entregan la pose de cada cámara dentro de un sistema mundial común. Por ello la ruta principal usa estructura desde movimiento conocido:

1. calibración intrínseca individual;
2. pose de cámara desde ChArUco/AprilTag;
3. extracción y asociación de puntos;
4. triangulación multivista con poses fijas;
5. refinamiento robusto;
6. restricciones STEP y primitivas mecánicas.

Esta ruta evita que un conjunto pequeño de imágenes deba resolver simultáneamente escala, pose, estructura y orientación absoluta.

Referencias técnicas:

- openMVG documenta una tubería específica de estructura desde poses conocidas, además de SfM incremental y global.
- COLMAP permite reconstruir y triangular desde poses conocidas y señala que los tracks de dos vistas pueden aportar restricciones adicionales en conjuntos escasos.
- OpenCV incluye IPPE/IPPE_SQUARE para marcadores planos y SQPnP como solución rápida/global para PnP.

## Componentes implementados

### `FewViewGeometry`

Modelo puro Java para:

- intrínsecos pinhole;
- poses rígidas cámara→mundo;
- rayos mundiales;
- proyección mundo→imagen;
- observaciones y tracks;
- puntos reconstruidos con métricas de calidad.

Los puntos de entrada deben estar previamente corregidos por distorsión.

### `FewViewTrackBuilder`

Construye tracks multivista desde matches por pares:

1. prueba de razón entre mejor y segunda distancia;
2. exigencia opcional de correspondencia recíproca;
3. ordenamiento por calidad;
4. cupos por celdas espaciales para evitar concentración;
5. unión de features mediante disjoint-set;
6. rechazo de uniones que introducirían dos features de una misma vista.

Configuración móvil inicial:

- ratio máximo: `0.78`;
- match recíproco obligatorio;
- cuadrícula: `8 × 6`;
- máximo: 6 matches por par de celdas;
- tracks: 2 a 8 vistas.

### `FewViewBaselineSelector`

Ordena pares de vistas usando:

- cantidad de tracks comunes;
- confianza de detección y pose;
- seno del ángulo entre rayos;
- penalización de pares con poco paralaje.

Esto evita comenzar la reconstrucción con dos fotografías casi idénticas.

### `KnownPoseFewViewReconstructor`

Para cada track:

1. forma rayos calibrados en el sistema mundial;
2. genera hipótesis desde todos los pares con paralaje suficiente;
3. calcula el punto medio del acercamiento mínimo entre rayos;
4. proyecta cada hipótesis en todas las cámaras;
5. selecciona por número de inliers, coste robusto y ángulo;
6. refina el punto con Gauss-Newton y pérdida Huber;
7. vuelve a clasificar inliers con un umbral más estricto;
8. rechaza puntos detrás de cámaras o con geometría degenerada.

Parámetros móviles iniciales:

- ángulo mínimo: `1.25°`;
- umbral inicial: `6 px`;
- umbral final: `2.75 px`;
- Huber delta: `2 px`;
- 15 iteraciones máximas;
- al menos dos vistas válidas.

El método de hipótesis exhaustivas es viable porque hay como máximo ocho vistas: para tres celulares solo existen tres pares.

### `AxisConstrainedPulleyEstimator`

El eje del alojamiento importado desde STEP se trata como una restricción fuerte. Cada punto reconstruido se descompone en:

- coordenada axial;
- distancia radial al eje.

Después:

1. calcula radio mediano;
2. estima dispersión mediante MAD;
3. rechaza outliers radiales;
4. refina el radio ponderado;
5. obtiene el tramo axial mediante cuantiles;
6. entrega cilindro, RMS radial y confianza.

Así el manto no puede deformarse como un cilindro oblicuo o una nube irregular por falta de imágenes.

## Relación con algoritmos publicados

### Pose desde targets

La implementación Android deberá usar:

- `SOLVEPNP_IPPE_SQUARE` para cada target cuadrado;
- IPPE o SQPnP para tableros y geometrías no reducidas a un único cuadrado;
- refinamiento iterativo usando la pose anterior como inicialización.

### Estimación robusta

MAGSAC++ marginaliza la escala de ruido y utiliza refinamiento reponderado, junto con muestreo progresivo P-NAPSAC. Para no incorporar todavía una implementación grande dentro del core Java:

- el modo conocido evalúa todos los pares disponibles;
- puntúa todas las vistas;
- utiliza pérdida Huber e IRLS/Gauss-Newton;
- OpenCV `USAC_MAGSAC` quedará como backend para el fallback de pose relativa.

No se afirma que el estimador Java sea una implementación de MAGSAC++.

### Triangulación

La literatura muestra ventajas de errores angulares y soluciones robustas multivista. La primera versión implementa:

- hipótesis por acercamiento de dos rayos;
- selección por consenso multivista;
- minimización posterior del error de reproyección en píxeles.

Una futura versión podrá reemplazar la hipótesis de dos rayos por triangulación angular óptima, manteniendo la misma API.

## Fallback cuando se pierde el target

El modo primario exige pose métrica desde targets. Cuando una cámara pierde temporalmente los targets se propone:

1. emparejar features contra el último keyframe con target;
2. estimar matriz esencial calibrada con 5 puntos;
3. usar `USAC_MAGSAC` para outliers;
4. recuperar pose relativa;
5. mantener escala con la última pose métrica, IMU y referencias STEP;
6. limitar la duración del fallback;
7. volver a anclar en cuanto reaparece un target.

Este fallback será implementado en C++/OpenCV, no como solver polinómico 5-point artesanal en Java.

## Prueba v0.18

`tools/run_few_view_v18_test.sh` valida:

- cuatro cámaras conocidas;
- cinco puntos 3D;
- ruido subpíxel;
- una observación falsa de `75 × 55 px`;
- selección automática de la línea base más informativa;
- rechazo del outlier;
- rechazo de una base de 1 mm a 5 m;
- creación de tracks A-B-C;
- rechazo de ratio, no reciprocidad y conflicto de vista;
- ajuste de manto de radio 500 mm con dos outliers radiales.

## Próximas etapas internas

1. Adaptador OpenCV para IPPE/SQPnP y targets.
2. Backend `USAC_MAGSAC + five-point essential` para pérdida temporal de target.
3. Corrección de distorsión según perfil de cada teléfono.
4. Ajuste conjunto ligero de poses y puntos con priors de target.
5. Proyección de bordes STEP y minimización CAD-imagen.
6. Clasificación de puntos entre manto, eje, discos y soporte.
7. Persistencia de tracks y keyframes.
8. Recién entonces construir la GUI en tiempo real.

## Referencias primarias consultadas

- OpenCV calib3d: PnP, IPPE, IPPE_SQUARE y SQPnP.
- Barath et al., “MAGSAC++, a Fast, Reliable and Accurate Robust Estimator”, CVPR 2020.
- Lee y Civera, “Closed-Form Optimal Two-View Triangulation Based on Angular Errors”, ICCV 2019.
- Hedborg et al., “Robust Three-view Triangulation Done Fast”, CVPR Workshops 2014.
- openMVG Structure from Motion documentation.
- COLMAP FAQ: reconstrucción desde poses conocidas y tracks de dos vistas.
