# SKM Polea AI — APK MVP

Aplicación Android offline para:

- identificar familias por código de material, OT, largo y diámetro;
- consultar una semilla SQLite local;
- sugerir largo, diámetro, centros de soportes, centros de rodamientos y cotas del eje;
- registrar `Coincide`, `Corregir`, `No coincide` o `No medido`;
- bloquear el overlay rígido hasta validar las cotas críticas.

Este MVP todavía no incorpora cámara ni superposición 3D. Es la primera interfaz funcional del motor lógico.

## Compilar

```bash
gradle :app:assembleDebug
```

APK esperada:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Artefacto validado

La versión `0.1.0-mvp` fue compilada y firmada como APK de pruebas. Se verificó:

- paquete `cl.skm.pulleyai`;
- actividad `cl.skm.pulleyai.MainActivity`;
- `minSdk 23`, `targetSdk 28`;
- DEX parseable;
- firma SHA-256/RSA válida;
- SHA-256 del APK: `00227ff6f5822bc82f33609c0be7b0d1eca47d9194046943737b23657c3bcc7c`.

No se realizó todavía una prueba de instalación en hardware físico desde esta sesión.
