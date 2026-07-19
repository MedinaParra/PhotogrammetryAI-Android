# v0.19–v0.21 — Conocimiento de poleas y superposición asistida

## Objetivo

Agregar conocimiento de dominio al core para que una polea no sea tratada como una nube de puntos genérica. El sistema debe:

1. reconocer componentes y relaciones mecánicas;
2. recuperar una familia o caso histórico similar;
3. usar un STEP similar de manera controlada;
4. reconstruir paramétricamente lo que sí fue medido;
5. separar claramente medición, inferencia y previsualización.

## Fuentes iniciales

La base integrada registra fuentes oficiales de fabricantes. Los hechos no se copian como reglas absolutas: se almacenan con confianza y trazabilidad.

- PROK — tipos y componentes de poleas:
  `https://prok.com/common-types-of-pulleys-found-in-a-conveyor-belt-system/`
- PROK — poleas diseñadas y opciones de revestimiento:
  `https://prok.com/pulleys/`
- PROK — polea gearless, discos extremos y ruta de carga:
  `https://prok.com/global-product/gearless-drive-pulley/`
- Continental — función del revestimiento de poleas:
  `https://www.continental-industry.com/global/en/products-solutions/conveying-solutions/conveyor-components/pulley-lagging`
- SKF — solución de tres barreras para soportes de poleas:
  `https://ro.promo.skf.com/acton/fs/blocks/showLandingPage/a/22868/p/p-00a9/t/page/fm/5`
- SKF — sellos tipo taconite para ambientes contaminados:
  `https://evolution.skf.com/sealing-solutions-for-challenging-environments/`

## Iteración v0.19 — base de conocimiento

Clase principal:

`core/pulleyknowledge/PulleyKnowledgeBase.java`

### Entidades

- `SourceRecord`: origen trazable.
- `Fact`: afirmación, clase, confianza, fuentes y etiquetas.
- `PulleyTemplate`: familia genérica sin dimensiones inventadas.
- `ReferenceCase`: polea real confirmada con dimensiones, componentes y STEP asociado.

### Taxonomía inicial

Roles:

- drive;
- head;
- tail;
- take-up;
- snub;
- bend;
- gearless drive;
- unknown.

Arreglos:

- live shaft;
- dead shaft;
- unknown.

Componentes:

- shell;
- end disc;
- shaft;
- hub;
- locking element;
- bearing;
- bearing housing;
- seal;
- lagging;
- drive connection.

Revestimientos:

- ninguno;
- goma lisa;
- goma diamantada;
- goma perfilada;
- cerámica con hoyuelos;
- cerámica lisa;
- cerámica de unión directa;
- poliuretano;
- desconocido.

### Principios

- El revestimiento es evidencia de fricción/desgaste, no una prueba absoluta de rol.
- El soporte y sello ayudan a inferir ambiente y centro de eje, no deciden por sí solos el rol.
- Eje, hub/locking, discos, manto y revestimiento forman normalmente una cadena coaxial.
- Los discos extremos conectan la carga del eje con el manto.
- Las dimensiones reales se incorporan solamente desde inspección confirmada, plano o STEP validado.

## Iteración v0.20 — búsqueda de polea similar

Clase:

`core/pulleyknowledge/PulleySimilarityEngine.java`

El ranking combina:

- compatibilidad de rol;
- arreglo live/dead shaft;
- familia de revestimiento;
- componentes observados;
- dimensiones directas;
- proporciones geométricas;
- etiquetas visuales/operacionales;
- calidad de evidencia;
- confianza de verificación del caso histórico.

### Proporciones usadas

- ancho de cara / diámetro de manto;
- diámetro de eje / diámetro de manto;
- distancia entre rodamientos / ancho de cara;
- largo total de eje / ancho de cara;
- diámetro de hub / diámetro de manto.

Las diferencias dimensionales se comparan en espacio logarítmico. Esto evita que una diferencia absoluta de 20 mm tenga el mismo significado en una polea pequeña y en una muy grande.

### Explicabilidad

Cada coincidencia devuelve explicaciones como:

- `same shaft arrangement`;
- `component topology=0.889`;
- `direct dimensions=0.956 from 5 values`;
- `dimension ratios=0.932 from 4 ratios`.

La GUI futura podrá mostrar por qué se eligió una polea.

## Iteración v0.21 — hipótesis de superposición

Clase:

`core/pulleyknowledge/PulleyOverlayHypothesisEngine.java`

### Modos

#### `RIGID_REFERENCE`

Se permite cuando:

- el candidato es un caso real verificado;
- existen al menos dos dimensiones comparables;
- el error dimensional máximo es bajo;
- la similitud global es alta.

No se escala el STEP. Todavía debe existir un ancla CAD que declare origen y eje local.

#### `UNIFORM_PREVIEW_SCALE`

Se permite para guiar visualmente cuando un caso es parecido pero no idéntico.

- solo escala uniforme;
- siempre marcado `previewOnly`;
- no puede alimentar cotas finales;
- no debe exportarse como geometría medida.

#### `PARAMETRIC_REBUILD`

Se usa cuando no existe un caso suficientemente parecido.

- manto y eje provienen de mediciones;
- la base aporta solo topología y restricciones;
- cubos, discos y revestimiento se agregan únicamente si hay dimensiones o evidencia.

### Primitivas neutrales al renderizador

- manto cilíndrico;
- revestimiento;
- eje;
- discos izquierdo/derecho;
- hubs izquierdo/derecho;
- centros de rodamientos.

Cada primitiva incluye:

- centro mundial;
- eje mundial;
- diámetro;
- largo;
- confianza;
- bandera `measured`.

### Restricciones mecánicas

- coaxialidad con el eje;
- perpendicularidad de discos;
- concentricidad con el manto;
- centrado entre discos;
- centros de soporte sobre el eje;
- ancla obligatoria para STEP.

## Flujo completo

```text
Targets + cámaras calibradas
        ↓
Few-view reconstruction
        ↓
Eje desde soportes STEP
        ↓
Manto paramétrico medido
        ↓
ObservedPulleySignature
        ↓
PulleySimilarityEngine
        ↓
Top cases / families
        ↓
PulleyOverlayHypothesisEngine
        ↓
Rigid reference | Preview scale | Parametric rebuild
```

## Aprendizaje futuro

Una inspección solo se agrega como `ReferenceCase` cuando:

- el operador confirma el rol;
- las cotas fueron verificadas;
- el modelo STEP corresponde a la pieza;
- se registra la procedencia;
- la confianza de verificación supera el umbral definido.

Las capturas no confirmadas pueden generar hipótesis, pero no modificar automáticamente la base validada.

## Prueba

```bash
bash tools/run_pulley_knowledge_v21_test.sh
```

La prueba cubre:

- carga de fuentes y hechos;
- tres casos históricos sintéticos;
- recuperación del caso correcto;
- propagación del STEP asociado;
- superposición rígida;
- superposición escalada solo para preview;
- degradación a reconstrucción paramétrica;
- generación de manto, eje, discos, lagging, hubs y centros de soporte.

## Pendientes

- persistencia Room/archivo de la base;
- importador de casos desde JSON;
- ancla CAD explícita por STEP;
- catálogo real validado por el usuario;
- proyección de primitivas y aristas en las tres cámaras;
- aprendizaje limitado desde sesiones confirmadas;
- búsqueda por soporte SKF/Timken y locking assembly.
