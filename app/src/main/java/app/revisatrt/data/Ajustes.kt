package app.revisatrt.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class Tema(val rotulo: String) { SISTEMA("Seguir o sistema"), CLARO("Claro"), ESCURO("Escuro") }

enum class CorTema(val rotulo: String) { ROSA("Rosa bebê"), AZUL("Azul bebê"), VERDE("Verde-água"), ROXO("Roxo claro") }

/** Lembrete: horário + dias da semana (1 = segunda ... 7 = domingo). */
@Serializable
data class Lembrete(
    val id: Int,
    val hora: Int,
    val minuto: Int,
    val dias: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val ativo: Boolean = true,
)

@Serializable
data class Ajustes(
    val tema: Tema = Tema.SISTEMA,
    val cor: CorTema = CorTema.ROSA,
    val metaDiaria: Int = 15,
    val metaSemanal: Int = 80,
    val notificacoes: Boolean = false,
    val avisarAtrasados: Boolean = true,
    val lembretes: List<Lembrete> = listOf(Lembrete(1, 19, 0)),
)

private val Context.dataStore by preferencesDataStore("ajustes")

class AjustesRepo(private val context: Context) {
    private val kTema = stringPreferencesKey("tema")
    private val kCor = stringPreferencesKey("cor")
    private val kMeta = intPreferencesKey("meta_diaria")
    private val kMetaSemanal = intPreferencesKey("meta_semanal")
    private val kNotif = booleanPreferencesKey("notificacoes")
    private val kAtrasados = booleanPreferencesKey("avisar_atrasados")
    private val kLembretes = stringPreferencesKey("lembretes")
    private val kSeed = intPreferencesKey("seed_versao")

    private val json = Json { ignoreUnknownKeys = true }

    val ajustes: Flow<Ajustes> = context.dataStore.data.map { p ->
        val padrao = Ajustes()
        Ajustes(
            tema = p[kTema]?.let { runCatching { Tema.valueOf(it) }.getOrNull() } ?: padrao.tema,
            cor = p[kCor]?.let { runCatching { CorTema.valueOf(it) }.getOrNull() } ?: padrao.cor,
            metaDiaria = p[kMeta] ?: padrao.metaDiaria,
            metaSemanal = p[kMetaSemanal] ?: padrao.metaSemanal,
            notificacoes = p[kNotif] ?: padrao.notificacoes,
            avisarAtrasados = p[kAtrasados] ?: padrao.avisarAtrasados,
            lembretes = p[kLembretes]?.let { runCatching { json.decodeFromString<List<Lembrete>>(it) }.getOrNull() }
                ?: padrao.lembretes,
        )
    }

    suspend fun atual(): Ajustes = ajustes.first()

    suspend fun salvar(a: Ajustes) {
        context.dataStore.edit { p ->
            p[kTema] = a.tema.name
            p[kCor] = a.cor.name
            p[kMeta] = a.metaDiaria
            p[kMetaSemanal] = a.metaSemanal
            p[kNotif] = a.notificacoes
            p[kAtrasados] = a.avisarAtrasados
            p[kLembretes] = json.encodeToString(a.lembretes)
        }
    }

    suspend fun versaoSeed(): Int = context.dataStore.data.first()[kSeed] ?: 0

    suspend fun salvarVersaoSeed(v: Int) {
        context.dataStore.edit { it[kSeed] = v }
    }
}
