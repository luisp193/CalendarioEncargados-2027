# CalendarioEncargados-2027

App Android personal de Luis para consultar el calendario de turnos de 12 h de los 5 encargados (JUANAN, CARLOS, AITOR, IÑAKI, LUIS) en 2027 y apuntar sus vacaciones.

## Normas
- **Versión:** `version.txt` en la raíz. Súbelo +1 en cada cambio que se compile, antes de hacer push. El número va en el nombre del APK (`Calendario_V<n>.apk`), en `app_name` y en `versionName` (lo hace `app/build.gradle.kts` solo leyendo `version.txt`). Enlace fijo: `Calendario.apk`.
- **Compilación:** no hay Java ni Android SDK en el PC. Se compila en GitHub Actions (`.github/workflows/build.yml`, push a `main` o a mano). El APK queda en la release `ultimo-apk`.
- **Firma fija:** `app/calendario.keystore` (misma clave que Claves) + `versionCode = GITHUB_RUN_NUMBER`, para instalar encima sin desinstalar.
- Paquete `com.luis.calendario` · minSdk 26 · Kotlin + Jetpack Compose, sin base de datos (SharedPreferences).
- El Excel original (`CALENDARIO 2027 (12h)ENCARGADOS.xlsx`) NO se sube a Git (`.gitignore`); los datos van ya extraídos en `app/src/main/assets/calendario2027.json`.

## Datos (calendario2027.json)
- `people[]`: `name`, `plan` (365 caracteres, uno por día del año) y `sobrantes` (días sobrantes del Excel).
- Códigos del plan: `M` mañana 8h · `T` tarde 8h · `N` noche 8h · `D` día 12h · `P` noche 12h · `A` partido (8h) · `.` libre.
- `holidays`: 365 caracteres `0/1`; festivo = día con turno 12h en día laborable (celdas rosas del Excel).
- Horas objetivo: 1592 por encargado. Sobrantes iniciales = (horas del calendario − 1592) / 8 (JUANAN 19, CARLOS 22, AITOR 18, IÑAKI 21,5, LUIS 19,5). Las horas calculadas coinciden con los totales del Excel.
- Para regenerar el JSON desde el Excel: se leyó el XML de la hoja "CALENDARIO 12H - 2027" (bloques de 10 filas por mes, filas +1..+5 = encargados, columnas B..AF = días).

## Reglas de negocio
- Colores del Excel: M naranja `FFC000`, T azul `00B0F0`, N gris `BFBFBF`, D/P rojo `FF0000` (fin de semana) o rosa `FF33CC` (festivo), Partido verde `92D050`.
- Vacaciones: casilla verde `99FF66` con **V** negra en mayúscula. Se guardan en el móvil.
- Cada día de vacaciones sobre un turno de 8 h descuenta 1 día sobrante; sobre uno de 12 h descuenta 1,5 (un fin de semana trabajado = 24 h = 3 jornadas de 8 h). Vacaciones en día libre no descuentan.
- La fila de **LUIS** siempre va más marcada (borde grueso negro, letra grande y en negrita, estrella ★).

## Estructura
- `Data.kt`: modelo (`Cal`, `Person`), reglas de turnos y almacén de vacaciones (`Vacaciones`).
- `Anual.kt`: vista del año completo de un encargado (se abre manteniendo pulsado 2 s su nombre en el Calendario).
- Horizontal: el mes entero cabe en pantalla (ancho de casilla calculado); la Activity gestiona el giro con `configChanges` para no perder el estado.
- `Ui.kt`: pantallas Compose: Calendario (rejilla del mes), Día (quién trabaja y en qué turno), Vacaciones, Buscar (filtros + texto libre) y Resumen (horas, sobrantes y acumulados por mes).
