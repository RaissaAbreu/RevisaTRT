package app.revisatrt.sync

import androidx.room.withTransaction
import app.revisatrt.data.Anotacao
import app.revisatrt.data.AppDatabase
import app.revisatrt.data.Ponto
import app.revisatrt.data.Revisao

/**
 * Armazenamento remoto de documentos por usuária: três coleções ("pontos", "revisoes", "anotacoes"),
 * cada documento identificado por um id e com um mapa de campos simples.
 */
interface Nuvem {
    suspend fun buscar(colecao: String): List<Pair<String, Map<String, Any?>>>
    fun enviar(colecao: String, id: String, dados: Map<String, Any?>)
    /** Avisa a cada mudança (inclusive a primeira leitura). Devolve uma função para parar de ouvir. */
    fun ouvir(colecao: String, aoMudar: (List<Pair<String, Map<String, Any?>>>) -> Unit): () -> Unit
}

/**
 * Sincroniza o banco local com a nuvem.
 *
 * Regras:
 *  - revisões e anotações têm um uuid igual em todos os aparelhos;
 *  - pontos são identificados pela chave ("trabalho-1"; criados no app: "usuario-<uuid>");
 *    da base só sobem os dados da usuária (favorito, edição, texto colado, exclusão);
 *  - apagar = marcar como apagado (o apagamento também precisa viajar);
 *  - em conflito vale a alteração mais recente (atualizadoEm); se a nuvem receber uma versão mais
 *    antiga (aparelho que estava offline), quem tem a mais nova a reenvia.
 */
class Sincronizador(private val db: AppDatabase, private val nuvem: Nuvem) {
    private val dao = db.dao()

    companion object {
        const val PONTOS = "pontos"
        const val REVISOES = "revisoes"
        const val ANOTACOES = "anotacoes"
    }

    /** Primeira sincronização: baixa tudo (pontos antes, por causa das referências) e envia o que está pendente. */
    suspend fun sincronizarTudo() {
        aplicarPontos(nuvem.buscar(PONTOS))
        aplicarRevisoes(nuvem.buscar(REVISOES))
        aplicarAnotacoes(nuvem.buscar(ANOTACOES))
        enviarPendentes()
    }

    /** Envia as alterações locais pendentes. */
    suspend fun enviarPendentes() {
        val chaves = dao.todosPontos().associate { it.id to it.chave }
        val discs = dao.disciplinasAgora().associate { it.id to it.chave }
        for (p in dao.pontosPendentes()) {
            val chave = p.chave ?: continue
            nuvem.enviar(PONTOS, chave, pontoParaMapa(p, discs[p.disciplinaId]))
            dao.pontoEnviado(p.id, p.atualizadoEm)
        }
        for (r in dao.revisoesPendentes()) {
            val chave = chaves[r.pontoId] ?: continue
            nuvem.enviar(REVISOES, r.uuid, mapOf(
                "ponto" to chave, "data" to r.data, "avaliacao" to r.avaliacao.toLong(),
                "apagada" to r.apagada, "atualizadoEm" to r.atualizadoEm,
            ))
            dao.revisaoEnviada(r.id, r.atualizadoEm)
        }
        for (a in dao.anotacoesPendentes()) {
            val chave = chaves[a.pontoId] ?: continue
            nuvem.enviar(ANOTACOES, a.uuid, mapOf(
                "ponto" to chave, "texto" to a.texto, "criadaEm" to a.criadaEm, "editadaEm" to a.editadaEm,
                "apagada" to a.apagada, "atualizadoEm" to a.atualizadoEm,
            ))
            dao.anotacaoEnviada(a.id, a.atualizadoEm)
        }
    }

    private fun pontoParaMapa(p: Ponto, disciplina: String?): Map<String, Any?> {
        val m = mutableMapOf<String, Any?>(
            "favorito" to p.favorito, "oculto" to p.oculto, "editado" to p.editado,
            "textoEditado" to p.textoEditado, "atualizadoEm" to p.atualizadoEm,
        )
        val criadoNoApp = p.chave?.startsWith("usuario-") == true
        if (p.editado || criadoNoApp) {
            m += mapOf(
                "disciplina" to disciplina, "titulo" to p.titulo, "oQueCai" to p.oQueCai,
                "importancia" to p.importancia.toLong(), "vigencia" to p.vigencia, "atencao" to p.atencao,
                "ondeCaiu" to p.ondeCaiu, "ordem" to p.ordem.toLong(), "criadoEm" to p.criadoEm,
            )
        }
        if (p.textoEditado || criadoNoApp) m["textoLei"] = p.textoLei
        return m
    }

