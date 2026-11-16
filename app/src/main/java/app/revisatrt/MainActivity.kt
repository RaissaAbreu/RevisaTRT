package app.revisatrt

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revisatrt.data.Ajustes
import app.revisatrt.data.Repositorio
import app.revisatrt.notify.Lembretes
import app.revisatrt.ui.AppNavegacao
import app.revisatrt.ui.LocalRepo
import app.revisatrt.ui.theme.RevisaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RevisaApp : Application() {
    lateinit var repo: Repositorio
    lateinit var sync: app.revisatrt.sync.ServicoSync
    val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        repo = Repositorio(this)
        sync = app.revisatrt.sync.ServicoSync(this, app.revisatrt.data.AppDatabase.get(this))
        Lembretes.criarCanal(this)
        escopo.launch {
            repo.sincronizarBase()
            // a base precisa estar carregada antes de receber dados da nuvem
            runCatching { sync.iniciar() }
            Lembretes.reagendar(this@RevisaApp)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as RevisaApp).repo
        setContent {
            val ajustes by repo.ajustes.collectAsStateWithLifecycle(initialValue = Ajustes())
            RevisaTheme(ajustes.tema, ajustes.cor) {
                CompositionLocalProvider(LocalRepo provides repo) {
                    AppNavegacao()
                }
            }
        }
    }
}

object BuildConfigInfo {
    fun versao(context: android.content.Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }.getOrDefault("")
}
