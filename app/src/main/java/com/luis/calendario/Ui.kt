@file:OptIn(ExperimentalMaterial3Api::class)

package com.luis.calendario

import android.app.DatePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

// ---------- Colores del Excel ----------
val CNaranja = Color(0xFFFFC000)   // Mañana
val CAzul = Color(0xFF00B0F0)      // Tarde
val CGris = Color(0xFFBFBFBF)      // Noche 8h
val CRojo = Color(0xFFFF0000)      // Día/Noche 12h (fin de semana)
val CRosa = Color(0xFFFF33CC)      // Día/Noche 12h festivo
val CPartido = Color(0xFF92D050)   // Partido
val CVacas = Color(0xFF99FF66)     // Vacaciones (V negra)
val CNegro = Color.Black

val ColorPersona = listOf(
    Color(0xFF99CC00), // JUANAN
    Color(0xFFB4C6E7), // CARLOS
    Color(0xFFFFD966), // AITOR
    Color(0xFFFFFF00), // IÑAKI
    Color(0xFFAEABAB), // LUIS
)

fun colorTurno(c: Char, festivo: Boolean): Color = when (c) {
    'M' -> CNaranja
    'T' -> CAzul
    'N' -> CGris
    'D', 'P' -> if (festivo) CRosa else CRojo
    'A' -> CPartido
    'V' -> CVacas
    else -> Color.Transparent
}

private val colorCache = HashMap<String, Color>()
fun hexColor(h: String?): Color =
    if (h == null) Color.White else colorCache.getOrPut(h) { Color(("FF$h").toLong(16)) }

/** Color real de la casilla (el de la celda del Excel). Vacaciones = verde. */
fun Cal.fill(p: Int, i: Int, c: Char): Color = if (c == 'V') CVacas else hexColor(fillHex(p, i))

/** Textos de la rejilla con tamaño fijo (sin la escala de letra del sistema), para que no se corten. */
@Composable
fun SinEscalaLetra(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, 1f)) { content() }
}

val DiaLetra = listOf("L", "M", "X", "J", "V", "S", "D")

fun cap(s: String) = s.replaceFirstChar { it.uppercase() }
fun fechaLarga(d: LocalDate) = cap(d.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES)))
fun fechaCorta(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("EEE d MMM", ES))
fun nombreMes(m: Int) = cap(Month.of(m).getDisplayName(TextStyle.FULL, ES))
fun quitaTildes(s: String) =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

fun pickDate(ctx: Context, cal: Cal, cur: LocalDate, onPick: (LocalDate) -> Unit) {
    val dp = DatePickerDialog(ctx, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) }, cur.year, cur.monthValue - 1, cur.dayOfMonth)
    val zone = ZoneId.systemDefault()
    dp.datePicker.minDate = LocalDate.of(cal.year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    dp.datePicker.maxDate = LocalDate.of(cal.year, 12, 31).atStartOfDay(zone).toInstant().toEpochMilli()
    dp.show()
}

fun hoyOInicio(cal: Cal): LocalDate {
    val h = LocalDate.now()
    return if (cal.inYear(h)) h else LocalDate.of(cal.year, 1, 1)
}

// ---------- Piezas comunes ----------
/** Toque corto = onTap; mantener pulsado 2 s = onLong. */
fun Modifier.tapOLargo(k1: Any?, k2: Any?, onTap: () -> Unit, onLong: () -> Unit): Modifier =
    pointerInput(k1, k2) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var estado = 1 // 0 = toque, 1 = largo, 2 = cancelado (scroll, etc.)
            withTimeoutOrNull(2000L) {
                val up = waitForUpOrCancellation()
                estado = if (up != null) 0 else 2
            }
            if (estado == 0) onTap()
            else if (estado == 1) {
                onLong()
                waitForUpOrCancellation()
            }
        }
    }

/** Pulsación larga de 2 segundos. */
fun Modifier.pulsar2s(onLong: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var timedOut = true
        withTimeoutOrNull(2000L) {
            waitForUpOrCancellation()
            timedOut = false
        }
        if (timedOut) onLong()
    }
}

