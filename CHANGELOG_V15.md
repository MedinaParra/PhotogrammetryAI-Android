# v0.15 — Memoria de experiencia local (“Ingeniería Viva”)

Esta versión convierte cada inspección validada en evidencia reutilizable, sin permitir aprendizaje descontrolado.

## Componentes nuevos

- `ExperienceRecord`: sesión de campo inmutable con observaciones, relaciones, cotas y correcciones.
- `ExperienceCorrection`: registra diferencias entre predicción y confirmación humana.
- `LearnedGeometryProfile`: consolida confiabilidad por clase con suavizado bayesiano.
- `ExperienceLearningEngine`: adapta el ranking con límites estrictos y rechaza sesiones no verificadas.
- `ExperienceAwareGeometryAssistant`: fachada para personalizar detecciones y aprender de una sesión.
- `DeviceExperiencePassport`: pasaporte portable que cuantifica experiencia acumulada.
- `ExperiencePassportJsonWriter`: exporta el pasaporte sin fotografías ni datos sensibles.

## Seguridad del aprendizaje

- Una observación aislada no modifica fuertemente el sistema.
- Solo se consolida información confirmada por el operador y con calidad >= 0,65.
- El ajuste máximo de confianza por memoria es 0,12.
- La confianza nunca alcanza 100 % automáticamente.
- El “puntaje de experiencia” no es una tasación monetaria ni certifica precisión metrológica.

## Prueba

```bash
./tools/run_experience_v15_test.sh
```
