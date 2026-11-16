package app.revisatrt.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class SeedDisciplina(
    val chave: String,
    val nome: String,
    val grupo: String,
    val ordem: Int,
    val edital: String = "",
)

@Serializable
private data class SeedPonto(
    val chave: String,
    val disciplina: String,
    val ordem: Int,
    val titulo: String,
    val oQueCai: String = "",
    val importancia: Int = 2,
    val cadernos: Int = 0,
    val questoes: Int = 0,
    val areaJudiciaria: Int = 0,
    val oficialJustica: Int = 0,
    val vigencia: String = "",
    val atencao: String = "",
    val ondeCaiu: String = "",
    val textoLei: String = "",
)

@Serializable
private data class Seed(
    val versao: Int,
    val concurso: String = "",
    val dataProva: String = "",
    val disciplinas: List<SeedDisciplina>,
    val pontos: List<SeedPonto>,
)

@Serializable
data class Backup(
    val formato: Int = 1,
    val exportadoEm: Long,
    val versaoSeed: Int,
    val ajustes: Ajustes? = null,
    val disciplinas: List<Disciplina>,
    val pontos: List<Ponto>,
    val revisoes: List<Revisao>,
    val anotacoes: List<Anotacao>,
)

class Repositorio(private val context: Context) {
    private val db = AppDatabase.get(context)
    val dao = db.dao()
    val ajustesRepo = AjustesRepo(context)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val disciplinas = dao.disciplinas()
    val pontos = dao.pontosResumo()
    val revisoes = dao.revisoes()
    val anotacoes = dao.anotacoes()
    val ajustes = ajustesRepo.ajustes

    /**
     * Carrega a base (assets/seed.json). Na primeira execução insere tudo; nas seguintes, se a versão
     * da base aumentou, acrescenta disciplinas/pontos novos e atualiza o conteúdo dos pontos que o
     * usuário não editou. Revisões, anotações e favoritos nunca são tocados.
     */
    suspend fun sincronizarBase() {
        val texto = context.assets.open("seed.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val seed = json.decodeFromString<Seed>(texto)
        if (ajustesRepo.versaoSeed() >= seed.versao) return

        db.withTransaction {
            val existentes = dao.disciplinasAgora().associateBy { it.chave }
            val idPorChave = mutableMapOf<String, Long>()
            for (d in seed.disciplinas) {
                val atual = existentes[d.chave]
                if (atual == null) {
                    idPorChave[d.chave] = dao.inserirDisciplina(
                        Disciplina(chave = d.chave, nome = d.nome, grupo = d.grupo, ordem = d.ordem, edital = d.edital)
                    )
                } else {
                    dao.atualizarDisciplina(atual.copy(nome = d.nome, grupo = d.grupo, ordem = d.ordem, edital = d.edital))
                    idPorChave[d.chave] = atual.id
                }
            }
            val pontosAtuais = dao.todosPontos().filter { it.chave != null }.associateBy { it.chave!! }
            for (p in seed.pontos) {
                val discId = idPorChave[p.disciplina] ?: continue
                val atual = pontosAtuais[p.chave]
                if (atual == null) {
                    dao.inserirPonto(
                        Ponto(
                            chave = p.chave, disciplinaId = discId, ordem = p.ordem, titulo = p.titulo,
                            oQueCai = p.oQueCai, importancia = p.importancia, cadernos = p.cadernos,
                            questoes = p.questoes, areaJudiciaria = p.areaJudiciaria, oficialJustica = p.oficialJustica,
                            vigencia = p.vigencia, atencao = p.atencao, ondeCaiu = p.ondeCaiu, textoLei = p.textoLei,
                        )
                    )
                } else {
                    var novo = atual
                    if (!atual.editado) {
                        novo = novo.copy(
                            disciplinaId = discId, ordem = p.ordem, titulo = p.titulo, oQueCai = p.oQueCai,
                            importancia = p.importancia, cadernos = p.cadernos, questoes = p.questoes,
                            areaJudiciaria = p.areaJudiciaria, oficialJustica = p.oficialJustica,
                            vigencia = p.vigencia, atencao = p.atencao, ondeCaiu = p.ondeCaiu,
                        )
                    }
                    if (novo.textoLei.isBlank() && p.textoLei.isNotBlank()) novo = novo.copy(textoLei = p.textoLei)
                    if (novo != atual) dao.atualizarPonto(novo)
                }
            }
        }
        ajustesRepo.salvarVersaoSeed(seed.versao)
    }

    // ---- Ações
    suspend fun revisar(pontoId: Long, avaliacao: Int): Long =
        dao.inserirRevisao(Revisao(pontoId = pontoId, data = System.currentTimeMillis(), avaliacao = avaliacao))

    suspend fun apagarRevisao(id: Long) = dao.apagarRevisao(id)

    suspend fun favoritar(pontoId: Long, fav: Boolean) = dao.marcarFavorito(pontoId, fav)

    suspend fun salvarAnotacao(pontoId: Long, texto: String, existente: Anotacao?) {
        if (existente == null) {
            dao.inserirAnotacao(Anotacao(pontoId = pontoId, texto = texto, criadaEm = System.currentTimeMillis()))
        } else {
            dao.atualizarAnotacao(existente.copy(texto = texto, editadaEm = System.currentTimeMillis()))
        }
    }

    suspend fun apagarAnotacao(id: Long) = dao.apagarAnotacao(id)

    suspend fun salvarPonto(p: Ponto): Long =
        if (p.id == 0L) dao.inserirPonto(p) else {
            dao.atualizarPonto(p.copy(editado = p.chave != null || p.editado)); p.id
        }

    suspend fun apagarPonto(p: Ponto) {
        if (p.chave == null) dao.apagarPonto(p.id) else dao.atualizarPonto(p.copy(oculto = true))
    }

    suspend fun salvarAjustes(a: Ajustes) = ajustesRepo.salvar(a)

    // ---- Backup
    suspend fun exportar(uri: Uri) {
        val b = Backup(
            exportadoEm = System.currentTimeMillis(),
            versaoSeed = ajustesRepo.versaoSeed(),
            ajustes = ajustesRepo.atual(),
            disciplinas = dao.disciplinasAgora(),
            pontos = dao.todosPontos(),
            revisoes = dao.todasRevisoes(),
            anotacoes = dao.todasAnotacoes(),
        )
        context.contentResolver.openOutputStream(uri, "wt")!!.use {
            it.write(json.encodeToString(b).toByteArray(Charsets.UTF_8))
        }
    }

    /** Substitui todos os dados pelos do arquivo. Retorna um resumo. */
    suspend fun importar(uri: Uri): String {
        val texto = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val b = json.decodeFromString<Backup>(texto)
        db.withTransaction {
            dao.limparRevisoes()
            dao.limparAnotacoes()
            dao.limparPontos()
            dao.limparDisciplinas()
            dao.inserirDisciplinas(b.disciplinas)
            dao.inserirPontos(b.pontos)
            dao.inserirRevisoes(b.revisoes)
            dao.inserirAnotacoes(b.anotacoes)
        }
        b.ajustes?.let { ajustesRepo.salvar(it) }
        ajustesRepo.salvarVersaoSeed(b.versaoSeed)
        // Se o app tiver uma base mais nova que o backup, incorpora as novidades.
        sincronizarBase()
        return "${b.pontos.size} pontos, ${b.revisoes.size} revisões e ${b.anotacoes.size} anotações restaurados."
    }
}
