# Estado actual de iteraciones

**Actualizado:** 2026-07-21  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha33`

## Estado acumulado

- Alpha33 compila para `arm64-v8a` con STEP/OCCT.
- Evidencia dimensional, identificación, safety gate, BA local, productores visuales y pose graph mantienen gates automáticos.
- La ventana BA sigue limitada a 8 cámaras, 120 puntos y 1500 observaciones con cámara global 0 fija.
- Competencia homografía/fundamental, desenfoque, reflejos y repetición gobiernan la admisión del BA.
- Métricas y ventana BA reutilizan una preparación compartida de frames, gray, features e intrínsecos.
- Deadline de 180 segundos y cancelación visible conservan fallback sin optimizar.
- La cancelación se observa dentro de detección Harris y matching de descriptors mediante checkpoints profundos.
- Cada ejecución se prepara en una generación `.pending` y solo se vuelve vigente tras promoción a `.committed` y reemplazo del puntero activo.
- Una cancelación elimina la ventana parcial y publica una generación `ABORTED` separada.
- El exportador solo incluye la generación activa comprometida; sesiones anteriores conservan compatibilidad legacy.
- El manifiesto de campaña vincula versión, SHA-256 del APK instalado, dispositivo, SDK, sesión, código, OT, frames y evidencia runtime.
- La huella es reproducible, pero no constituye firma digital ni validación metrológica.
- No existe todavía campaña física completada ni calificación industrial.

## Avance global estimado

- **Avance integral: 89 %.**
- **Madurez alpha: 98 %.**
- **Preparación industrial/metrológica: 22 %.**

El incremento corresponde a cancelación profunda, recuperación transaccional y trazabilidad reproducible. La preparación industrial permanece baja porque Samsung A15, Honor X5C e instrumentos trazables no fueron ejecutados.

## Iteración actual o última cerrada

### ITER-015 — Checkpoints profundos, recuperación transaccional y campaña reproducible

Registro: `history/ITER-015_2026-07-21_deep-cancellation-transactional-evidence.md`

- checkpoints dentro de detección y matching;
- enlace opcional que conserva gates Java históricos;
- generaciones `.pending`/`.committed` y puntero activo;
- rollback sin sustituir la última generación válida;
- aborto sin ventana BA parcial;
- manifiesto canónico con APK, dispositivo, sesión y hashes;
- exportación exclusiva de evidencia comprometida;
- paquete de sesión actualizado a esquema 5;
- gate v58 incorporado;
- producto run `#670`: `success`;
- 32 gates Java;
- APK alpha33 publicada;
- uso industrial continúa bloqueado.

## Bloqueos activos

1. `RUNTIME-003`: fundamental, pose, triangulación y Bitmap decode no tienen checkpoints internos;
2. `TX-001`: no existe transacción coordinada entre SQLite y filesystem;
3. `SIGN-001`: la huella de evidencia no está firmada por una identidad corporativa;
4. `DEVICE-001`: campaña Samsung A15 no ejecutada;
5. `DEVICE-002`: campaña Honor X5C no ejecutada;
6. `JNI-001`: historial de salidas nativas no constituye cobertura JNI exhaustiva;
7. `BA-001`: rotaciones e intrínsecos permanecen fijos;
8. `BA-003`: no existe BA global;
9. `VALID-001`: metrología trazable ausente;
10. `DATA-007`: hashes reales de todos los PDF pendientes;
11. `ABI-001`: OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-016 — Rotaciones BA acotadas e incertidumbre estadística

Agregar rotaciones pequeñas con priors y damping al BA local, mantener cámara 0 fija y producir intervalos estadísticos de residuos claramente diferenciados de incertidumbre metrológica.

## Criterios de entrada

- conservar los 32 gates previos;
- mantener límites 8/120/1500 y cámara 0 fija;
- conservar generaciones transaccionales y manifiesto reproducible;
- no optimizar intrínsecos todavía;
- no declarar campañas físicas no ejecutadas;
- mantener PR en borrador.

## Criterios de salida

- rotaciones acotadas incorporadas al BA local;
- gauge y priors rotacionales verificados;
- aceptación exige mejora de reproyección y profundidad;
- intervalos estadísticos diferenciados de metrología;
- fallback por degeneración probado;
- alpha34 compilada;
- CI producto e historial exitosos;
- ITER-016 archivada;
- este archivo actualizado con ITER-017.