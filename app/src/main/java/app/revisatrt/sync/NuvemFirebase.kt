package app.revisatrt.sync

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import app.revisatrt.data.AppDatabase
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** Coleções em usuarios/{uid}/... no Firestore. */
class NuvemFirestore(private val uid: String) : Nuvem {
    private val fs = FirebaseFirestore.getInstance()
    private fun col(c: String) = fs.collection("usuarios").document(uid).collection(c)

    override suspend fun buscar(colecao: String) =
        col(colecao).get().await().documents.map { it.id to (it.data ?: emptyMap()) }

    // O Firestore guarda a escrita localmente e envia quando houver internet; não é preciso esperar.
    override fun enviar(colecao: String, id: String, dados: Map<String, Any?>) {
        col(colecao).document(id).set(dados, SetOptions.merge())
    }

    override fun ouvir(colecao: String, aoMudar: (List<Pair<String, Map<String, Any?>>>) -> Unit): () -> Unit {
        val reg = col(colecao).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            val mudados = snap.documentChanges.map { it.document.id to it.document.data }
            if (mudados.isNotEmpty()) aoMudar(mudados)
        }
        return { reg.remove() }
    }
}

data class EstadoSync(
    val configurado: Boolean = false,
    val email: String? = null,
    val sincronizando: Boolean = false,
    val ultima: Long? = null,
    val erro: String? = null,
)

/** Login com Google + sincronização contínua enquanto o app está aberto. */
@OptIn(FlowPreview::class)
class ServicoSync(private val context: Context, private val db: AppDatabase) {
    private val escopo = CoroutineScope(Dispatchers.IO)
    private val trava = Mutex()
    private val _estado = MutableStateFlow(EstadoSync())
    val estado: StateFlow<EstadoSync> = _estado
    private var ouvintes: List<() -> Unit> = emptyList()
    private var envioAutomatico: Job? = null
    private var sincronizador: Sincronizador? = null

    /** O Firebase só existe se o google-services.json foi incluído no build. */
    val configurado: Boolean = runCatching { FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null }
        .getOrDefault(false)

    private val auth get() = FirebaseAuth.getInstance()

    fun iniciar() {
        _estado.value = EstadoSync(configurado = configurado)
        if (!configurado) return
        auth.currentUser?.let { conectar(it.uid, it.email) }
    }

    private fun idClienteWeb(): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id)
    }

    suspend fun entrar(activity: Activity) {
        if (!configurado) return
        val cliente = idClienteWeb() ?: run {
            _estado.value = _estado.value.copy(erro = "Login com Google não está ativado no Firebase (baixe de novo o google-services.json).")
            return
        }
        try {
            val pedido = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(cliente).build()
                ).build()
            val resposta = CredentialManager.create(activity).getCredential(activity, pedido)
            val token = GoogleIdTokenCredential.createFrom(resposta.credential.data).idToken
            val usuario = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user
                ?: error("Não foi possível entrar.")
            conectar(usuario.uid, usuario.email)
        } catch (_: GetCredentialCancellationException) {
            // usuária cancelou
        } catch (e: Exception) {
            _estado.value = _estado.value.copy(erro = "Não foi possível entrar: ${e.message}")
        }
    }

    fun sair() {
        pararOuvintes()
        auth.signOut()
        sincronizador = null
        _estado.value = EstadoSync(configurado = configurado)
    }

    private fun conectar(uid: String, email: String?) {
        val s = Sincronizador(db, NuvemFirestore(uid))
        sincronizador = s
        _estado.value = EstadoSync(configurado = true, email = email)
        escopo.launch {
            sincronizarAgora()
            pararOuvintes()
            val nuvem = NuvemFirestore(uid)
            ouvintes = listOf(
                nuvem.ouvir(Sincronizador.PONTOS) { docs -> escopo.launch { trava.withLock { s.aplicarPontos(docs) }; marcar() } },
                nuvem.ouvir(Sincronizador.REVISOES) { docs -> escopo.launch { trava.withLock { s.aplicarRevisoes(docs) }; marcar() } },
                nuvem.ouvir(Sincronizador.ANOTACOES) { docs -> escopo.launch { trava.withLock { s.aplicarAnotacoes(docs) }; marcar() } },
            )
            // envia sozinho o que for alterado neste aparelho
            envioAutomatico = escopo.launch {
                db.dao().totalPendentes().filter { it > 0 }.debounce(800).collect {
                    trava.withLock { runCatching { s.enviarPendentes() } }
                    marcar()
                }
            }
        }
    }

    private fun marcar() {
        _estado.value = _estado.value.copy(ultima = System.currentTimeMillis(), sincronizando = false, erro = null)
    }

    suspend fun sincronizarAgora() {
        val s = sincronizador ?: return
        _estado.value = _estado.value.copy(sincronizando = true, erro = null)
        try {
            trava.withLock { s.sincronizarTudo() }
            marcar()
        } catch (e: Exception) {
            _estado.value = _estado.value.copy(sincronizando = false, erro = "Sem conexão com a nuvem agora. As alterações ficam guardadas e sobem depois.")
        }
    }

    private fun pararOuvintes() {
        ouvintes.forEach { it() }
        ouvintes = emptyList()
        envioAutomatico?.cancel()
        envioAutomatico = null
    }
}
