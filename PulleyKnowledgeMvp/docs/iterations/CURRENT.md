# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha20`

## Estado acumulado

- La aplicación Android compila para `arm64-v8a` e incluye el puente STEP/OCCT.
- El pipeline fotogramétrico tiene pruebas sintéticas para correspondencias, fundamental, pose, triangulación, tracks, grafo, cilindro y restricciones CAD.
- No existe todavía bundle adjustment local/global ni calificación metrológica.
- La ejecución real Camera2/STEP debe validarse en Samsung A15 y Honor X5C.
- La base histórica inicial contiene ocho códigos, pero aún mezcla evidencia de familia con evidencia específica de OT.
- La auditoría Drive confirmó que un código puede tener múltiples OT y revisiones.
- Se adoptó una tolerancia máxima de 30 mm en radio exterior como condición necesaria de coincidencia.
- La base actual no está autorizada para identificación automática industrial.

## Iteración actual o última cerrada

### ITER-002 — Auditoría Drive de código, OT, planos y cotas

Registro:

`history/ITER-002_2026-07-21_drive-plan-database-audit.md`

Resultado:

- `10415863` y `10415860`: familia coherente, dimensiones deben separarse por OT/revisión;
- `4162054`, `4196149`, `1462827`: planos dimensionales localizados, base pendiente de completar;
- `4196111`, `10510386`: sin radio aprobado suficiente en la base revisada;
- `4162045`: conflicto crítico de identidad y aliases;
- puerta de radio definida, todavía no implementada.

## Bloqueos activos

1. `DATA-001`: modelo actual no separa OT/plano/revisión.
2. `DATA-002`: `4162045` no puede producir `MATCH`.
3. `DATA-003`: radio, diámetro y espesor radial no tienen tipos suficientes.
4. `DATA-004`: manto desnudo y superficie revestida pueden confundirse.
5. `DATA-005`: faltan conversiones trazables de unidades originales.
6. `DATA-006`: `4196111` tiene posible conflicto con `4162038`.
7. `VALID-001`: no existe validación física ni repetibilidad en dispositivos.
8. `ANDROID-001`: Camera2 no siempre solicita la máxima resolución nativa.
9. `RECON-001`: no existe bundle adjustment local/global.
10. `ABI-001`: OCCT disponible solo para `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-003 — Modelo OT/plano/revisión y puerta de radio

Prioridad de trabajo:

1. implementar entidades y persistencia por OT, plano, revisión, dimensión y superficie;
2. migrar la semilla actual sin perder evidencia ni romper sesiones;
3. introducir radios `BARE_SHELL` y `OUTER_LAGGING`;
4. implementar la regla `abs(radio_observado - radio_referencia) <= 30 mm`;
5. agregar pruebas de frontera en 29, 30 y 31 mm;
6. probar pulgadas/milímetros y revisiones contradictorias;
7. cargar evidencia auditada de `10415863`, `10415860`, `4162054`, `4196149` y `1462827`;
8. mantener `4162045`, `4196111` y `10510386` en `BLOCKED`;
9. generar razones y fuentes legibles para cada decisión.

## Criterios de entrada

- comenzar desde la cabeza actual del PR #8;
- leer `ROADMAP_ENGINEERING.md` e ITER-002;
- no modificar valores históricos sin conservar su fuente;
- no incorporar nuevas familias antes de estabilizar el modelo;
- preservar compatibilidad de compilación alpha20.

## Criterios de salida

- esquema nuevo y migración idempotente;
- consultas por código, OT, plano y revisión;
- radios y superficies con tipos explícitos;
- pruebas 29/30/31 mm aprobadas;
- pruebas de unidades aprobadas;
- conflictos producen `REVIEW` o `BLOCKED`;
- CI del producto e historial exitosos;
- ITER-003 archivada en `history/`;
- este archivo actualizado con ITER-004 y sus puertas de salida.