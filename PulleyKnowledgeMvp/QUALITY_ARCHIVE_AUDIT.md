# Auditoría inicial del archivo de Calidad

## Fuente operativa

Se identificaron tres carpetas llamadas `Control de Calidad SKM`. Dos son copias de 2026 con contenido histórico equivalente y una corresponde a la fuente antigua de 2022. Para la ingesta se usa como índice operativo:

- `Control de Calidad SKM / Informes Por OT`
- carpeta: `1OkR0Qe5oBOqVTh15fPTS1wzsnDbIZ-_9`

La identidad de un documento no se determina por su URL. El importador deberá deduplicar por hash del contenido y conservar las distintas ubicaciones como referencias.

## OT-781

Documento de evaluación: `Informe de evaluacion OT 781 Polea Motriz 150CV017 código de material 4162018 Minera DGM Rev.1.pdf`.

Datos explícitos:

- código de evaluación: `4162018`;
- componente: Polea motriz 150CV017;
- diámetro de manto: `760 mm`;
- largo de manto: `2286 mm`;
- espesor original de manto: `22 mm`;
- soporte FAG SAF 534;
- rodamiento FAG 22234 E1-K;
- manguito H3134.515;
- elemento MAV 1008, 200 × 260 mm.

El informe final de armado declara `4196149`. No se fusionan automáticamente `4162018` y `4196149`.

## OT-867

Documento de evaluación: `Informe de evaluación OT 867 Polea de Cola 140CV006-008 código 4162038 Minera DGM.rev.1.pdf`.

Datos explícitos:

- código de evaluación: `4162038`;
- componente: Polea de cola 140CV008;
- diámetro de manto: `610 mm`;
- largo de manto: `1375 mm`;
- largo de eje informado por UT: `1935 mm`;
- diámetros del eje: `150–185 mm`;
- soporte FAG SAF 534;
- rodamiento FAG 22234 E1-XL-K;
- manguito H3134.515;
- elemento BIKON XT 60 5 15/16.

En la carpeta aparecen `4162037` en recepción, `4162038` en evaluación y `4196111` en armado final. Las dimensiones se promueven solamente bajo `4162038`; los demás códigos quedan relacionados pero no equivalentes.

## OT-270

Documento: `(OT 270) Informe de Evaluación Polea Motriz CV 26 Minera Spence-OC 4507252320.docx`.

- código de material: `10510386`;
- componente: Polea Motriz CV 26;
- diámetro de manto: `1260 mm`;
- largo de manto: `1525 mm`;
- distancia entre centros de soportes: `2080 mm`;
- distancia entre centros de rodamientos: `2054 mm`;
- espesor de revestimiento: `25 mm`;
- espesor de manto: `30 mm`;
- alojamiento de soporte: `540 mm`;
- soporte: SD 3164;
- rodamiento: 23164 CCK W33 C3.

Fuente Drive: `1thr4fjtlwDzBntHDLOEGHVVJNfLpAvl3`.

## OT-343

Documento: `(OT 343) Informe Final de Armado Polea de Cola Minera Gaby-Rev.0.pdf`.

- código de material: `4162045`;
- componente: Polea de Cola;
- diámetro de manto: `610 mm`;
- largo de manto: `1981 mm`;
- distancia entre centros de soportes: `2718 mm`;
- distancia entre centros de rodamientos: `2712 mm`;
- espesor de revestimiento: `25 mm`, goma lisa;
- espesor de manto: `34 mm`;
- largo total de eje: `2873 mm`;
- diámetro en rodamientos: `150,81 mm`, h9;
- diámetro en manguitos de expansión: `190 mm`, h8;
- material de eje declarado: SAE 4340;
- manguito de expansión: B Loc B115, 250 × 190 mm.

La cota de 250 mm corresponde al alojamiento del manguito de expansión en el manto, no al alojamiento de soporte; no se almacena bajo `SUPPORT_BORE_DIAMETER`.

Fuente Drive: `167or-wZIguJAmDqWPBl6GsRdxrmmeXKP`.

## Reglas derivadas

1. Una OT puede contener más de un código documental.
2. Recepción, evaluación y armado no se consideran automáticamente la misma variante.
3. Cada dimensión se vincula al código declarado en el documento que la contiene.
4. El ranking puede mostrar códigos relacionados, pero el overlay rígido exige una variante resuelta.
5. Los conflictos se exponen; nunca se corrigen silenciosamente.
6. Una cota se clasifica por su semántica mecánica; un alojamiento de manguito no se convierte en alojamiento de soporte.