    // ---- Recebimento

    suspend fun aplicarPontos(docs: List<Pair<String, Map<String, Any?>>>) = db.withTransaction {
        val discs = dao.disciplinasAgora().associateBy { it.chave }
        for ((chave, d) in docs) {
            val versao = d.long("atualizadoEm")
            val local = dao.pontoPorChave(chave)
            if (local != null && local.atualizadoEm > versao && local.atualizadoEm > 0) { dao.reenviarPonto(local.id); continue }
            if (local != null && local.atualizadoEm >= versao) continue
            var p = local ?: run {
                val disc = discs[d.str("disciplina")] ?: return@run null
                Ponto(chave = chave, disciplinaId = disc.id, titulo = d.str("titulo") ?: return@run null, criadoEm = d.long("criadoEm"))
            } ?: continue
            p = p.copy(
                favorito = d.bool("favorito"), oculto = d.bool("oculto"), editado = d.bool("editado"),
                textoEditado = d.bool("textoEditado"), atualizadoEm = versao, pendente = false,
            )
            if (d.containsKey("titulo")) {
                p = p.copy(
                    disciplinaId = discs[d.str("disciplina")]?.id ?: p.disciplinaId,
                    titulo = d.str("titulo") ?: p.titulo, oQueCai = d.str("oQueCai") ?: "",
                    importancia = d.long("importancia").toInt(), vigencia = d.str("vigencia") ?: "",
                    atencao = d.str("atencao") ?: "", ondeCaiu = d.str("ondeCaiu") ?: "", ordem = d.long("ordem").toInt(),
                )
            }
            if (d.containsKey("textoLei")) p = p.copy(textoLei = d.str("textoLei") ?: "")
            if (local == null) dao.inserirPonto(p) else dao.atualizarPonto(p)
        }
    }

    suspend fun aplicarRevisoes(docs: List<Pair<String, Map<String, Any?>>>) = db.withTransaction {
        for ((uuid, d) in docs) {
            val versao = d.long("atualizadoEm")
            val local = dao.revisaoPorUuid(uuid)
            // a nuvem ficou com uma versão mais antiga que a deste aparelho: reenvia a nossa
            if (local != null && local.atualizadoEm > versao) { dao.reenviarRevisao(local.id); continue }
            if (local != null && local.atualizadoEm >= versao) continue
            val ponto = dao.pontoPorChave(d.str("ponto") ?: continue) ?: continue
            val r = Revisao(
                id = local?.id ?: 0, pontoId = ponto.id, data = d.long("data"), avaliacao = d.long("avaliacao").toInt(),
                uuid = uuid, apagada = d.bool("apagada"), atualizadoEm = versao, pendente = false,
            )
            if (local == null) dao.inserirRevisao(r) else dao.atualizarRevisao(r)
        }
    }

    suspend fun aplicarAnotacoes(docs: List<Pair<String, Map<String, Any?>>>) = db.withTransaction {
        for ((uuid, d) in docs) {
            val versao = d.long("atualizadoEm")
            val local = dao.anotacaoPorUuid(uuid)
            if (local != null && local.atualizadoEm > versao) { dao.reenviarAnotacao(local.id); continue }
            if (local != null && local.atualizadoEm >= versao) continue
            val ponto = dao.pontoPorChave(d.str("ponto") ?: continue) ?: continue
            val a = Anotacao(
                id = local?.id ?: 0, pontoId = ponto.id, texto = d.str("texto") ?: "", criadaEm = d.long("criadaEm"),
                editadaEm = (d["editadaEm"] as? Number)?.toLong(), uuid = uuid, apagada = d.bool("apagada"),
                atualizadoEm = versao, pendente = false,
            )
            if (local == null) dao.inserirAnotacao(a) else dao.atualizarAnotacao(a)
        }
    }
}

private fun Map<String, Any?>.long(k: String) = (this[k] as? Number)?.toLong() ?: 0L
private fun Map<String, Any?>.bool(k: String) = this[k] as? Boolean ?: false
private fun Map<String, Any?>.str(k: String) = this[k] as? String
