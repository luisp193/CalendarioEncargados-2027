# CalendarioEncargados-2027

App Android personal de Luis para consultar el calendario de turnos de 12 h de los 5 encargados (JUANAN, CARLOS, AITOR, IÑAKI, LUIS) en 2027 y apuntar sus vacaciones.

## Normas
- **Versión:** `version.txt` en la raíz. Súbelo +1 en cada cambio que se compile, antes de hacer push. El número va en el nombre del APK (`Calendario_V<n>.apk`), en `app_name` y en `versionName` (lo hace `app/build.gradle.kts` solo leyendo `version.txt`). Enlace fijo: `Calendario.apk`.
- **Compilación:** no hay Java ni Android SDK en el PC. Se compila en GitHub Actions (`.github/workflows/build.yml`, push a `main` o a mano). El APK queda en la release `ultimo-apk`.
- **Firma fija:** la llave NO esta en el repo (el repo es publico). Viene de los secrets
  `KEYSTORE_BASE64` y `KEYSTORE_PASSWORD`; el workflow reconstruye `app/calendario.keystore`
  antes de compilar y lo borra al acabar. Alias `calendario`. Con `versionCode = GITHUB_RUN_NUMBER`
  se instala encima sin desinstalar. Si compilas en local sin la llave, se firma con la de
  depuracion y ese APK NO sirve para actualizar encima del instalado.
- La llave anterior (alias `claves`, compartida con la app Claves) estuvo subida a este repo
  publico con su contrasena, asi que se dio por comprometida y se cambio por otra el 19/11/2026.
  Sigue en el historial de Git: no vale volver a usarla. Por eso hay que desinstalar la app una
  vez al pasar a V9.
- Paquete `com.luis.calendario` · minSdk 26 · Kotlin + Jetpack Compose, sin base de datos (SharedPreferences).
- El Excel original (`CALENDARIO 2027 (12h)ENCARGADOS.xlsx`) NO se sube a Git (`.gitignore`); los datos van ya extraídos en `app/src/main/assets/calendario2027.json`.

## Datos (app/src/main/assets/calendarios.json)
- `years[]`: 2026 y 2027 sacados del Excel (`year`, `target`, `holidays`, `people[]` con `name` y `plan`).
- `plan`: un carácter por día del año. `M` mañana 8h · `T` tarde 8h · `N` noche 8h · `D` día 12h · `P` noche 12h · `A` partido (8h) · `.` libre.
- `palette` + `fill` (por persona) + `hfill` (cabecera): color de fondo REAL de cada casilla del Excel, también de las vacías (en el Excel toda la columna de fin de semana va en rojo y la de festivo en rosa). `fill` = una letra por día (`a` = palette[0]…, `-` = sin color). La app pinta exactamente eso; las vacaciones (V) van encima en verde. En los años de previsión: fin de semana = columna roja, turnos con su color.
- La rejilla (Calendario y vista anual) ignora la escala de letra del sistema (`SinEscalaLetra`) para que los textos no se corten.
- `holidays`: `0/1` por día; festivo = día con alguna casilla rosa en el Excel.
- Fila de LUIS: mismos colores que el Excel; se marca con borde grueso negro, letra más grande y negrita, fila más alta y ★.
- Verificado celda a celda contra el Excel (turnos, color de fondo de todas las casillas y recuentos mensuales de M/T/N/D/P) en 2026 y 2027: 0 diferencias.
- **Horas objetivo:** 2026 = 1635 h (1592 + 43, lo dijo Luis; el Excel pone 1638). 2027 en adelante = 1592 h.
- Sobrantes iniciales = (horas del calendario − objetivo) / 8. En 2027 coinciden con el Excel (JUANAN 19, CARLOS 22, AITOR 18, IÑAKI 21,5, LUIS 19,5).
- **2028–2040 (previsión):** se generan con `cycle` (ciclo de 5 semanas = 35 días, empezando el 2026-01-01; comprobado sin fallos contra 2026 y 2027). Sin festivos (no se pueden predecir; son de la empresa) y con objetivo 1592. La app los marca como PREVISIÓN.
- Para regenerar el JSON desde el Excel: se leyó el XML de las hojas "CALENDARIO 12H - 2026/2027" (bloques de 10 filas por mes, filas +1..+5 = encargados, columnas B..AF = días).
- Las vacaciones se guardan por fecha (epochDay) en SharedPreferences (`v2`), para todos los años. `v` es el formato antiguo (índices de 2027) y se migra solo.

## Reglas de negocio
- Colores del Excel: M naranja `FFC000`, T azul `00B0F0`, N gris `BFBFBF`, D/P rojo `FF0000` (fin de semana) o rosa `FF33CC` (festivo), Partido verde `92D050`.
- Vacaciones: casilla verde `99FF66` con **V** negra en mayúscula. Se guardan en el móvil.
- Cada día de vacaciones sobre un turno de 8 h descuenta 1 día sobrante; sobre uno de 12 h descuenta 1,5 (un fin de semana trabajado = 24 h = 3 jornadas de 8 h). Vacaciones en día libre no descuentan.
- La fila de **LUIS** siempre va más marcada (borde grueso negro, letra grande y en negrita, estrella ★).

## Interacción del Calendario
- Tocar un día (casilla) → abre la pantalla Día con esa fecha. Mantener pulsada una casilla 2 s → diálogo para apuntar/quitar vacaciones eligiendo rango (Desde/Hasta). Mantener pulsado un nombre 2 s → año completo.
- Al pasar de diciembre con "Sig." se va a enero del año siguiente (y de enero con "Ant." a diciembre del anterior).
- Columna de nombres estrecha (62 dp, `NombreChip(compacto = true)`) para que las casillas de los días sean más anchas en horizontal.
- Barra inferior propia (50 dp, iconos de 24 sp) en vez de NavigationBar, para dejar más sitio al calendario en vertical.

## Estructura
- `Data.kt`: modelo (`Cal` = un año, `Calendarios` = todos los años), reglas de turnos, almacén de vacaciones (`VacStore`) y su vista por año (`Vacaciones`). Selector de año ◀ ▶ arriba en la app.
- `Anual.kt`: vista del año completo de un encargado (ajustada para entrar entera en pantalla, zoom con pellizco, doble toque restaura, sin barra inferior) (se abre manteniendo pulsado 2 s su nombre en el Calendario).
- Horizontal: el mes entero cabe en pantalla (ancho de casilla calculado); la Activity gestiona el giro con `configChanges` para no perder el estado.
- `Ui.kt`: pantallas Compose: Calendario (rejilla del mes), Día (quién trabaja y en qué turno), Vacaciones, Buscar (filtros + texto libre) y Resumen (horas, sobrantes y acumulados por mes).
