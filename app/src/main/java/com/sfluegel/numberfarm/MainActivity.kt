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
    object Home       : Screen()
    object Statistics : Screen()
    data class Game(val size: Int) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SolveHistory.init(this)
        enableEdgeToEdge()
        setContent {
            NumberFarmTheme {
                // 0 = Home; -1 = Statistics; 3/5/7/9 = game size
                var encodedScreen by rememberSaveable { mutableStateOf(0) }
                val screen: Screen = when {
                    encodedScreen == 0  -> Screen.Home
                    encodedScreen == -1 -> Screen.Statistics
                    else                -> Screen.Game(encodedScreen)
                }
                when (val s = screen) {
                    is Screen.Statistics -> StatisticsScreen(onBack = { encodedScreen = 0 })
                    is Screen.Home -> Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        HomeScreen(
                            modifier      = Modifier.padding(innerPadding),
                            resumableGame = GameSave.getLastSaved(),
                            onResume      = { encodedScreen = GameSave.getLastSaved()!!.n },
                            onNewGame     = { size ->
                                GameSave.clear(size)
                                encodedScreen = size
                            },
                            onStats       = { encodedScreen = -1 }
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
