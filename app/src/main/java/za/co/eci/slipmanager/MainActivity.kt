package za.co.eci.slipmanager

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import za.co.eci.slipmanager.data.SlipRepository
import za.co.eci.slipmanager.ui.SlipApp
import za.co.eci.slipmanager.ui.theme.ECISlipTheme
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashLogger()
        super.onCreate(savedInstanceState)
        val repository = SlipRepository(applicationContext)
        setContent {
            ECISlipTheme {
                SlipApp(repository)
            }
        }
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { writer ->
                    error.printStackTrace(PrintWriter(writer))
                }.toString()
                File(filesDir, "last_crash.txt").writeText(
                    buildString {
                        appendLine("ECI Slip Manager crash report")
                        appendLine("Time: ${LocalDateTime.now()}")
                        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Android: ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
                        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("Thread: ${thread.name}")
                        appendLine()
                        append(trace)
                    }
                )
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }
}
