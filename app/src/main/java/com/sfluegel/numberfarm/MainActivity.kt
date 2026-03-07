package com.sfluegel.numberfarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sfluegel.numberfarm.ui.theme.NumberFarmTheme

private sealed class Screen {
    object Home : Screen()
    data class Game(val size: Int) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NumberFarmTheme {
                // 0 = Home; 3/5/7 = game size
                var encodedScreen by rememberSaveable { mutableStateOf(0) }
                val screen: Screen = if (encodedScreen == 0) Screen.Home
                                     else                    Screen.Game(encodedScreen)
                when (val s = screen) {
                    is Screen.Home -> Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        HomeScreen(
                            modifier  = Modifier.padding(innerPadding),
                            onNewGame = { size -> encodedScreen = size }
                        )
                    }
                    is Screen.Game -> GameScreen(
                        n      = s.size,
                        onBack = { encodedScreen = 0 }
                    )
                }
            }
        }
    }
}
