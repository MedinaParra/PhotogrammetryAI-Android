# Roadmap de ingeniería — SKM Polea AI

**Estado del documento:** activo  
**Rama de trabajo:** `agent/photogrammetry-validation-alpha20`  
**Base de producto:** `product/single-device-photogrammetry-v1`  
**Última revisión:** 2026-07-21  
**Alcance:** aplicación Android offline, fotogrametría de poleas, base histórica código–OT–plano y comparación CAD/STEP.

## 1. Propósito del producto

SKM Polea AI debe transformarse en una herramienta de ingeniería capaz de:

1. capturar una sesión fotográfica trazable con un único teléfono Android;
2. reconstruir experimentalmente la geometría visible de una polea;
3. estimar manto, eje y componentes principales con incertidumbre explícita;
4. identificar familias históricas mediante código de material, OT, plano y revisión;
5. comparar la reconstrucción con cotas aprobadas y componentes STEP;
6. impedir conclusiones industriales cuando la evidencia sea incompleta o contradictoria;
7. operar completamente en local, sin enviar fotografías ni geometría a servidores.

La aplicación no se considerará apta para liberación metrológica hasta completar campañas físicas trazables y documentadas.

## 2. Principios no negociables

- **El plano aprobado gobierna.** Informes, WIP y nombres de archivo son evidencia secundaria.
- **Una OT no sobrescribe a otra.** Las dimensiones deben versionarse por OT, número de plano y revisión.
- **Código de material no implica identidad geométrica por sí solo.** También deben coincidir rol, cliente, superficie medida y cotas críticas.
- **La superficie comparada debe declararse.** `BARE_SHELL` y `OUTER_LAGGING` no son intercambiables.
- **No se inventan cotas faltantes.** La ausencia de evidencia produce `BLOCKED` o `REVIEW`, nunca una coincidencia automática.
- **Las pruebas sintéticas no equivalen a validación física.** Cada afirmación debe indicar su nivel de evidencia.
- **Todo resultado debe ser reproducible.** Fotografías, hashes, parámetros, versión del algoritmo, plano y decisión humana deben quedar registrados.

## 3. Regla dimensional de identificación

La puerta primaria para poleas es el radio exterior observado:

```text
radio_observado = radio de la superficie realmente visible
radio_referencia = radio exterior aprobado para OT/plano/revisión
error_radio_mm = abs(radio_observado - radio_referencia)
```

Estados:

- `MATCH`: `error_radio_mm <= 30` y no existen contradicciones críticas.
- `REVIEW`: el radio cumple, pero existe ambigüedad de revisión, revestimiento, unidad, alias, OT o cota axial.
- `BLOCKED`: `error_radio_mm > 30`, falta radio aprobado, se confundió radio con diámetro, o hay planos vigentes incompatibles.

Una diferencia de 30 mm en radio equivale a 60 mm en diámetro, pero el motor debe almacenar y evaluar explícitamente el radio para evitar errores de interpretación.

El radio es una condición necesaria, no suficiente. También se deben comprobar, cuando estén disponibles:

- largo de manto o cara;
- distancia entre centros de soportes;
- distancia entre centros de rodamientos;
- largo total del eje;
- diámetros de zonas de rodamiento y fijación;
- tipo y espesor de revestimiento;
- rol de la polea y equipo/posición.

## 4. Modelo de datos objetivo

```text
MaterialFamily
├── materialCode
├── aliases[]
├── clients[]
├── roleHints[]
├── WorkOrder[]
│   ├── otNumber
│   ├── componentDescription
│   ├── purchaseOrder
│   ├── Drawing[]
│   │   ├── drawingNumber
│   │   ├── revision
│   │   ├── approvalState
│   │   ├── sourceUri
│   │   ├── sourceSha256
│   │   └── DimensionEvidence[]
│   └── InterventionEvent[]
├── DerivedNominalEnvelope
└── ValidationConflict[]
```

Cada `DimensionEvidence` debe conservar:

- tipo de dimensión;
- valor y unidad originales;
- valor normalizado en milímetros;
- superficie de referencia;
- tolerancia de plano;
- OT, plano y revisión;
- documento fuente y SHA-256;
- método de extracción;
- confianza automática;
- estado de revisión humana;
- fecha y responsable de aprobación.

La clave lógica mínima será:

```text
material_code + ot_number + drawing_number + revision + dimension_kind + surface_kind
```

## 5. Estado actual comprobado

### Base técnica disponible

