package com.luis.calendario

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

val ES: Locale = Locale("es", "ES")

/** Códigos de turno. '.' = libre, 'V' = vacaciones (solo en la capa de la app). */
object Turno {
    fun horas(c: Char): Int = when (c) {
        'M', 'T', 'N', 'A' -> 8
        'D', 'P' -> 12
        else -> 0
    }

    fun nombre(c: Char, festivo: Boolean = false): String = when (c) {
        'M' -> "Mañana 8h"
        'T' -> "Tarde 8h"
        'N' -> "Noche 8h"
        'D' -> if (festivo) "Día 12h (festivo)" else "Día 12h"
        'P' -> if (festivo) "Noche 12h (festivo)" else "Noche 12h"
        'A' -> "Partido"
        'V' -> "Vacaciones"
        else -> "Libre"
    }

    /** Letra tal y como aparece en el Excel. */
    fun letra(c: Char): String = when (c) {
        'A' -> "Pa"
        '.' -> ""
        else -> c.toString()
    }

    val trabajo = listOf('M', 'T', 'N', 'D', 'P', 'A')
}

/** `tone`: color de la casilla en el Excel para turnos de 12h: R rojo, K rosa (festivo), W sin color, G gris. */
class Person(val name: String, val plan: String, val tone: String)

/** Un año de calendario. `forecast` = generado con el ciclo de 5 semanas (sin festivos). */
class Cal(
    val year: Int,
    val target: Int,
    val people: List<Person>,
    private val holidays: String,
    val forecast: Boolean
) {
    val days: Int get() = holidays.length
    val luis: Int = people.indexOfFirst { it.name == "LUIS" }.coerceAtLeast(0)

    fun date(i: Int): LocalDate = LocalDate.ofYearDay(year, i + 1)
    fun index(d: LocalDate): Int = d.dayOfYear - 1
    fun inYear(d: LocalDate) = d.year == year
    fun isHoliday(i: Int) = holidays[i] == '1'
    fun isWeekend(i: Int) = date(i).dayOfWeek.value >= 6

    /** Turno del calendario, sin vacaciones. */
    fun plan(p: Int, i: Int): Char = people[p].plan[i]

    fun monthStart(m: Int): Int = index(LocalDate.of(year, m, 1))
    fun monthLength(m: Int): Int = LocalDate.of(year, m, 1).lengthOfMonth()
}

/** Todos los años: 2026 y 2027 vienen del Excel; los siguientes se generan con el ciclo de 5 semanas. */
class Calendarios(json: String) {
    companion object {
        const val ULTIMO = 2040
    }

    private val o = JSONObject(json)
    private val cycleStart = LocalDate.parse(o.getString("cycleStart"))
    private val defaultTarget = o.getInt("defaultTarget")
    private val cycle = o.getJSONObject("cycle")
    private val cache = HashMap<Int, Cal>()
    private val excel = HashMap<Int, JSONObject>()

    init {
        val arr = o.getJSONArray("years")
        for (k in 0 until arr.length()) excel[arr.getJSONObject(k).getInt("year")] = arr.getJSONObject(k)
    }

    val primero: Int = excel.keys.min()
    val years: List<Int> = (primero..ULTIMO).toList()

    fun get(year: Int): Cal = cache.getOrPut(year) {
        val e = excel[year]
        if (e != null) {
            val arr = e.getJSONArray("people")
            Cal(
                year, e.getInt("target"),
                (0 until arr.length()).map { Person(arr.getJSONObject(it).getString("name"), arr.getJSONObject(it).getString("plan"), arr.getJSONObject(it).getString("tone")) },
                e.getString("holidays"), false
            )
        } else {
            val n = LocalDate.of(year, 1, 1).lengthOfYear()
            val ini = LocalDate.of(year, 1, 1).toEpochDay() - cycleStart.toEpochDay()
            val orden = excel.getValue(primero).getJSONArray("people").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("name") } }
            Cal(
                year, defaultTarget,
                orden.map { nm ->
                    val c = cycle.getString(nm)
                    val plan = String(CharArray(n) { i -> c[Math.floorMod(ini + i, 35L).toInt()] })
                    Person(nm, plan, String(CharArray(n) { i -> if (plan[i] == 'D' || plan[i] == 'P') 'R' else '.' }))
                },
                "0".repeat(n), true
            )
        }
    }
}

