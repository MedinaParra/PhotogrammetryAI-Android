# SKM Polea AI 0.8.0-alpha9

## Captura

- interfaz horizontal fija;
- Camera2 con JPEG de resolución completa;
- IMU y estabilidad por disparo;
- dos anillos de 12 sectores;
- mínimo de 30 imágenes aceptadas;
- control de desenfoque, iluminación, movimiento y espacio libre;
- sesiones persistentes, hashes y exportación auditable.

## Fotogrametría

- características y correspondencias visuales;
- RANSAC afín de diagnóstico;
- matriz fundamental y error de Sampson;
- intrínsecos obtenidos desde Camera2;
- matriz esencial y pose relativa;
- cheirality, profundidad positiva y paralaje;
- triangulación dispersa local;
- error RMS de reproyección;
- grafo multivista con componentes, vistas aisladas y enlaces entre anillos;
- estado `SPARSE_READY` solo cuando todas las puertas son superadas.

## Validación CI

La APK fue compilada después de superar pruebas independientes para captura, escala, orientación, correspondencias, grafo, RANSAC, matriz fundamental, pose esencial y triangulación.

## Estado

Candidata de laboratorio. No se declara metrológica ni industrial final hasta completar pruebas físicas de teléfono, estabilidad térmica, nube global, bundle adjustment y comparación contra instrumentos.
