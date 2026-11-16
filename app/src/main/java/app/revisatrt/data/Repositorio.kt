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
    val totalCadernos: Int = 0,
    val totalAJ: Int = 0,
    val totalOJ: Int = 0,
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
    val outrasProvas: String = "",
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
                        Disciplina(
                            chave = d.chave, nome = d.nome, grupo = d.grupo, ordem = d.ordem, edital = d.edital,
                            totalCadernos = d.totalCadernos, totalAJ = d.totalAJ, totalOJ = d.totalOJ,
                        )
                    )
                } else {
                    dao.atualizarDisciplina(
                        atual.copy(
                            nome = d.nome, grupo = d.grupo, ordem = d.ordem, edital = d.edital,
                            totalCadernos = d.totalCadernos, totalAJ = d.totalAJ, totalOJ = d.totalOJ,
                        )
                    )
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
                            vigencia = p.vigencia, atencao = p.atencao, ondeCaiu = p.ondeCaiu,
                            outrasProvas = p.outrasProvas, textoLei = p.textoLei,
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
                    // dado informativo da planilha (não editável no app): sempre atualiza
                    if (novo.outrasProvas != p.outrasProvas) novo = novo.copy(outrasProvas = p.outrasProvas)
                    // texto da lei: a base atualiza, a menos que a usuária tenha editado o texto no app
                    if (!novo.textoEditado && p.textoLei.isNotBlank() && novo.textoLei != p.textoLei) novo = novo.copy(textoLei = p.textoLei)
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
        val agora = System.currentTimeMillis()
        if (existente == null) {
            dao.inserirAnotacao(Anotacao(pontoId = pontoId, texto = texto, criadaEm = agora, atualizadoEm = agora))
        } else {
            dao.atualizarAnotacao(existente.copy(texto = texto, editadaEm = agora, atualizadoEm = agora, pendente = true))
        }
    }

    suspend fun apagarAnotacao(id: Long) = dao.apagarAnotacao(id)

    suspend fun salvarTextoLei(p: Ponto, texto: String) {
        if (texto != p.textoLei) dao.atualizarPonto(alterado(p.copy(textoLei = texto, textoEditado = true)))
    }

    /** Marca o ponto como alterado pela usuária (vai para a nuvem). */
    private fun alterado(p: Ponto) = p.copy(atualizadoEm = System.currentTimeMillis(), pendente = true)

    suspend fun salvarPonto(p: Ponto): Long =
        if (p.id == 0L) dao.inserirPonto(alterado(p.copy(chave = p.chave ?: "usuario-${novoUuid()}"))) else {
            dao.atualizarPonto(alterado(p.copy(editado = true))); p.id
        }

    /** Apagar = ocultar, para o apagamento chegar aos outros aparelhos. */
    suspend fun apagarPonto(p: Ponto) = dao.atualizarPonto(alterado(p.copy(oculto = true)))

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
        // o que veio do backup também sobe para a nuvem
        dao.marcarRevisoesPendentes()
        dao.marcarAnotacoesPendentes()
        ajustesRepo.salvarVersaoSeed(b.versaoSeed)
        // Se o app tiver uma base mais nova que o backup, incorpora as novidades.
        sincronizarBase()
        return "${b.pontos.size} pontos, ${b.revisoes.size} revisões e ${b.anotacoes.size} anotações restaurados."
    }
}
