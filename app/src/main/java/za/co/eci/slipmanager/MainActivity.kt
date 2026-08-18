package za.co.eci.slipmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import za.co.eci.slipmanager.data.SlipRepository
import za.co.eci.slipmanager.ui.SlipApp
import za.co.eci.slipmanager.ui.theme.ECISlipTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = SlipRepository(applicationContext)
        setContent {
            ECISlipTheme {
                SlipApp(repository)
            }
        }
    }
}