@Composable
fun TurnoBox(c: Char, festivo: Boolean, size: Dp = 28.dp, destacado: Boolean = false, fondo: Color? = null) {
    Box(
        Modifier
            .size(size)
            .background(fondo ?: colorTurno(c, festivo))
            .border(if (destacado) 2.dp else 0.5.dp, if (destacado) CNegro else Color(0xFF888888)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            Turno.letra(c), color = CNegro, fontSize = 12.sp,
            fontWeight = if (destacado || c == 'V') FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun NombreChip(cal: Cal, p: Int, modifier: Modifier = Modifier, compacto: Boolean = false) {
    val luis = p == cal.luis
    Box(
        modifier
            .background(ColorPersona[p])
            .border(if (luis) 3.dp else 0.5.dp, if (luis) CNegro else Color(0xFF888888))
            .padding(horizontal = if (compacto) 3.dp else 8.dp, vertical = if (compacto) 0.dp else if (luis) 8.dp else 5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            (if (luis) (if (compacto) "★" else "★ ") else "") + cal.people[p].name, color = CNegro,
            fontWeight = if (luis) FontWeight.ExtraBold else FontWeight.Medium,
            fontSize = if (compacto) (if (luis) 12.sp else 11.sp) else if (luis) 16.sp else 14.sp,
            maxLines = 1
        )
    }
}

@Composable
fun Titulo(t: String) {
    Text(t, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
}

// ---------- App ----------
@Composable
fun App(cals: Calendarios, store: VacStore) {
    var tab by remember { mutableStateOf(0) }
    val hoy = LocalDate.now()
    var year by remember { mutableStateOf(if (hoy.year in cals.years) hoy.year else cals.primero) }
    val cal = remember(year) { cals.get(year) }
    val vac = remember(cal) { Vacaciones(store, cal) }
    var dia by remember { mutableStateOf(if (hoy.year == year) hoy else LocalDate.of(year, 1, 1)) }
    var anual by remember { mutableStateOf<Int?>(null) }
    val tabs = listOf("Calendario" to "📅", "Día" to "🔎", "Vacaciones" to "🌴", "Buscar" to "🔍", "Resumen" to "📊")

    fun cambiarAnio(y: Int) {
        if (y !in cals.years) return
        val m = dia.monthValue
        dia = LocalDate.of(y, m, minOf(dia.dayOfMonth, LocalDate.of(y, m, 1).lengthOfMonth()))
        year = y
    }

    MaterialTheme {
        Scaffold(
            bottomBar = {
                if (anual == null) Row(
                    Modifier.fillMaxWidth().background(Color(0xFFF1EEF6)).navigationBarsPadding().height(50.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { i, (n, ic) ->
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(if (tab == i) Color(0xFFD8CCF0) else Color.Transparent)
                                .clickable { tab = i },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(ic, fontSize = 24.sp, lineHeight = 26.sp)
                            Text(n, fontSize = 9.sp, maxLines = 1, lineHeight = 10.sp)
                        }
                    }
                }
            }
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                val an = anual
                if (an == null) {
                    Row(
                        Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFEDEDED)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { cambiarAnio(year - 1) }, enabled = year > cals.primero) { Text("◀") }
                        Text("$year", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        TextButton(onClick = { cambiarAnio(year + 1) }, enabled = year < Calendarios.ULTIMO) { Text("▶") }
                        if (cal.forecast) {
                            Text(
                                "PREVISIÓN: ciclo de 5 semanas, sin festivos", fontSize = 11.sp, maxLines = 2,
                                color = Color(0xFFB45F00), fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text("Objetivo ${cal.target} h", fontSize = 11.sp)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    key(year) {
                        if (an != null) {
                            BackHandler { anual = null }
                            AnualScreen(cal, vac, an) { anual = null }
                        } else when (tab) {
                            0 -> CalendarioScreen(cal, vac, dia, { d -> dia = d; tab = 1 }, { anual = it }) { y, m ->
                                if (y in cals.years) { dia = LocalDate.of(y, m, 1); year = y }
                            }
                            1 -> DiaScreen(cal, vac, dia) { dia = it }
                            2 -> VacacionesScreen(cal, vac)
                            3 -> BuscarScreen(cal, vac)
                            else -> ResumenScreen(cal, vac)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Calendario (rejilla como el Excel) ----------
@Composable
fun CalendarioScreen(
    cal: Cal, vac: Vacaciones, inicial: LocalDate, onVerDia: (LocalDate) -> Unit, onAnual: (Int) -> Unit,
    onIrA: (Int, Int) -> Unit
) {
    var month by remember { mutableStateOf(inicial.monthValue) }
    var sel by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var rDesde by remember { mutableStateOf<LocalDate?>(null) }
    var rHasta by remember { mutableStateOf<LocalDate?>(null) }
    val ctx = LocalContext.current
    val n = cal.monthLength(month)
    val start = cal.monthStart(month)

    SinEscalaLetra {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val horizontal = maxWidth > maxHeight
    val nameW = 62.dp
    val cw = if (horizontal) (maxWidth - nameW - 18.dp) / n else 32.dp
    val hdrH = 42.dp
    fun rowH(p: Int) = if (horizontal) (if (p == cal.luis) 38.dp else 30.dp) else (if (p == cal.luis) 44.dp else 34.dp)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { if (month == 1) onIrA(cal.year - 1, 12) else month -= 1 }) { Text("◀ Ant.") }
            Text("${nombreMes(month)} ${cal.year}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            TextButton(onClick = { if (month == 12) onIrA(cal.year + 1, 1) else month += 1 }) { Text("Sig. ▶") }
        }
        Row {
            Column(Modifier.width(nameW)) {
                Box(Modifier.height(hdrH).fillMaxWidth().background(Color(0xFFD9D9D9)), contentAlignment = Alignment.Center) {
                    Text("Encargado", fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                cal.people.indices.forEach { p ->
                    NombreChip(cal, p, Modifier.height(rowH(p)).fillMaxWidth().pulsar2s { onAnual(p) }, compacto = true)
                }
            }
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row {
                    for (d in 0 until n) {
                        val i = start + d
                        Column(
                            Modifier.size(cw, hdrH)
                                .background(hexColor(cal.headHex(i)))
                                .border(0.5.dp, Color(0xFF888888)),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            Text("${d + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CNegro)
                            Text(DiaLetra[cal.date(i).dayOfWeek.value - 1], fontSize = 10.sp, color = CNegro, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                cal.people.indices.forEach { p ->
                    val luis = p == cal.luis
                    Row {
                        for (d in 0 until n) {
                            val i = start + d
                            val c = vac.code(p, i)
                            val bg = cal.fill(p, i, c)
                            Box(
                                Modifier.size(cw, rowH(p))
                                    .background(bg)
                                    .border(if (luis) 2.dp else 0.5.dp, if (luis) CNegro else Color(0xFF888888))
                                    .tapOLargo(p, i,
                                        onTap = { onVerDia(cal.date(i)) },
                                        onLong = { rDesde = cal.date(i); rHasta = cal.date(i); sel = p to i }),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    Turno.letra(c), color = CNegro,
                                    fontSize = if (horizontal) (if (luis) 14.sp else 11.sp) else (if (luis) 16.sp else 12.sp),
                                    fontWeight = if (luis || c == 'V') FontWeight.ExtraBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Toca un día para ver quién trabaja. Mantén 2 s una casilla para apuntar vacaciones (eliges el rango). Mantén 2 s un nombre para ver su año.", fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            listOf('M', 'T', 'N', 'D', 'P', 'A', 'V').forEach { c ->
                TurnoBox(c, false, 24.dp)
                Text(" ${Turno.nombre(c)}   ", fontSize = 11.sp)
            }
            TurnoBox('D', true, 24.dp)
            Text(" Festivo 12h", fontSize = 11.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
    }
    }

    sel?.let { (p, i0) ->
        val d0 = rDesde ?: cal.date(i0)
        val d1 = rHasta ?: d0
        val x = cal.index(d0)
        val y = cal.index(d1)
        val nuevos = (x..y).filter { !vac.has(p, it) }
        val desc = nuevos.sumOf { Turno.horas(cal.plan(p, it)) } / 8.0
        val quedan = vac.sobrantesRestantes(p) - desc
        AlertDialog(
            onDismissRequest = { sel = null },
            title = { Text("Vacaciones de ${cal.people[p].name}") },
            text = {
                Column {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        pickDate(ctx, cal, d0) { d -> rDesde = d; if (d1 < d) rHasta = d }
                    }) { Text("Desde: ${fechaLarga(d0)}", fontSize = 12.sp) }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        pickDate(ctx, cal, d1) { d -> rHasta = d; if (d0 > d) rDesde = d }
                    }) { Text("Hasta: ${fechaLarga(d1)}", fontSize = 12.sp) }
                    Spacer(Modifier.height(8.dp))
                    Text("${y - x + 1} día(s) · se descontarían ${fmtDias(desc)} días sobrantes")
                    Text(
                        "Quedarían ${fmtDias(quedan)}", fontWeight = FontWeight.Bold,
                        color = if (quedan < 0) Color(0xFFCC0000) else CNegro
                    )
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        vac.remove(p, x, y); sel = null
                    }) { Text("Quitar vacaciones de este rango") }
                }
            },
            confirmButton = { TextButton(onClick = { vac.add(p, x, y); sel = null }) { Text("Apuntar") } },
            dismissButton = { TextButton(onClick = { sel = null }) { Text("Cancelar") } }
        )
    }
}

// ---------- Consulta de un día ----------
@Composable
fun DiaScreen(cal: Cal, vac: Vacaciones, fecha: LocalDate, onFecha: (LocalDate) -> Unit) {
    val ctx = LocalContext.current
    val i = cal.index(fecha)
    val fest = cal.isHoliday(i)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { if (i > 0) onFecha(cal.date(i - 1)) }) { Text("◀") }
            Button(onClick = { pickDate(ctx, cal, fecha) { onFecha(it) } }) { Text("Elegir fecha") }
            OutlinedButton(onClick = { if (i < cal.days - 1) onFecha(cal.date(i + 1)) }) { Text("▶") }
        }
        Spacer(Modifier.height(10.dp))
        Text(fechaLarga(fecha), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (fest) Text("FESTIVO", color = Color(0xFFCC0099), fontWeight = FontWeight.Bold)
        else if (cal.isWeekend(i)) Text("Fin de semana", color = Color(0xFFCC0000), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val trabajan = cal.people.indices.count { Turno.horas(vac.code(it, i).let { c -> if (c == 'V') '.' else c }) > 0 }
        Text("Trabajan $trabajan de ${cal.people.size} encargados", fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        listOf('M', 'T', 'N', 'D', 'P', 'A', 'V', '.').forEach { c ->
            val lista = cal.people.indices.filter { vac.code(it, i) == c }
            if (lista.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                    TurnoBox(c, fest, 30.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(Turno.nombre(c, fest), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                lista.forEach { p ->
                    Row(Modifier.padding(start = 38.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        NombreChip(cal, p, Modifier.width(150.dp))
                        if (c == 'V') {
                            val o = cal.plan(p, i)
                            Text("  (era ${Turno.nombre(o, fest)})", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Vacaciones ----------
@Composable
fun VacacionesScreen(cal: Cal, vac: Vacaciones) {
    val ctx = LocalContext.current
    var p by remember { mutableStateOf(cal.luis) }
    var desde by remember { mutableStateOf<LocalDate?>(null) }
    var hasta by remember { mutableStateOf<LocalDate?>(null) }
    val a = desde
    val b = hasta
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Titulo("Encargado")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cal.people.indices.forEach { q ->
                FilterChip(selected = p == q, onClick = { p = q }, label = { Text(cal.people[q].name) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = ColorPersona[p].copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Días sobrantes de ${cal.people[p].name}", fontWeight = FontWeight.Bold)
                Text("Iniciales: ${fmtDias(vac.sobrantesIniciales(p))}")
                Text("Descontados por vacaciones: ${fmtDias(vac.descontados(p))}")
                val r = vac.sobrantesRestantes(p)
                Text("Restantes: ${fmtDias(r)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (r < 0) Color(0xFFCC0000) else CNegro)
            }
        }
        Titulo("Apuntar vacaciones")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                pickDate(ctx, cal, a ?: hoyOInicio(cal)) { d ->
                    desde = d
                    if (b == null || b < d) hasta = d
                }
            }) { Text("Desde: " + (a?.let { fechaCorta(it) } ?: "—")) }
            OutlinedButton(onClick = {
                pickDate(ctx, cal, b ?: a ?: hoyOInicio(cal)) { d ->
                    hasta = d
                    if (a == null || a > d) desde = d
                }
            }) { Text("Hasta: " + (b?.let { fechaCorta(it) } ?: "—")) }
        }
        if (a != null && b != null) {
            val i0 = cal.index(a)
            val i1 = cal.index(b)
            val rango = (i0..i1)
            val nuevos = rango.filter { !vac.has(p, it) }
            val trabajo = nuevos.filter { Turno.horas(cal.plan(p, it)) > 0 }
            val desc = trabajo.sumOf { Turno.horas(cal.plan(p, it)) } / 8.0
            val quedan = vac.sobrantesRestantes(p) - desc
            Spacer(Modifier.height(8.dp))
            Text("${rango.count()} días naturales · ${trabajo.size} con turno de trabajo")
            Text("Se descontarían ${fmtDias(desc)} días sobrantes (12h = 1,5 días; 8h = 1 día)")
            Text(
                "Quedarían ${fmtDias(quedan)} días sobrantes",
                fontWeight = FontWeight.Bold, color = if (quedan < 0) Color(0xFFCC0000) else CNegro
            )
            if (quedan < 0) Text("Ojo: te pasas de los días sobrantes disponibles.", color = Color(0xFFCC0000), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vac.add(p, i0, i1) }) { Text("Apuntar") }
                OutlinedButton(onClick = { vac.remove(p, i0, i1) }) { Text("Quitar del rango") }
            }
        }
        Titulo("Vacaciones apuntadas de ${cal.people[p].name}")
        val tramos = vac.tramos(p)
        if (tramos.isEmpty()) Text("Ninguna todavía.")
        tramos.forEach { (x, y) ->
            val dd = (x..y).sumOf { Turno.horas(cal.plan(p, it)) } / 8.0
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TurnoBox('V', false, 26.dp)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(if (x == y) fechaCorta(cal.date(x)) else "${fechaCorta(cal.date(x))} → ${fechaCorta(cal.date(y))}", fontWeight = FontWeight.Medium)
                    Text("${y - x + 1} día(s) · descuenta ${fmtDias(dd)}", fontSize = 12.sp)
                }
                TextButton(onClick = { vac.remove(p, x, y) }) { Text("Borrar") }
            }
        }
    }
}

// ---------- Búsqueda ----------
class Hit(val p: Int, val d: Int, val c: Char)

@Composable
fun BuscarScreen(cal: Cal, vac: Vacaciones) {
    val ctx = LocalContext.current
    var texto by remember { mutableStateOf("") }
    var personas by remember { mutableStateOf(setOf<Int>()) }
    var turnos by remember { mutableStateOf(setOf<Char>()) }
    var semana by remember { mutableStateOf(setOf<Int>()) }
    var tipo by remember { mutableStateOf(setOf<String>()) }
    var desde by remember { mutableStateOf(LocalDate.of(cal.year, 1, 1)) }
    var hasta by remember { mutableStateOf(LocalDate.of(cal.year, 12, 31)) }
    val vacMap = vac.map

    val hits = remember(texto, personas, turnos, semana, tipo, desde, hasta, vacMap) {
        val toks = quitaTildes(texto).split(" ").filter { it.isNotBlank() }
        val r = ArrayList<Hit>()
        for (i in cal.index(desde)..cal.index(hasta)) {
            val date = cal.date(i)
            val fest = cal.isHoliday(i)
            val fin = cal.isWeekend(i)
            val dow = date.dayOfWeek.value
            if (semana.isNotEmpty() && dow !in semana) continue
            if (tipo.isNotEmpty()) {
                val t = if (fest) "festivo" else if (fin) "finde" else "laborable"
                if (t !in tipo && !(fest && "finde" in tipo && fin)) continue
            }
            for (p in cal.people.indices) {
                if (personas.isNotEmpty() && p !in personas) continue
                val c = vac.code(p, i)
                if (turnos.isNotEmpty() && c !in turnos) continue
                if (toks.isNotEmpty()) {
                    val hay = quitaTildes(
                        "${cal.people[p].name} ${Turno.nombre(c, fest)} ${Turno.letra(c)} " +
                            "${date.dayOfWeek.getDisplayName(TextStyle.FULL, ES)} ${Month.of(date.monthValue).getDisplayName(TextStyle.FULL, ES)} " +
                            "${date.dayOfMonth}/${date.monthValue}/${date.year} $date " +
                            (if (fest) "festivo " else "") + (if (fin) "fin de semana finde " else "laborable ") +
                            (if (Turno.horas(c) > 0) "trabaja ${Turno.horas(c)}h" else if (c == 'V') "" else "descansa")
                    )
                    if (!toks.all { hay.contains(it) }) continue
                }
                r.add(Hit(p, i, c))
            }
        }
        r
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Titulo("Buscar")
            OutlinedTextField(
                value = texto, onValueChange = { texto = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Texto libre (luis, agosto, sábado, noche, festivo…)") }
            )
            Text("Encargados", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cal.people.indices.forEach { q ->
                    FilterChip(
                        selected = q in personas,
                        onClick = { personas = if (q in personas) personas - q else personas + q },
                        label = { Text(cal.people[q].name) }
                    )
                }
            }
            Text("Turnos", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf('M', 'T', 'N', 'D', 'P', 'A', 'V', '.').forEach { c ->
                    FilterChip(
                        selected = c in turnos,
                        onClick = { turnos = if (c in turnos) turnos - c else turnos + c },
                        label = { Text(if (c == '.') "Libre" else Turno.nombre(c)) }
                    )
                }
            }
            Text("Día de la semana", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DiaLetra.forEachIndexed { k, l ->
                    val dow = k + 1
                    FilterChip(
                        selected = dow in semana,
                        onClick = { semana = if (dow in semana) semana - dow else semana + dow },
                        label = { Text(l) }
                    )
                }
            }
            Text("Tipo de día", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("laborable" to "Laborable", "finde" to "Fin de semana", "festivo" to "Festivo").forEach { (k, l) ->
                    FilterChip(
                        selected = k in tipo,
                        onClick = { tipo = if (k in tipo) tipo - k else tipo + k },
                        label = { Text(l) }
                    )
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    pickDate(ctx, cal, desde) { d -> desde = d; if (hasta < d) hasta = d }
                }) { Text("Desde: ${fechaCorta(desde)}") }
                OutlinedButton(onClick = {
                    pickDate(ctx, cal, hasta) { d -> hasta = d; if (desde > d) desde = d }
                }) { Text("Hasta: ${fechaCorta(hasta)}") }
            }
            TextButton(onClick = {
                texto = ""; personas = emptySet(); turnos = emptySet(); semana = emptySet(); tipo = emptySet()
                desde = LocalDate.of(cal.year, 1, 1); hasta = LocalDate.of(cal.year, 12, 31)
            }) { Text("Limpiar filtros") }

            val horas = hits.sumOf { Turno.horas(it.c) }
            Text(
                "${hits.size} resultados · ${hits.map { it.d }.distinct().size} días · $horas h (${fmtDias(horas / 8.0)} jornadas de 8h)",
                fontWeight = FontWeight.Bold
            )
            val porPersona = cal.people.indices.map { q -> q to hits.count { it.p == q } }.filter { it.second > 0 }
            if (porPersona.isNotEmpty()) Text(porPersona.joinToString("  ·  ") { "${cal.people[it.first].name}: ${it.second}" }, fontSize = 12.sp)
            if (hits.size > 400) Text("Se muestran los 400 primeros; afina los filtros.", fontSize = 12.sp, color = Color(0xFFCC0000))
            Spacer(Modifier.height(6.dp))
        }
        items(hits.take(400)) { h ->
            val fest = cal.isHoliday(h.d)
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(fechaCorta(cal.date(h.d)), modifier = Modifier.width(92.dp), fontSize = 13.sp)
                NombreChip(cal, h.p, Modifier.width(112.dp))
                Spacer(Modifier.width(8.dp))
                TurnoBox(h.c, fest, 26.dp, fondo = cal.fill(h.p, h.d, h.c))
                Text(
                    " " + Turno.nombre(h.c, false) + if (Turno.horas(h.c) > 0 && h.c in "DP" && fest) " fest." else "",
                    fontSize = 12.sp
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ---------- Resumen / acumulados ----------
@Composable
fun ResumenScreen(cal: Cal, vac: Vacaciones) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Titulo("Horas y días sobrantes (objetivo ${cal.target} h)")
        cal.people.indices.forEach { p -> PersonaCard(cal, vac, p) }
    }
}

@Composable
fun PersonaCard(cal: Cal, vac: Vacaciones, p: Int) {
    var abierto by remember { mutableStateOf(false) }
    val luis = p == cal.luis
    val rest = vac.sobrantesRestantes(p)
    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        border = if (luis) BorderStroke(3.dp, CNegro) else BorderStroke(0.5.dp, Color(0xFF888888)),
        colors = CardDefaults.cardColors(containerColor = ColorPersona[p].copy(alpha = if (luis) 0.55f else 0.3f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                (if (luis) "★ " else "") + cal.people[p].name,
                fontWeight = if (luis) FontWeight.ExtraBold else FontWeight.Bold, fontSize = if (luis) 20.sp else 17.sp
            )
            Text("Horas del calendario: ${vac.horasPlan(p)} h")
            Text("Horas de vacaciones descontadas: ${vac.horasPlan(p) - vac.horasEfectivas(p)} h")
            Text("Horas a trabajar: ${vac.horasEfectivas(p)} h  (objetivo ${cal.target})")
            Text("Días sobrantes iniciales: ${fmtDias(vac.sobrantesIniciales(p))}")
            Text("Descontados por vacaciones: ${fmtDias(vac.descontados(p))}")
            Text(
                "Días sobrantes restantes: ${fmtDias(rest)}", fontWeight = FontWeight.Bold, fontSize = 17.sp,
                color = if (rest < 0) Color(0xFFCC0000) else CNegro
            )
            Text("Días de vacaciones apuntados: ${vac.days(p).size}")
            TextButton(onClick = { abierto = !abierto }) { Text(if (abierto) "Ocultar meses" else "Ver acumulados por mes") }
            if (abierto) TablaMeses(cal, vac, p)
        }
    }
}

@Composable
fun TablaMeses(cal: Cal, vac: Vacaciones, p: Int) {
    val cab = listOf("Mes", "M", "T", "N", "D", "P", "Pa", "V", "Horas", "Acum", "Dif")
    val ancho = listOf(46.dp, 28.dp, 28.dp, 28.dp, 28.dp, 28.dp, 32.dp, 28.dp, 48.dp, 52.dp, 56.dp)
    @Composable
    fun fila(v: List<String>, bold: Boolean = false) {
        Row {
            v.forEachIndexed { k, s ->
                Text(
                    s, modifier = Modifier.width(ancho[k]), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    textAlign = if (k == 0) TextAlign.Start else TextAlign.End
                )
            }
        }
    }
    var acum = 0
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        fila(cab, true)
        for (m in 1..12) {
            val s = cal.monthStart(m)
            val idx = s until s + cal.monthLength(m)
            val cod = idx.map { vac.code(p, it) }
            val h = cod.sumOf { Turno.horas(it) }
            acum += h
            fila(
                listOf(
                    nombreMes(m).take(3),
                    cod.count { it == 'M' }.toString(), cod.count { it == 'T' }.toString(), cod.count { it == 'N' }.toString(),
                    cod.count { it == 'D' }.toString(), cod.count { it == 'P' }.toString(), cod.count { it == 'A' }.toString(),
                    cod.count { it == 'V' }.toString(), h.toString(), acum.toString(), (acum - cal.target).toString()
                )
            )
        }
    }
    Text("V = días de vacaciones apuntados. Dif = horas acumuladas − ${cal.target}.", fontSize = 11.sp)
}
