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
