# SKM Polea AI — Roadmap ejecutable del producto

## Propósito

Construir una aplicación Android offline que permita levantar una polea con un solo teléfono, reconstruir su geometría principal, relacionarla con el historial técnico y pedir validación humana antes de exportar un modelo.

La aplicación no se considerará fotogramétrica porque tenga cámara o formularios. Solo alcanzará ese estado cuando produzca poses multivista, una reconstrucción escalada y errores cuantificados.

## Flujo único

```text
CaptureSession
  -> captura multivista
  -> control de calidad
  -> calibración y escala
  -> estimación de poses
  -> nube dispersa
  -> ajuste de manto/eje/soportes
  -> consulta código/OT/variante
  -> sugerencia de cotas
  -> validación o corrección
  -> geometría confirmada
  -> exportación
```

Toda evidencia pertenece a una sesión. Ningún valor histórico se convierte automáticamente en una medición actual.

## Fase 0 — Fundaciones de producto

### Entregables

- Un único proyecto Android instalable.
- Build reproducible local y CI.
- Persistencia SQLite/Room con migraciones no destructivas.
- Registro de versión de esquema, versión de conocimiento y versión de algoritmo.
- Diagnóstico exportable del dispositivo y de cada sesión.

### Puerta de salida

- `assembleDebug` es repetible.
- La APK abre en el teléfono objetivo.
- Una sesión sobrevive a reinicio de proceso y reinicio del teléfono.
- Las migraciones preservan fotos, mediciones y auditorías.

## Fase 1 — Captura single-device multi-view

### Entregables

- Preview de cámara.
- Captura JPEG de resolución completa.
- IMU y orientación por fotografía.
- Bloqueo de foco/exposición cuando el hardware lo permita.
- Control de desenfoque, exposición, movimiento y repetición de vista.
- Cobertura por sectores angulares y dos alturas.
- Acercamientos guiados a extremos y soportes.

### Puerta de salida

- 30–50 fotografías válidas en una sola sesión.
- Metadatos, orientación y calidad persistidos por foto.
- La app indica qué zona falta sin depender de Internet.

## Fase 2 — Calibración y escala

### Entregables

- Perfil intrínseco por cámara/resolución.
- Corrección de distorsión.
- Largo del manto obligatorio.
- Marcadores calibrados opcionales.
- Cota manual entre dos puntos como respaldo.
- ARCore Depth opcional y nunca obligatorio.

### Puerta de salida

Sobre cilindros de verdad conocida:

- error de largo <= 2 %;
- error de diámetro <= 3 %;
- repetibilidad entre sesiones <= 2 %.

Estas son metas internas, no certificación metrológica.

## Fase 3 — Fotogrametría dispersa

### Entregables

- Features y descriptores.
- Emparejamiento robusto y tracks multivista.
- Matriz esencial, pose relativa y triangulación.
- Bundle adjustment local/global.
- Nube dispersa escalada.
- Error de reproyección y descarte de vistas defectuosas.

### Puerta de salida

- Trayectoria de cámaras y nube coherentes en una escena conocida.
- Error y confianza visibles.
- No se acepta una reconstrucción silenciosamente degenerada.

## Fase 4 — Geometría especializada de poleas

### Entregables

- Segmentación de manto, eje, soportes, cubos y fondo.
- RANSAC de cilindros y planos.
- Restricciones de coaxialidad, simetría y paralelismo.
- Extremos del manto y centros de soportes/rodamientos.
- Modelo paramétrico preliminar.

### Puerta de salida

La app estima y permite corregir:

- largo del manto;
- diámetro del manto;
- eje principal;
- distancia entre centros de soportes;
- distancia entre centros de rodamientos.

## Fase 5 — Conocimiento técnico unificado

### Entregables

- MaterialFamily, MaterialVariant e InterventionOT separados.
- Fuentes y revisiones por evidencia.
- Deduplicación por hash de contenido.
- Paquetes SQLite versionados.
- Analizador de contradicciones.

### Orden de autoridad

1. medición física confirmada;
2. medición visual validada;
3. plano aplicable y revisión correcta;
4. informe histórico;
5. consenso de otras OTs;
6. inferencia visual;
7. aproximación.

## Fase 6 — Validación dimensional guiada

### Entregables

- Valor histórico, estimación visual, tolerancia y confianza.
- Respuestas: coincide, corregir, no coincide, no medido.
- Marcado manual de puntos extremos.
- Bloqueo de overlay rígido cuando faltan cotas críticas.

### Puerta de salida

Cada cota usada por el modelo final tiene procedencia y estado explícitos.

## Fase 7 — STEP y exportación

### Modos

- coincidencia rígida: solo rotación y traslación;
- reconstrucción paramétrica: familia como plantilla, cotas actuales como autoridad;
- referencia visual: sin declaración de coincidencia.

Nunca se escala uniformemente un STEP completo para hacerlo calzar.

## Fase 8 — Dataset e IA

La IA se entrena con sesiones verificadas. Primero se entrena segmentación; no se intenta generar CAD directamente desde fotografías sin geometría y verdad terreno.

## Fase 9 — Piloto industrial

### Indicadores

- error de largo, diámetro y centros;
- top-1/top-3 de identificación;
- porcentaje de sesiones corregidas;
- tiempo de captura/procesamiento;
- tasa de reconstrucción válida;
- fallos por modelo de teléfono y condiciones de taller.

## Política de versiones

- `foundation/*`: infraestructura transversal.
- `capture/*`: cámara, IMU y calidad.
- `reconstruction/*`: geometría multivista.
- `knowledge/*`: archivo técnico y SQLite.
- `product/*`: integración de extremo a extremo.

Una APK se publica como candidata solo cuando supera la puerta de la fase que declara. Las funciones no integradas se muestran como pendientes; nunca se simulan.
