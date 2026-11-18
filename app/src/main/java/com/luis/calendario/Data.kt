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

class Person(val name: String, val plan: String, val sobrantes: Double)

class Cal(json: String) {
    val year: Int
    val target: Int
    val people: List<Person>
    private val holidays: String

    init {
        val o = JSONObject(json)
        year = o.getInt("year")
        target = o.getInt("target")
        holidays = o.getString("holidays")
        val arr = o.getJSONArray("people")
        people = (0 until arr.length()).map {
            val p = arr.getJSONObject(it)
            Person(p.getString("name"), p.getString("plan"), p.getDouble("sobrantes"))
        }
    }

    val days: Int get() = holidays.length
    val luis: Int = people.indexOfFirst { it.name == "LUIS" }.coerceAtLeast(0)

    fun date(i: Int): LocalDate = LocalDate.ofYearDay(year, i + 1)
    fun index(d: LocalDate): Int = d.dayOfYear - 1
    fun inYear(d: LocalDate) = d.year == year
    fun isHoliday(i: Int) = holidays[i] == '1'
    fun isWeekend(i: Int) = date(i).dayOfWeek.value >= 6

    /** Turno del Excel, sin vacaciones. */
    fun plan(p: Int, i: Int): Char = people[p].plan[i]

    fun monthStart(m: Int): Int = index(LocalDate.of(year, m, 1))
    fun monthLength(m: Int): Int = LocalDate.of(year, m, 1).lengthOfMonth()
}

/** Vacaciones apuntadas (se guardan en el móvil). */
class Vacaciones(private val ctx: Context, private val cal: Cal) {
    private val prefs = ctx.getSharedPreferences("vacaciones", Context.MODE_PRIVATE)

    /** nombre -> días del año (índice 0..364) */
    var map: Map<String, Set<Int>> by mutableStateOf(load())
        private set

    private fun load(): Map<String, Set<Int>> {
        return try {
            val o = JSONObject(prefs.getString("v", "{}") ?: "{}")
            val r = HashMap<String, Set<Int>>()
            for (k in o.keys()) {
                val a = o.getJSONArray(k)
                r[k] = (0 until a.length()).map { a.getInt(it) }.toSet()
            }
            r
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun save(m: Map<String, Set<Int>>) {
        val o = JSONObject()
        for ((k, v) in m) o.put(k, JSONArray(v.sorted()))
        prefs.edit().putString("v", o.toString()).apply()
        map = m
    }

    fun has(p: Int, i: Int) = map[cal.people[p].name]?.contains(i) == true

    fun days(p: Int): Set<Int> = map[cal.people[p].name] ?: emptySet()

    fun add(p: Int, from: Int, to: Int) {
        val n = cal.people[p].name
        val m = map.toMutableMap()
        m[n] = (m[n] ?: emptySet()) + (from..to)
        save(m)
    }

    fun remove(p: Int, from: Int, to: Int) {
        val n = cal.people[p].name
        val m = map.toMutableMap()
        m[n] = (m[n] ?: emptySet()) - (from..to).toSet()
        save(m)
    }

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
