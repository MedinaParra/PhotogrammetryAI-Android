# SKM Polea AI — producto Android alpha

Aplicación Android offline orientada al levantamiento de poleas con un solo teléfono.

## Integrado en esta rama

- sesiones persistentes de captura en `capture_sessions.db`;
- Camera2 con preview y JPEG de resolución completa;
- orientación e IMU vinculadas al instante del disparo;
- cobertura guiada en 12 sectores y dos alturas;
- control local de desenfoque, exposición y movimiento;
- almacenamiento de metadatos y SHA-256 por fotografía;
- conocimiento técnico separado en `pulley_knowledge.db`;
- validación y corrección humana de cotas;
- parche idempotente con evidencia auditada de OT-781 y OT-867.

## Todavía no implementado

- poses multivista robustas;
- nube de puntos real;
- escala fotogramétrica y error de reproyección;
- detección automática de manto, eje y soportes;
- overlay STEP.

La aplicación no declara reconstrucción 3D mientras esos resultados no existan y no estén cuantificados.

## Compilar

```bash
gradle :app:assembleDebug
```

APK esperada:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Pruebas puras

```bash
bash tools/run_capture_core_v26_tests.sh
```

## Documentación

- `../PRODUCT_ROADMAP.md`
- `QUALITY_ARCHIVE_AUDIT.md`
