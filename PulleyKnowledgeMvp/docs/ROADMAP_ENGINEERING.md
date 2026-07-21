# Roadmap de ingeniería — SKM Polea AI

**Estado:** activo  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**Base de producto:** `product/single-device-photogrammetry-v1`  
**Actualizado:** 2026-07-21  
**Última iteración cerrada:** `ITER-006`  
**Siguiente iteración:** `ITER-007`  
**Avance integral estimado:** `55 %`

## 1. Propósito del producto

SKM Polea AI debe operar completamente en Android y de forma local para:

1. capturar sesiones fotográficas trazables de poleas;
2. reconstruir experimentalmente su geometría visible;
3. estimar manto, eje y componentes con incertidumbre explícita;
4. identificar referencias históricas por código, OT, plano y revisión;
5. comparar reconstrucción, cotas aprobadas y componentes STEP;
6. impedir decisiones cuando la evidencia sea insuficiente o contradictoria;
7. conservar archivos, hashes, parámetros, decisiones y resultados reproducibles.

La aplicación sigue siendo una alpha de ingeniería. No está autorizada para liberación dimensional ni metrología industrial.

## 2. Principios no negociables

- El plano aprobado gobierna sobre nombres, informes, WIP y aliases.
- Una OT o revisión nunca sobrescribe a otra.
- Código de material no demuestra identidad geométrica por sí solo.
- `BARE_SHELL` y `OUTER_LAGGING` son superficies distintas.
- Una ausencia de evidencia produce `REVIEW` o `BLOCKED`, nunca una coincidencia inventada.
- Una contradicción crítica domina cualquier puntuación promedio.
- La IMU y la órbita guiada son priors, no verdad geométrica.
- Pruebas sintéticas y compilación no equivalen a validación física.
- Un perfil de cámara no es una calibración hasta ser validado con un patrón físico.
- Todo optimizador debe declarar alcance, gauge, priors, límites y métricas antes/después.

## 3. Regla dimensional de identificación

```text
radio_observado = radio de la superficie realmente visible
radio_referencia = radio aprobado para OT/plano/revisión/superficie
error_radio_mm = abs(radio_observado - radio_referencia)
```

Estados:

- `MATCH`: `error_radio_mm <= 30` y no existe contradicción crítica.
- `REVIEW`: el radio cumple, pero faltan cotas o existe ambigüedad recuperable.
- `BLOCKED`: el radio supera 30 mm, falta una referencia primaria o hay evidencia incompatible.

La diferencia de 30 mm se evalúa en radio, no en diámetro. El radio es una condición necesaria, pero también deben evaluarse cuando existan:

- largo de manto o cara;
- centros de soportes y rodamientos;
- largo total de eje;
- diámetros de zonas de rodamiento, fijación y soporte;
- tipo y espesor de revestimiento;
- equipo, posición, OT, plano y revisión.

## 4. Arquitectura de evidencia y decisiones

```text
MaterialFamily
└── WorkOrder
    └── Drawing
        └── DrawingRevision
            └── DimensionEvidence

IdentificationDecisionAudit
├── observaciones
├── referencias
├── residuos y tolerancias
├── fuentes
├── estado y puntuación
└── huella reproducible
```

La clave mínima de evidencia es:

```text
material_code + ot_number + drawing_number + revision + dimension_kind + surface_kind
```

Cada evidencia conserva valor y unidad originales, valor en milímetros, superficie, tolerancia, aprobación, fuente, hash, método y confianza.

## 5. Estado técnico comprobado

### Disponible

- captura Camera2, sesiones SQLite, IMU, calidad, SHA-256 de fotografías y ZIP;
- selección balanceada y presupuesto de recursos;
- features, matching, fundamental, esencial, pose, triangulación y tracks;
- grafo de poses experimental, nube dispersa y ajuste de cilindro;
- safety gate fail-closed `READY/REVIEW/BLOCKED`;
- BA local acotado de puntos y traslaciones con cámara 0 fija;
- perfiles Brown-Conrady versionados y almacenamiento SQLite;
- conocimiento revisionado por OT/plano/revisión;
- identificación multivariable y auditoría append-only;
- STEP/OCCT para `arm64-v8a`, teselación y ensamblaje rígido.

### Deuda crítica

- el safety gate y BA aún no gobiernan el pipeline runtime;
- no se optimizan rotaciones ni existe BA global;
- homografía, reflejos y ambigüedad repetitiva no se producen automáticamente por sesión;
- no hay perfiles calibrados físicamente para Samsung A15/Honor X5C;
- la interfaz de conocimiento heredada sigue activa;
- faltan SHA-256 reales de algunos PDF;
- tres familias históricas permanecen bloqueadas;
- no existe campaña física, térmica, de memoria o repetibilidad;
- `armeabi-v7a` no está disponible.

## 6. Fases del roadmap

## R0 — Gobierno de evidencia y trazabilidad — 82 %

Entregables principales:

- jerarquía de fuentes y aprobaciones;
- historial inmutable de iteraciones;
- auditoría append-only de decisiones;
- hashes reales por documento;
- conflictos conservados sin borrar evidencia.

Puerta pendiente: sustituir identificadores provisionales por hashes reales y exportar auditorías con las sesiones.