- Captura Camera2, sesiones SQLite, IMU, calidad local, SHA-256 y ZIP.
- Correspondencias visuales con ratio, simetría, cobertura y coherencia.
- Fundamental, esencial, pose, triangulación, tracks y grafo de poses experimental.
- Ajuste cilíndrico y escala conocida.
- Importación STEP con OCCT/JNI para `arm64-v8a`.
- Base histórica inicial con ocho códigos de material.

### Deuda crítica

- No existe bundle adjustment local/global.
- El descriptor visual es experimental y débil ante rotación, escala, reflejos y patrones repetitivos.
- Camera2 no siempre solicita el JPEG nativo máximo.
- Falta calibración de distorsión por dispositivo.
- La base mezcla dimensiones de familia con dimensiones específicas de OT.
- Varias familias carecen de radio exterior aprobado.
- `4162045` presenta asociaciones documentales incompatibles y debe permanecer bloqueado.
- No existe campaña física completa en Samsung A15 y Honor X5C.
- `armeabi-v7a` no está disponible en el cierre OCCT actual.

## 6. Fases del roadmap

## R0 — Gobierno de evidencia y trazabilidad

**Objetivo:** impedir que datos ambiguos ingresen como verdad técnica.

Entregables:

- jerarquía formal de fuentes;
- estados `DRAFT`, `REVIEWED`, `APPROVED`, `REJECTED`;
- SHA-256 por documento fuente;
- política de revisiones y obsolescencia;
- registro de conflictos sin borrar evidencia histórica;
- catálogo de unidades y superficies.

Puerta de salida:

- 100 % de las cotas activas apuntan a un documento, OT, plano y revisión;
- ninguna dimensión inferida se presenta como medición aprobada.

## R1 — Migración del modelo dimensional

**Objetivo:** separar familia, intervención y revisión.

Entregables:

- tablas `material_family`, `work_order`, `drawing`, `drawing_revision`, `dimension_evidence`, `validation_conflict`;
- migración idempotente desde la semilla actual;
- compatibilidad de lectura con sesiones existentes;
- consultas por código, OT, revisión y tipo de dimensión;
- pruebas de migración y rollback lógico.

Puerta de salida:

- una OT nueva no modifica las cotas de otra OT;
- las revisiones antiguas siguen auditables;
- ningún registro pierde su URI de origen.

## R2 — Saneamiento de familias históricas

**Objetivo:** construir una base de referencia confiable.

Prioridad:

1. `4162045`: separar equipos, alias y posibles errores de codificación;
2. `4196111`: localizar plano de conjunto y cargar dimensiones;
3. `4196149`: convertir y conservar cotas originales en pulgadas;
4. `1462827`: agregar OT-1632 y OT-1712 y cargar Ø1016/Ø1046;
5. `4162054`: registrar Ø609,60 y Ø673,10;
6. `10415863`: separar OT-262 de OT-1702;
7. `10415860`: separar OT-243 de OT-1645;
8. `10510386`: mantener bloqueado hasta encontrar plano dimensional.

Puerta de salida:

- cada familia habilitada tiene al menos un radio exterior aprobado;
- los códigos conflictivos no pueden producir `MATCH` automático;
- existe una tabla de conflictos resueltos y pendientes.

## R3 — Motor de identificación geométrica

**Objetivo:** decidir coincidencias sin depender solo de nombres o códigos.

Entregables:

- comparación explícita de radios con umbral máximo de 30 mm;
- manejo de manto desnudo y revestimiento exterior;
- normalización segura de pulgadas y milímetros;
- puntuación multivariable para cotas axiales;
- bloqueo por revisión desconocida;
- explicación legible de cada decisión;
- pruebas de frontera en 29, 30 y 31 mm.

Puerta de salida:

- ninguna diferencia superior a 30 mm produce `MATCH`;
- ninguna cota en pulgadas se interpreta como milímetros;
- el resultado incluye fuentes y razones de aceptación/rechazo.

## R4 — Fotogrametría robusta para taller

**Objetivo:** mejorar correspondencias y reconstrucción bajo condiciones reales.

Entregables:

- descriptor con mayor robustez a rotación y escala;
- detección de degeneración homográfica y bajo paralaje;
- rechazo de reflejos, desenfoque y zonas repetitivas;
- selección adaptativa de pares y keyframes;
- conjunto de imágenes reales anonimizadas con ground truth;
- métricas de reproyección, cobertura y conectividad.

Puerta de salida:

- reconstrucción repetible en al menos tres poleas de geometría distinta;
- fallos geométricos se bloquean en lugar de producir modelos plausibles falsos.

## R5 — Optimización y calibración

**Objetivo:** reducir error geométrico acumulado.

Entregables:

