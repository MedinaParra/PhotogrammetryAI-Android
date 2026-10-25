# Terreno — paquete saneado de fuente funcional + propuesta premium

## Integración en este repositorio

La fuente saneada se incorporó desde el commit `b30abbd9636d18abcc5b109b99ba5b73aca97f5e`; sus 44 entradas de manifiesto se verificaron antes de editar. `SOURCE-MANIFEST.json` y `TRANSFER-TEST-NOTES.json` conservan la evidencia del paquete recibido, no describen los hashes ni resultados de cambios posteriores.

La integración añade la allowlist nativa de renderer, Three, CSS y fuente local con MIME y CSP restrictivos; pruebas de assets transitivos; harnesses con sandbox explícito; y CI de navegador previo al job Android. `npm ci`, `npm test` y `npm run build` verifican la copia actual. El preview escucha solo en loopback. Playwright es dependencia de desarrollo fijada con lockfile; la aplicación sigue cargando bibliotecas locales sin CDN.

`npm run build:apk` requiere SDK Android 35/build-tools 35.0.0 y compilador JDK 17+, y produce únicamente `output/Terreno-premium-unsigned.apk`, su hash y manifiesto. No crea ni usa claves. El paquete conserva `org.terreno.piloto` y su provider; la versión es `1.4.0-premium-piloto`, code 5, superior al code 4 del snapshot recibido. No se presenta como versión instalable ni actualización compatible hasta comparar el APK estable real y firmar en custodia privada.

El workflow público ejecuta pruebas y capturas sintéticas con Chromium sandbox activo y Mesa/Xvfb; si fallan, el job Android queda bloqueado. Sus resultados reales, no la existencia del workflow, determinan qué está validado. Las capturas de Chromium no acreditan Android, Samsung ni aprobación estética del usuario. No se ha migrado firma ni modificado su repositorio custodio.

---

## Foto local y selección

El flujo de foto analiza el encuadre completo por defecto y ofrece recorte manual opcional. El procesamiento ocurre en un Worker local cancelable; los estados de resultado, ambigüedad, error y timeout son distintos. No se envían fotos a servidores. El motor actual sigue limitado a bastidor/pórtico frontal o sección aislada: todavía no reconoce familias generales de atriles. El objetivo de respuesta en cinco segundos requiere medición en Samsung; no se acredita aquí.

La selección muestra una ficha breve y una longitud geométrica temporal, con procedencia explícita. Una longitud del modelo no sustituye una medición física. Las pruebas de assets nativos son estáticas; compilación y empaquetado no equivalen a instrumentación Android ni prueba WebView.

## Notas originales del paquete recibido

**Estado verificado al 25 de octubre de 2026.** Este paquete fuente no es una APK, no acredita instalación Android y no incluye una propuesta visual aprobada por el usuario. Destino de trabajo indicado por el usuario: **PhotogrammetryAI-Android**, utilizado como almacenamiento; el contenido anterior no es la base funcional. Confirmar visibilidad/autorización antes de publicar: entregar este ZIP privado no autoriza un push público.

## Qué contiene y qué conserva
Base funcional Terreno 1.3 piloto (snapshot local de origen), más propuesta visual aislada. Conserva los módulos originales, no un mockup:
- Proyectos locales, persistencia IndexedDB, edición de nodos/vigas/perfiles, medidas y procedencia, deshacer, fotografías, importación/exportación JSON.
- Modelos iniciales paramétricos de bastidor/atril, ejemplos claramente estimados y no aprobados.
- Foto primero: análisis local experimental Sobel/Hough para marco frontal sencillo, revisión humana de trazos y medidas explícitas. NO reconstrucción multivista general ni medición automática de dimensiones reales.
- Vista 3D grande horizontal, gestos táctiles, rueda virtual para menú y azimut, orientación por presets/cubo, cierre/cancelación y controles de campo.
- Exportador STEP sólido para entidades compatibles. Si hay cunas de previsualización NO permite exportar un conjunto incompleto: no suprimir esa guardia.
- Envoltura Android nativa, acceso a cámara/archivos por intents y modo inmersivo. No desactiva silenciosamente todas las notificaciones ni garantiza prevención de accidentes.

## Propuesta visual REAL, todavía sin captura aprobada
`dist/premium-viewer.js` adapta el visor original `dist/viewer.js`: Three.js local WebGL2, cámara ortográfica, acero satinado, luz/sombra discreta y cunas con normales suaves. Modelo canónico, milímetros y exportador permanecen separados del render.
`dist/premium.css` y SVG inline agregan Inter, iconos Lucide coherentes, controles táctiles y panel lateral limpio. No es una imagen generada: es código ejecutable.