/** Vacaciones apuntadas (se guardan en el móvil), por fecha, para todos los años. */
class VacStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("vacaciones", Context.MODE_PRIVATE)

    /** nombre -> días (epochDay) */
    var map: Map<String, Set<Long>> by mutableStateOf(load())
        private set

    private fun load(): Map<String, Set<Long>> {
        return try {
            val r = HashMap<String, Set<Long>>()
            val nuevo = prefs.getString("v2", null)
            if (nuevo != null) {
                val o = JSONObject(nuevo)
                for (k in o.keys()) {
                    val a = o.getJSONArray(k)
                    r[k] = (0 until a.length()).map { a.getLong(it) }.toSet()
                }
            } else {
                // Formato antiguo (V1-V3): índices de día de 2027
                val o = JSONObject(prefs.getString("v", "{}") ?: "{}")
                val base = LocalDate.of(2027, 1, 1).toEpochDay()
                for (k in o.keys()) {
                    val a = o.getJSONArray(k)
                    r[k] = (0 until a.length()).map { base + a.getInt(it) }.toSet()
                }
            }
            r
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun save(m: Map<String, Set<Long>>) {
        val o = JSONObject()
        for ((k, v) in m) o.put(k, JSONArray(v.sorted()))
        prefs.edit().putString("v2", o.toString()).apply()
        map = m
    }

    fun add(name: String, from: Long, to: Long) {
        val m = map.toMutableMap()
        m[name] = (m[name] ?: emptySet()) + (from..to)
        save(m)
    }

    fun remove(name: String, from: Long, to: Long) {
        val m = map.toMutableMap()
        m[name] = (m[name] ?: emptySet()) - (from..to).toSet()
        save(m)
    }
}

/** Vistas de las vacaciones para un año concreto. */
class Vacaciones(private val st: VacStore, private val cal: Cal) {
    private val first = LocalDate.of(cal.year, 1, 1).toEpochDay()

    /** Para claves de recomposición/caché en las pantallas. */
    val map: Map<String, Set<Long>> get() = st.map

    private fun nm(p: Int) = cal.people[p].name

    fun has(p: Int, i: Int) = st.map[nm(p)]?.contains(first + i) == true

    /** Índices de día (0..) del año con vacaciones. */
    fun days(p: Int): Set<Int> =
        (st.map[nm(p)] ?: emptySet()).map { (it - first).toInt() }.filter { it >= 0 && it < cal.days }.toSet()

    fun add(p: Int, from: Int, to: Int) = st.add(nm(p), first + from, first + to)
    fun remove(p: Int, from: Int, to: Int) = st.remove(nm(p), first + from, first + to)

    /** Turno efectivo: 'V' si hay vacaciones. */
    fun code(p: Int, i: Int): Char = if (has(p, i)) 'V' else cal.plan(p, i)

    /** Días de 8h descontados por las vacaciones (12h = 1,5). Solo cuentan los días con turno. */
    fun descontados(p: Int): Double = days(p).sumOf { Turno.horas(cal.plan(p, it)) } / 8.0

    fun horasPlan(p: Int): Int = cal.people[p].plan.sumOf { Turno.horas(it) }
    fun horasEfectivas(p: Int): Int = horasPlan(p) - days(p).sumOf { Turno.horas(cal.plan(p, it)) }
    fun sobrantesIniciales(p: Int): Double = (horasPlan(p) - cal.target) / 8.0
    fun sobrantesRestantes(p: Int): Double = (horasEfectivas(p) - cal.target) / 8.0

    /** Tramos consecutivos de vacaciones: pares (desde, hasta). */
    fun tramos(p: Int): List<Pair<Int, Int>> {
        val s = days(p).sorted()
        val r = ArrayList<Pair<Int, Int>>()
        var a = -1
        var b = -1
        for (d in s) {
            if (a == -1) { a = d; b = d }
            else if (d == b + 1) b = d
            else { r.add(a to b); a = d; b = d }
        }
        if (a != -1) r.add(a to b)
        return r
    }
}

fun fmtDias(d: Double): String {
    val t = if (d == Math.floor(d)) d.toLong().toString() else String.format(ES, "%.1f", d)
    return t
}
