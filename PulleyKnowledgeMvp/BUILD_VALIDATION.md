# Validación portátil — 0.2.0-alpha2

## Código verificado

- compilación de todas las clases Android contra framework Android 15;
- recompilación compatible a bytecode Java 6 para el empaquetador portátil;
- conversión a `classes.dex` Dalvik 035;
- pruebas puras de cobertura, calidad, escala y selección de pares;
- manifest binario con launcher, cámara y actividades internas;
- firma APK Signature Scheme v2 verificada entre API 24 y 35.

## Artefacto

- paquete: `cl.skm.pulleyai`;
- versión: `0.2.0-alpha2` (`versionCode 3`);
- `minSdk 24`;
- `targetSdk 35`;
- launcher: `cl.skm.pulleyai.LauncherActivity`;
- SHA-256: `5adf26de896af487deda6a86c1f849146e642d1989e447ba0eed01a6fff505fc`.

## Funciones integradas

- creación y continuación de sesiones;
- preview Camera2 y JPEG de resolución completa;
- IMU y orientación por disparo;
- cobertura de 12 sectores en dos alturas;
- puerta mínima de 30 fotografías aceptadas;
- rechazo explicado por desenfoque, luz o movimiento;
- persistencia de fotografía, metadatos y SHA-256;
- motor SQLite de conocimiento y validación dimensional;
- evidencia auditada inicial de OT-781 y OT-867;
- resolución matemática de escala y selección de pares disponible como núcleo v0.27.

## Límites

No se ha ejecutado la APK en un teléfono dentro de esta sesión porque no existe ADB/dispositivo conectado. La aplicación aún no calcula correspondencias visuales, poses, triangulación, nube de puntos ni overlay STEP; por ello sigue siendo una alpha de captura y preparación fotogramétrica, no una versión productiva final.
