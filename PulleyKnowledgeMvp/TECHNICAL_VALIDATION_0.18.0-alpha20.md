# Validación técnica 0.18.0-alpha20

## Alcance de esta iteración

Esta iteración parte del commit `705691d88370b820dacd79e9ae80b523acfc6db3` de `product/single-device-photogrammetry-v1`. No reemplaza el proyecto ni fusiona con `main`.

El objetivo inmediato es separar el estado real del código de las afirmaciones de producto, reforzar la calidad de correspondencias y preparar una compilación reproducible para validación física posterior.

## Clasificación de funciones

| Área | Estado | Evidencia actual | Límite |
|---|---|---|---|
| Camera2 y JPEG | Implementado en Android | Código y APK compilada | Falta prueba sistemática en Samsung A15 y Honor X5C; la alpha19 selecciona 3–12,5 MP, no siempre la resolución nativa máxima. |
| Dos anillos/12 sectores/30 fotos | Implementado | Lógica Android, SQLite y pruebas puras | Falta recorrido físico completo y recuperación bajo interrupciones reales. |
| Metadatos, IMU y calidad | Implementado | Persistencia y gates locales | Sensores son priors; no sustituyen pose visual calibrada. |
| SHA-256 y ZIP | Implementado | Código Android y SAF | Falta ensayo de archivos grandes y espacio insuficiente en hardware. |
| Features y matching | Implementado, experimental | Harris + descriptor binario de 64 bits; ratio, simetría y cobertura espacial | Sensible a superficies repetitivas, reflejos, pintura uniforme y cambios fuertes de escala/rotación. |
| Matriz fundamental | Implementado y probado sintéticamente | Ocho puntos normalizados, rank-2, RANSAC, Sampson | Falta banco de imágenes reales con ground truth. |
| Matriz esencial y pose | Implementado y probado sintéticamente | Cuatro soluciones, cheirality y paralaje | Intrínsecos Camera2 no equivalen a calibración completa de distorsión. |
| Triangulación | Implementado y probado sintéticamente | DLT, profundidad, paralaje y reproyección | No existe refinamiento no lineal por observación. |
| Tracks multivista | Implementado y probado sintéticamente | Unión de observaciones de 3+ imágenes | Falta validación contra errores de asociación en texturas repetitivas. |
| Grafo global de poses | Parcial | Propagación por árbol confiable y auditoría de ciclos | No hay optimización global de pose graph ni bundle adjustment. |
| Nube dispersa | Parcial | Fusión robusta de puntos triangulados | Las poses y puntos no se optimizan conjuntamente. |
| Cilindro del manto | Implementado, experimental | RANSAC y escala conocida | Falta propagación completa de covarianzas y validación de ovalidad con patrón físico. |
| STEP/OCCT | Compilado e incluido | AAR verificado y bibliotecas OCCT en APK | La prueba CI valida empaquetado; ejecución JNI real debe verificarse en teléfono con STEP de taller. |
| Ensamblaje CAD | Implementado | Transformaciones rígidas, restricciones y exportación | Falta validar grandes ensamblajes, unidades erróneas y piezas mal posicionadas en dispositivo. |
| Metrología industrial | No validada | Ninguna cadena física trazable todavía | Prohibido declarar liberación dimensional o precisión industrial. |

## Hallazgos críticos

1. La rama `product/single-device-photogrammetry-v1` contiene más código funcional que `agent/core-step-tracking-foundation` y `main` y es la base correcta.
2. El README anterior era contradictorio: simultáneamente describía funciones integradas y las marcaba como no implementadas.
3. La alpha19 no captura necesariamente el JPEG de mayor resolución; selecciona una resolución entre 3 y 12,5 megapíxeles.
4. El matching anterior podía aprobar muchas correspondencias concentradas en una zona pequeña. Alpha20 añade distribución espacial en detección y en el gate del par.
5. El ratio test y la simetría sí existían, pero el segundo vecino no se trataba explícitamente cuando no estaba disponible.
6. La matriz fundamental usa normalización y rank-2; la pose esencial evalúa las cuatro soluciones y aplica cheirality.
7. El denominado grafo global no realiza bundle adjustment: propaga poses por las mejores aristas y mide residuos de ciclos.
8. La escala de pares usa un prior de órbita derivado de sectores/IMU. Debe mantenerse como prior y nunca como medida absoluta.
9. La APK alpha19 contiene únicamente `arm64-v8a`. Agregar `armeabi-v7a` requiere reconstruir y validar todo el cierre OCCT para 32 bits.
10. El CI actual es fuerte para regresiones sintéticas y empaquetado, pero no sustituye instalación, ejecución JNI, temperatura, memoria o repetibilidad física.

## Cambios alpha20

- Distribución de características en una grilla 8 × 6 con segunda pasada de relleno.
- Correspondencia mutua y ratio test conservados.
- Gate de cobertura de correspondencias en grilla 6 × 4.
- Prueba negativa para impedir que un parche localizado sea clasificado como geometría utilizable.
- Versión incrementada a `0.18.0-alpha20`.
- README corregido para reflejar implementación y límites reales.

## Pruebas que siguen siendo obligatorias

- ruido y outliers con varios niveles en matriz fundamental;
- escenas casi planas y degeneración homográfica;
- líneas base pequeñas y paralaje insuficiente;
- órbita completa alrededor de un cilindro con ground truth;
- tracks con asociaciones contradictorias y cierres de ciclo corruptos;
- bundle adjustment local y global, cuando sea implementado;
- cilindro con outliers, ovalidad y eje descentrado;
- referencias de escala contradictorias;
- STEP con orientación, unidades y tamaños distintos;
- falta de memoria, cierre de proceso y recuperación de sesión;
- campañas Android 10–15 en Samsung A15 y Honor X5C;
- ejecución real de `STEPControl_Reader`, teselación y autoprueba OCCT en dispositivo;
- ensayo térmico y consumo de memoria durante 30–50 imágenes y reconstrucción completa;
- repetibilidad contra patrones dimensionales trazables.

## Criterio de liberación

Una APK puede distribuirse como alpha de evaluación cuando CI compila y audita su contenido. No puede declararse apta para metrología industrial hasta completar y documentar pruebas físicas, incertidumbre, repetibilidad y comparación contra instrumentos trazables.