Conversión render-only F=(-X,Y,Z) del grupo y cámara conserva proyección legacy usando frustum normal; Three corrige frontFace por determinante del objeto. Suelo usa worldToLocal, lados de cunas normales coherentes. Selección usa raycast3D CPU sobre mallas; no se denomina GPU picking. Fallback Canvas explícito ante ausencia/pérdida/error de GPU; render bajo demanda, DPR limitado, recursos liberados. No afirmar calidad/FPS en dispositivo objetivo sin medir.

## Validación y límites exactos
- La base anterior tuvo QA funcional en Chromium, pero esa evidencia NO valida el nuevo render.
- En esta propuesta: 202 regresiones locales originales y un grupo nuevo del renderer pasan (cámara en cuatro orientaciones, mallas/normales finitas, cotas de cunas, inmutabilidad y bloqueo STEP). Build estático pasa.
- **QA visual premium real pendiente**: no hay capturas premium ni aprobación estética en este paquete. `scripts/premium-qa.mjs` está preparado, no se presenta como ejecutado.
- **Android premium pendiente**: MainActivity mantiene la allowlist antigua. Todavía hay que integrar los nombres/rutas de renderer y font locales y CSP font-src self de forma restrictiva. NO compilar una APK de esta copia y afirmar que renderiza premium antes de resolverlo y probar WebView real.
- El APK anterior fue rechazado por acabado visual; no volver a entregar el diseño antiguo como solución.
- Motor BEAM experimental, puente AsterMax/Code_Aster, PMX, Bluetooth físico y cálculo operacional NO están incluidos en este paquete. Son frentes separados, no eliminados de la visión del producto.

## Bibliotecas y privacidad
`VENDOR-MANIFEST.json` registra versiones, integridad de paquetes y hashes de archivos. Three.js0.186.1 MIT; Inter4.1 OFL-1.1; Lucide1.61.0 ISC más aviso MIT de Feather. Licencias completas en `dist/vendor`. Todo se sirve localmente; sin CDN ni telemetría añadida. No se incluyeron claves, secretos, PDF/plano privado, fotos de usuarios, logs de sesión, APK, historiales git ni workflows de repositorios antiguos. Los nombres de variables de entorno de firma en el script son referencias, no credenciales.

## Trabajo de la tarea Codex
1. Materializar este Library ZIP en el entorno consumidor mediante skill Library vigente; verificar SHA256 externo y `SOURCE-MANIFEST.json`. No asumir que rutas de este productor existen en otro executor.
2. Inspeccionar README y código antes de cambios. Mantener features existentes; no volver a iniciar un cascarón vacío. Si aparecen cambios del usuario, reconciliarlos y no sobreescribirlos.
3. Ejecutar `npm test` y `npm run build` en la copia local. Node22+ y Python3; no se necesitan dependencias npm de producción porque están vendorizadas.
4. Para QA visual, instalar Playwright sólo como dependencia de desarrollo desde registro oficial y navegador oficial según política del entorno; ejecutar `npm run test:visual-premium` con `PREMIUM_QA_OUTPUT`. El harness exige WebGL2 real de Chromium (software explícito), guarda reposo/menú/selección/teléfono/formulario/fallback y verifica errores. Inspeccionar píxeles, no sólo tests verdes. Fallback simulado no valida GPU física.
5. Respetar cualquier denegación de herramientas. No desactivar sandbox/web security ni cambiar permisos KVM para sortear bloqueos. Si una ruta autorizada falta, reportar acción/entorno/bloqueo y pedir coordinación, no evadirla cambiando de herramienta.
6. Integrar assets nativos mediante interceptor HTTPS local estricto. Nunca habilitar allowUniversalAccessFromFileURLs, Internet innecesario, file:// o comodines de orígenes. Medir y registrar versión WebView/API; cubrir inicio, offline, worker si se integra después, cámara/persistencia, interrupción y cancelación.
7. Mostrar al usuario capturas reales del acabado antes de otra APK. No prometer aprobación automática por usar bibliotecas premium.
8. CI/firma están bajo coordinación del agente padre. No hay workflows ni material de firma transferidos; `scripts/build-apk.sh` es referencia de construcción y requiere clave estable autorizada por fuera. No generar claves sustitutas, cambiar identidad de paquete o publicar releases sin autorización. No afirmar APK compatible hasta verificar firma, versión y prueba nativa.

`SOURCE-MANIFEST.json` permite verificar todos los archivos del paquete excepto sí mismo. La huella SHA256 del ZIP se entrega por separado para no crear autorreferencia.

## Saneamiento del paquete transferido
Esta copia elimina marca de empresa y referencia a una orden de trabajo en comentarios. El atril inicial y formulario usan parámetros ficticios independientes y redondos: largo2000mm, ancho1000mm, radio800mm; perfiles y posiciones son convenciones sintéticas de demostración, no extraídas de planos. No se consultaron ni copiaron planos, estándares empresariales, CAD, correos o fotos privados para este paquete. Las fuentes privadas originales permanecen intactas. Las licencias de terceros conservan sus atribuciones exigidas.
