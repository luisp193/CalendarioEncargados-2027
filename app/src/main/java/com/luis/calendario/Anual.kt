package com.luis.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Año completo de un encargado: 12 filas (meses) x 31 columnas (días). */
@Composable
fun AnualScreen(cal: Cal, vac: Vacaciones, p: Int, onCerrar: () -> Unit) {
    val luis = p == cal.luis
    val borde = Color(0xFF888888)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontal = maxWidth > maxHeight
        val labW = 40.dp
        val cw = if (horizontal) (maxWidth - labW - 18.dp) / 31 else 26.dp
        val ch = if (horizontal) 22.dp else 28.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onCerrar) { Text("◀ Volver") }
                Spacer(Modifier.width(10.dp))
                NombreChip(cal, p, Modifier.width(130.dp))
                Text("  ${cal.year}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            val rest = vac.sobrantesRestantes(p)
            Text(
                "${vac.horasEfectivas(p)} h (objetivo ${cal.target}) · sobrantes iniciales ${fmtDias(vac.sobrantesIniciales(p))}" +
                    " · descontados ${fmtDias(vac.descontados(p))} · restantes ${fmtDias(rest)}",
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (rest < 0) Color(0xFFCC0000) else CNegro,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    Row {
                        Box(Modifier.size(labW, 22.dp).background(Color(0xFFD9D9D9)).border(0.5.dp, borde))
                        for (d in 1..31) {
                            Box(
                                Modifier.size(cw, 22.dp).background(Color(0xFFD9D9D9)).border(0.5.dp, borde),
                                contentAlignment = Alignment.Center
                            ) { Text("$d", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CNegro) }
                        }
                    }
                    for (m in 1..12) {
                        val len = cal.monthLength(m)
                        val st = cal.monthStart(m)
                        Row {
                            Box(
                                Modifier.size(labW, ch).background(ColorPersona[p]).border(0.5.dp, borde),
                                contentAlignment = Alignment.Center
                            ) { Text(nombreMes(m).take(3).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CNegro) }
                            for (d in 1..31) {
                                if (d > len) {
                                    Box(Modifier.size(cw, ch).background(Color(0xFF666666)))
                                } else {
                                    val i = st + d - 1
                                    val c = vac.code(p, i)
                                    val bg = if (c != '.') colorTurno(c, cal.isHoliday(i))
                                    else if (cal.isWeekend(i) || cal.isHoliday(i)) Color(0xFFEDEDED)
                                    else Color.White
                                    Box(
                                        Modifier.size(cw, ch).background(bg)
                                            .border(if (luis) 1.dp else 0.5.dp, if (luis) CNegro else borde),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            Turno.letra(c), color = CNegro,
                                            fontSize = if (horizontal) 9.sp else 11.sp,
                                            fontWeight = if (luis || c == 'V') FontWeight.ExtraBold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                listOf('M', 'T', 'N', 'D', 'P', 'A', 'V').forEach { c ->
                    TurnoBox(c, false, 22.dp)
                    Text(" ${Turno.nombre(c)}   ", fontSize = 11.sp)
                }
                TurnoBox('D', true, 22.dp)
                Text(" Festivo 12h", fontSize = 11.sp)
            }
        }
    }
}
