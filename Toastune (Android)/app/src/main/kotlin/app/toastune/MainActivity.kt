package app.toastune

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.toastune.ui.MainScreen
import app.toastune.ui.theme.ToastuneTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val s by vm.settings.collectAsState()
            ToastuneTheme(mode = s.theme, glass = s.glass, coverColors = s.coverColors, dyslexic = s.dyslexic) {
                MainScreen(vm)
            }
        }
    }

    // Con la pantalla apagada o la app en segundo plano no se consulta la posición.
    override fun onStart() {
        super.onStart()
        vm.player.setUiActive(true)
        vm.syncSilently()
    }

    override fun onStop() {
        vm.player.setUiActive(false)
        vm.syncSilently()
        super.onStop()
    }
}
