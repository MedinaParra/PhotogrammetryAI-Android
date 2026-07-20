# Estado de preparación industrial — 0.8.0-alpha9

## Implementado

- captura horizontal fija con Camera2;
- dos anillos de 12 sectores y mínimo de 30 imágenes aceptadas;
- control de desenfoque, iluminación, movimiento y espacio libre;
- sesiones SQLite con migraciones no destructivas y trazabilidad SHA-256;
- exportación ZIP con fotografías, metadatos y diagnósticos;
- características visuales y correspondencias;
- RANSAC afín de respaldo;
- matriz fundamental y error de Sampson;
- intrínsecos desde focal y tamaño físico del sensor;
- matriz esencial, pose relativa, cheirality y paralaje;
- triangulación dispersa local y error de reproyección;
- grafo multivista con vistas aisladas y enlaces entre anillos;
- invalidación automática del diagnóstico al agregar nuevas fotografías.

## Puerta de reconstrucción

La sesión solo queda `SPARSE_READY` cuando tiene intrínsecos disponibles, suficientes aristas trianguladas, ambos anillos conectados, profundidad positiva y error de reproyección aceptable.

## Pendiente antes de declarar producto industrial

1. Prueba física en Samsung/Honor: instalación, permiso, suspensión, reapertura y rotación.
2. Ensayo térmico, almacenamiento y memoria en recorridos de 30–50 imágenes.
3. Tracks persistentes de tres o más vistas.
4. Optimización global y bundle adjustment.
5. Nube dispersa unificada y escala global.
6. Ajuste robusto de cilindro, eje, soportes y centros.
7. Comparación contra instrumentos y poleas de geometría conocida.
8. Modelo paramétrico y overlay STEP únicamente después de validar cotas.

La versión actual contiene fotogrametría real, pero no se declara metrológica ni de producción hasta superar estas pruebas.
