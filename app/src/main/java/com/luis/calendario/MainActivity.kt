package com.luis.calendario

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val json = assets.open("calendarios.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val cals = Calendarios(json)
        val store = VacStore(applicationContext)
        setContent { App(cals, store) }
    }
}
