package com.luis.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Año completo de un encargado: 12 filas (meses) x 31 columnas (días).
 * Se ajusta para que entre entero en pantalla (pensado para horizontal)
 * y se puede ampliar pellizcando con dos dedos. Doble toque = volver al tamaño inicial.
 */
@Composable
fun AnualScreen(cal: Cal, vac: Vacaciones, p: Int, onCerrar: () -> Unit) {
    val luis = p == cal.luis
    val borde = Color(0xFF888888)
    var scale by remember { mutableStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    var caja by remember { mutableStateOf(IntSize.Zero) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontal = maxWidth > maxHeight
        val labW = 36.dp
        val topH = 46.dp
        val legH = 24.dp
        val areaH = maxHeight - topH - legH - 8.dp
        val cw = (maxWidth - labW - 8.dp) / 31
        val ch = minOf((areaH - 18.dp) / 13, cw * 2.2f)
        val fs = (cw.value * 0.5f).coerceIn(6f, 13f).sp

        Column(Modifier.fillMaxSize().padding(4.dp)) {
            Row(Modifier.fillMaxWidth().height(topH), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onCerrar) { Text("◀ Volver", fontSize = 12.sp) }
                Spacer(Modifier.width(8.dp))
                NombreChip(cal, p, Modifier.width(if (horizontal) 130.dp else 100.dp))
                Spacer(Modifier.width(8.dp))
                val rest = vac.sobrantesRestantes(p)
                Text(
                    "${cal.year} · ${vac.horasEfectivas(p)} h/${cal.target} · sobrantes ${fmtDias(vac.sobrantesIniciales(p))}" +
                        " − ${fmtDias(vac.descontados(p))} = ${fmtDias(rest)}",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                    color = if (rest < 0) Color(0xFFCC0000) else CNegro
                )
            }
            // Zona de la rejilla: aquí se hace zoom y desplazamiento
            Box(
                Modifier.fillMaxWidth().weight(1f).clipToBounds()
                    .onSizeChanged { caja = it }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { scale = 1f; off = Offset.Zero })
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centro, pan, zoom, _ ->
                            val ns = (scale * zoom).coerceIn(1f, 6f)
                            var o = centro - (centro - off) * (ns / scale) + pan
                            val minX = caja.width * (1f - ns)
                            val minY = caja.height * (1f - ns)
                            o = Offset(o.x.coerceIn(minOf(minX, 0f), 0f), o.y.coerceIn(minOf(minY, 0f), 0f))
                            scale = ns
                            off = o
                        }
                    }
            ) {
                Column(
                    Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = off.x
                        translationY = off.y
                    }
                ) {
                    Row {
                        Box(Modifier.size(labW, 18.dp).background(Color(0xFFD9D9D9)).border(0.5.dp, borde))
                        for (d in 1..31) {
                            Box(
                                Modifier.size(cw, 18.dp).background(Color(0xFFD9D9D9)).border(0.5.dp, borde),
                                contentAlignment = Alignment.Center
                            ) { Text("$d", fontSize = fs, fontWeight = FontWeight.Bold, color = CNegro, maxLines = 1) }
                        }
                    }
                    for (m in 1..12) {
                        val len = cal.monthLength(m)
                        val st = cal.monthStart(m)
                        Row {
                            Box(
                                Modifier.size(labW, ch).background(ColorPersona[p]).border(0.5.dp, borde),
                                contentAlignment = Alignment.Center
                            ) { Text(nombreMes(m).take(3).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CNegro, maxLines = 1) }
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
                                            Turno.letra(c), color = CNegro, fontSize = fs, maxLines = 1,
                                            fontWeight = if (luis || c == 'V') FontWeight.ExtraBold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(legH), verticalAlignment = Alignment.CenterVertically) {
                listOf('M', 'T', 'N', 'D', 'P', 'A', 'V').forEach { c ->
                    TurnoBox(c, false, 16.dp)
                    Text(" ${Turno.nombre(c)}  ", fontSize = 10.sp, maxLines = 1)
                }
                TurnoBox('D', true, 16.dp)
                Text(" Festivo  ·  Pellizca para ampliar, doble toque para restaurar", fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}
