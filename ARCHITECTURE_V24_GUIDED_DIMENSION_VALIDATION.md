# v0.24 — Validación dimensional guiada

## Objetivo

Después de identificar una familia de polea, la aplicación no debe aceptar silenciosamente todas las cotas históricas. Debe convertirlas en preguntas trazables para el operador y registrar si coinciden con el levantamiento actual.

Una cota histórica sigue siendo evidencia histórica aunque el operador la confirme. La confirmación no modifica el informe fuente y una corrección de terreno se almacena como un valor separado.

## Secuencia funcional

```text
identificación de familia
        ↓
generación de cotas sugeridas
        ↓
preguntas ordenadas por criticidad
        ↓
coincide / corregir / no coincide / no medido
        ↓
evaluación de preparación para overlay
        ↓
overlay rígido, paramétrico o bloqueado
```

## Primeras cotas sugeridas

1. Largo del manto.
2. Diámetro del manto.
3. Distancia entre centros de soportes.
4. Distancia entre centros de rodamientos.
5. Largo total del eje.
6. Diámetro del eje en rodamientos.
7. Diámetro del eje en manguitos de expansión.
8. Diámetro del alojamiento del soporte.
9. Espesor del revestimiento.
10. Espesor del manto.

La distancia entre soportes y la distancia entre rodamientos se mantienen como variables distintas.

## Origen de una sugerencia

- `USER_INPUT`: dato ingresado directamente por el operador.
- `HISTORICAL_CONSENSUS`: mediana ponderada de informes históricos.
- `SCAN_ESTIMATE`: estimación producida por el levantamiento visual.
- `FUSED_HISTORY_AND_SCAN`: combinación de historial y escaneo cuando son compatibles.

Cada pregunta conserva:

- valor recomendado;
- valor histórico, si existe;
- valor del escaneo, si existe;
- tolerancia de comparación;
- confianza;
- prioridad;
- procedencia;
- resumen de fuentes;
- respuesta del operador;
- valor corregido;
- desviación;
- nota y fecha de respuesta.

## Respuestas del operador

### `CONFIRMED_MATCH`

La medición coincide dentro de la tolerancia. Puede utilizarse como geometría validada de la sesión.

### `CORRECTED`

El operador entrega el valor real. El motor conserva el valor sugerido y utiliza el corregido como valor efectivo de la sesión.

### `DOES_NOT_MATCH`

La geometría histórica no coincide y no existe todavía una cota sustituta confiable. Bloquea el overlay rígido si la cota es crítica.

### `NOT_MEASURED`

No fue posible verificar la cota. Las cotas opcionales pueden permanecer así; una cota crítica no resuelta bloquea el overlay rígido.

## Prioridad

### Críticas

- largo del manto;
- diámetro del manto;
- centros de soportes;
- centros de rodamientos.

### Importantes

- largo total de eje;
- diámetros de zonas de rodamientos y bloqueo;
- diámetro del alojamiento del soporte.

### Opcionales

- espesores del manto y revestimiento.

## Reglas de overlay

El overlay rígido solamente puede habilitarse cuando:

- la identificación no está en `VERIFY_VARIANT`, `VISUAL_ONLY` o `NO_MATCH`;
- todas las preguntas críticas disponibles están confirmadas o corregidas;
- no existe una contradicción crítica sin resolver.

Una corrección no invalida la familia. El overlay o la reconstrucción paramétrica deben usar el valor corregido.

## Ejemplo 10415863

```text
Largo del manto: 1520 mm — confirmado por entrada obligatoria
Diámetro del manto: 1400 mm — sugerido
Centros de soportes: 2080 mm — sugerido
Centros de rodamientos: 2030 mm — sugerido
Largo total del eje: 2355 mm — sugerido
```

Ejemplo de interacción:

```text
Distancia entre centros de soportes sugerida: 2080 mm
Tolerancia de validación: ±20,8 mm
Fuente: OT-262, informe de evaluación

¿Coincide con el levantamiento?
[Coincide] [Corregir valor] [No coincide] [No fue posible medir]
```

## SQLite v2

Se agregan:

- `dimension_review`;
- `dimension_review_item`.

La migración `1 → 2` es explícita y no destructiva. Las revisiones se vinculan con `identification_session`, mientras los valores históricos continúan en `dimension_evidence`.

## Clases

- `PulleyDimensionReview`
- `PulleyDimensionSuggestionEngine`
- `DimensionReviewStore`
- `InMemoryDimensionReviewStore`
- `AndroidSqliteDimensionReviewStore`
- `PostIdentificationDimensionReviewService`
- integración en `LocalPulleyKnowledgeEngine`

## Validación

```bash
bash tools/run_dimension_review_v24_test.sh
```

La prueba cubre:

- generación automática después de identificar;
- largo obligatorio preconfirmado;
- sugerencias de 1400, 2080 y 2030 mm;
- bloqueo mientras existan cotas críticas pendientes;
- corrección de centros de rodamientos;
- persistencia de respuestas;
- habilitación del overlay tras resolver las cotas críticas;
- bloqueo permanente de `VERIFY_VARIANT`;
- presencia de esquema y migración SQLite v2.

## Siguiente fase

1. Conectar observaciones geométricas reales del escaneo a todas las cotas sugeridas.
2. Dibujar cada cota sobre la imagen y el modelo 3D.
3. Permitir que el usuario seleccione visualmente los dos centros que está validando.
4. Generar una geometría paramétrica usando valores efectivos confirmados o corregidos.
5. Crear el resumen final de levantamiento con diferencias contra historial.
