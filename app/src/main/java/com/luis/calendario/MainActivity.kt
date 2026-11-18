package com.luis.calendario

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val json = assets.open("calendario2027.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val cal = Cal(json)
        val vac = Vacaciones(applicationContext, cal)
        setContent { App(cal, vac) }
    }
}