## R1 — Modelo dimensional revisionado — 85 %

Implementado:

- familia, OT, plano, revisión, evidencia y conflicto;
- unidades y superficies explícitas;
- siembra idempotente y rollback lógico mediante base separada;
- consultas tipadas desde SQLite.

Puerta pendiente: retirar la dependencia funcional de la interfaz heredada.

## R2 — Saneamiento de familias históricas — 50 %

Prioridades abiertas:

1. resolver `4162045`;
2. resolver `4196111` frente a `4162038`;
3. localizar radio aprobado de `10510386`;
4. completar hashes reales y OT adicionales;
5. cargar tolerancias específicas de plano.

Puerta: toda familia habilitada debe tener radio exterior aprobado, fuente verificada y ausencia de conflictos críticos.

## R3 — Identificación geométrica — 85 %

Implementado:

- puerta de radio de 30 mm;
- cotas axiales multivariables;
- separación entre evidencia faltante y contradicción;
- bloqueo de cruces OT/revisión;
- residuos, fuentes, puntuación y auditoría.

Puerta pendiente: tolerancias específicas por plano, integración visual completa y validación física.

## R4 — Fotogrametría robusta para taller — 68 %

Implementado:

- control de cobertura y coherencia;
- geometría multivista experimental;
- safety gate de paralaje, planaridad, grafo, ciclos, reproyección y degradación;
- adaptación desde el reporte actual.

Pendiente:

- producir homografía/reflejos/ambigüedad automáticamente;
- conectar el gate al flujo runtime;
- banco real de poleas con ground truth;
- descriptor más robusto a rotación, escala y superficies industriales.

## R5 — Optimización y calibración — 35 %

Implementado:

- BA local acotado;
- cámara 0 fija;
- puntos y traslaciones optimizados;
- pérdida Huber y priors;
- límites de cámaras, puntos y observaciones;
- perfiles Brown-Conrady versionados y fail-closed.

Pendiente:

- integración runtime;
- rotaciones y BA global;
- optimización eficiente tipo Schur;
- calibración física por dispositivo;
- propagación de incertidumbre dimensional.

## R6 — Robustez Android y dispositivos — 10 %

Dispositivos mínimos:

- Samsung A15;
- Honor X5C.

Pendiente:

- sesiones de 30–50 fotografías;
- pausa, rotación, cierre forzado y recuperación;
- memoria, temperatura, almacenamiento y duración;
- ejecución STEP/JNI y BA reales;
- Android 10–15.

## R7 — STEP, componentes y ensamblaje — 45 %

Disponible:

- importación STEP con OCCT/JNI;
- BRep, teselación, bounding box y normales;
- transformaciones rígidas;
- autoprueba y gates de empaquetado;
- comparación cuantitativa experimental.

Pendiente:

- grandes conjuntos reales;
- unidades y orientaciones diversas;
- pruebas de memoria/JNI en dispositivo;
- componentes de taller representativos;
- factibilidad `armeabi-v7a`.

## R8 — Calificación de ingeniería — 0 %

Pendiente completo:

- repetibilidad y reproducibilidad;
- comparación con instrumentos trazables;
- incertidumbre;
- criterios por uso;
- revisión de calidad y responsabilidades;
- versión calificada para un alcance delimitado.

## 7. Protocolo obligatorio de iteraciones

Cada iteración debe:

1. leer `docs/iterations/CURRENT.md` y la última iteración cerrada;
2. ejecutar un objetivo medible;
3. conservar archivos, commits, pruebas y resultados;
4. registrar fallos, riesgos y deuda sin ocultarlos;
5. crear un archivo inmutable en `docs/iterations/history/`;
6. actualizar `CURRENT.md` y este roadmap;
7. declarar una siguiente iteración concreta con criterios de entrada y salida;
8. pasar el gate `validate-iteration-history.yml`.

Una iteración no se considera cerrada sin CI, historial y siguiente paso.

## 8. Estrategia de versiones

- `alpha`: implementación y pruebas sintéticas.
- `device-alpha`: ejecución real controlada en teléfonos objetivo.
- `engineering-beta`: campañas físicas parciales y métricas comparativas.
- `validation-candidate`: protocolo metrológico ejecutado y en revisión.
- `qualified`: aprobación técnica y de calidad para un uso delimitado.

## 9. Próxima iteración obligatoria

### ITER-007 — Integración runtime del safety gate y BA local

Objetivos:

1. conectar el safety gate con `SessionOverlapAnalyzer` y la interfaz;
2. construir ventanas BA desde tracks, poses, intrínsecos y observaciones reales;
3. ejecutar BA solo cuando el gate sea `READY`;
4. persistir estado, parámetros, tiempo y RMS antes/después;
5. implementar fallback seguro ante divergencia o recursos insuficientes;
6. usar únicamente perfiles `VALIDATED` para corrección automática;
7. probar interrupción, memoria, recuperación y resultados no mejorados;
8. publicar `0.18.0-alpha25`.

Criterios de cierre:

- flujo runtime gobernado por el safety gate;
- BA alimentado por datos reales del pipeline;
- divergencia nunca reemplaza silenciosamente el resultado anterior;
- métricas y fuentes persistidas;
- pruebas y CI en `success`;
- ITER-007 archivada y ITER-008 declarada.