- bundle adjustment local;
- bundle adjustment global;
- optimización no lineal del grafo de poses;
- calibración intrínseca y distorsión por dispositivo;
- propagación de incertidumbre hacia radio, longitud y centros;
- almacenamiento de perfiles de cámara versionados.

Puerta de salida:

- convergencia determinista en bancos sintéticos y reales;
- mejora cuantificada frente al pipeline sin optimización;
- incertidumbre reportada con supuestos explícitos.

## R6 — Robustez Android y campaña de dispositivos

**Objetivo:** demostrar operación estable en hardware objetivo.

Dispositivos mínimos:

- Samsung A15;
- Honor X5C.

Ensayos:

- 30–50 fotografías por sesión;
- pausa/reanudación, rotación y cambio de aplicación;
- cierre forzado y recuperación;
- espacio insuficiente;
- memoria, temperatura y duración;
- captura máxima configurable;
- permisos y almacenamiento en Android 10–15;
- ejecución STEP/JNI y teselación real.

Puerta de salida:

- cero pérdida silenciosa de fotografías;
- sesiones interrumpidas recuperables;
- consumo térmico y memoria documentados;
- autoprueba STEP aprobada en ambos dispositivos.

## R7 — STEP, componentes y ensamblaje

**Objetivo:** usar CAD histórico como evidencia geométrica sin deformarlo.

Entregables:

- manejo explícito de unidades STEP;
- grandes archivos y ensamblajes;
- errores JNI y memoria controlados;
- posicionamiento rígido de soportes, rodamientos, manguitos y acoplamientos;
- comparación reconstrucción–STEP con residuos;
- exportación sin destruir el original;
- estudio de factibilidad para `armeabi-v7a`.

Puerta de salida:

- conjunto representativo de STEP de taller importado en dispositivo;
- errores de unidad/orientación producen `REVIEW` o `BLOCKED`;
- ninguna pieza se escala no uniformemente para forzar coincidencia.

## R8 — Calificación de ingeniería

**Objetivo:** determinar si el sistema puede apoyar decisiones dimensionales reales.

Entregables:

- protocolo de repetibilidad y reproducibilidad;
- comparación contra huincha, pie de metro, micrómetro o instrumentos trazables según dimensión;
- análisis de incertidumbre;
- criterios de aceptación por uso;
- informe de limitaciones y responsabilidades;
- versión de evaluación firmada y reproducible.

Puerta de salida:

- evidencia física suficiente para el uso declarado;
- ninguna afirmación de precisión excede los resultados medidos;
- aprobación técnica y de calidad antes de uso industrial.

## 7. Protocolo obligatorio de iteraciones

Cada iteración debe crear un archivo inmutable en `docs/iterations/history/` y actualizar `docs/iterations/CURRENT.md`.

El registro debe incluir como mínimo:

- identificador y fecha;
- objetivo y alcance;
- commit base y rama;
- archivos o módulos modificados;
- decisiones técnicas;
- evidencia y pruebas ejecutadas;
- resultados medibles;
- fallos y riesgos abiertos;
- deuda técnica introducida;
- estado del roadmap;
- siguiente iteración obligatoria;
- criterios de entrada y salida de esa siguiente iteración.

Una iteración no se considera cerrada si no declara explícitamente qué debe continuar.

## 8. Estrategia de versiones

- `alpha`: implementación y pruebas sintéticas; no apta para uso dimensional.
- `device-alpha`: pruebas reales de captura/STEP en dispositivos objetivo.
- `engineering-beta`: identificación y reconstrucción con campaña física parcial.
- `validation-candidate`: protocolo metrológico ejecutado, aún sujeto a revisión.
- `qualified`: únicamente después de aprobación técnica y de calidad para un uso claramente delimitado.

## 9. Próxima iteración obligatoria

### ITER-003 — Modelo OT/plano/revisión y puerta de radio

Objetivos:

1. implementar entidades separadas para OT, plano, revisión y superficie;
2. migrar sin pérdida la semilla existente;
3. introducir `BARE_SHELL_RADIUS` y `OUTER_LAGGING_RADIUS`;
4. implementar la puerta dura de ±30 mm en radio;
5. agregar pruebas de 29/30/31 mm, pulgadas, revisiones y superficies incompatibles;
6. cargar como casos iniciales los planos auditados de `10415863`, `10415860`, `4162054`, `4196149` y `1462827`;
7. mantener `4162045`, `4196111` y `10510386` en `BLOCKED` hasta completar evidencia.

Criterio de cierre:

- migración reproducible;
- pruebas exitosas;
- decisiones explicables con fuente;
- ninguna familia incompleta produce `MATCH` automático;
- registro de ITER-003 actualizado con la siguiente prioridad.