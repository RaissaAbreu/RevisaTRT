package app.revisatrt

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.revisatrt.data.Anotacao
import app.revisatrt.data.AppDatabase
import app.revisatrt.data.Avaliacao
import app.revisatrt.data.Disciplina
import app.revisatrt.data.Ponto
import app.revisatrt.data.Revisao
import app.revisatrt.sync.Nuvem
import app.revisatrt.sync.Sincronizador
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Nuvem em memória que se comporta como o Firestore (merge de campos + aviso aos ouvintes). */
class NuvemFalsa : Nuvem {
    val dados = mutableMapOf<String, MutableMap<String, MutableMap<String, Any?>>>()
    private val ouvintes = mutableMapOf<String, MutableList<(List<Pair<String, Map<String, Any?>>>) -> Unit>>()
    override suspend fun buscar(colecao: String) = dados[colecao].orEmpty().map { it.key to it.value.toMap() }
    /** Simula regras do Firestore recusando gravações. */
    var recusar = false
    override fun enviar(colecao: String, id: String, dados: Map<String, Any?>, aoTerminar: (Throwable?) -> Unit) {
        if (recusar) { aoTerminar(IllegalStateException("PERMISSION_DENIED")); return }
        val doc = this.dados.getOrPut(colecao) { mutableMapOf() }.getOrPut(id) { mutableMapOf() }
        doc.putAll(dados)
        ouvintes[colecao].orEmpty().toList().forEach { it(listOf(id to doc.toMap())) }
        aoTerminar(null)
    }
    override fun ouvir(
        colecao: String,
        aoMudar: (List<Pair<String, Map<String, Any?>>>) -> Unit,
        aoErro: (Throwable) -> Unit,
    ): () -> Unit {
        ouvintes.getOrPut(colecao) { mutableListOf() }.add(aoMudar)
        return { ouvintes[colecao]?.remove(aoMudar) }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SincronizacaoTest {
    private lateinit var tablet: AppDatabase
    private lateinit var celular: AppDatabase
    private val nuvem = NuvemFalsa()

    /** A mesma base (planilha) nos dois aparelhos, mas com ids diferentes de propósito. */
    private fun banco(comCivil: Boolean): AppDatabase {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            val dao = db.dao()
            if (comCivil) {
                val c = dao.inserirDisciplina(Disciplina(chave = "civil", nome = "Civil", grupo = "", ordem = 1))
                dao.inserirPonto(Ponto(chave = "civil-1", disciplinaId = c, titulo = "CC"))
            }
            val d = dao.inserirDisciplina(Disciplina(chave = "trabalho", nome = "Direito do Trabalho", grupo = "", ordem = 2))
            for (i in 1..3) dao.inserirPonto(Ponto(chave = "trabalho-$i", disciplinaId = d, titulo = "Ponto $i", ordem = i))
        }
        return db
    }

    @Before
    fun inicio() {
        tablet = banco(comCivil = false)
        celular = banco(comCivil = true)
    }

    @After
    fun fim() { tablet.close(); celular.close() }

    private suspend fun AppDatabase.ponto(chave: String) = dao().pontoPorChave(chave)!!

    @Test
    fun tabletECelularFicamIguais() = runBlocking {
        val sT = Sincronizador(tablet, nuvem)
        val sC = Sincronizador(celular, nuvem)
        val dT = tablet.dao()
        val dC = celular.dao()

        // 1) Progresso que já existia só no tablet
        val p1 = tablet.ponto("trabalho-1")
        dT.inserirRevisao(Revisao(pontoId = p1.id, data = 1_000, avaliacao = Avaliacao.DIFICIL, atualizadoEm = 1_000))
        dT.inserirRevisao(Revisao(pontoId = p1.id, data = 2_000, avaliacao = Avaliacao.FACIL, atualizadoEm = 2_000))
        dT.inserirAnotacao(Anotacao(pontoId = p1.id, texto = "anotação do tablet", criadaEm = 1_500, atualizadoEm = 1_500))
        dT.marcarFavorito(tablet.ponto("trabalho-2").id, true, agora = 1_600)
        val criado = Ponto(chave = "usuario-abc", disciplinaId = p1.disciplinaId, titulo = "Súmula 331 (criado no tablet)",
            textoLei = "texto colado", atualizadoEm = 1_700, pendente = true)
        val idCriado = dT.inserirPonto(criado)
        dT.inserirRevisao(Revisao(pontoId = idCriado, data = 1_800, avaliacao = Avaliacao.MEDIO, atualizadoEm = 1_800))

        // a nuvem recusa (regras não publicadas): nada pode sair da fila
        nuvem.recusar = true
        sT.sincronizarTudo()
        assertTrue(dT.totalPendentes().first() > 0)
        assertTrue(sT.ultimoErro != null)
        // regras corrigidas: o que estava na fila sobe
        nuvem.recusar = false
        sT.enviarPendentes()
        assertEquals(0, dT.totalPendentes().first())

        // 2) Celular entra com a mesma conta e recebe tudo
        sC.sincronizarTudo()
        assertEquals(3, dC.revisoes().first().size)
        assertEquals("anotação do tablet", dC.anotacoes().first().single().texto)
        assertTrue(celular.ponto("trabalho-2").favorito)
        val criadoNoCelular = celular.ponto("usuario-abc")
        assertEquals("Súmula 331 (criado no tablet)", criadoNoCelular.titulo)
        assertEquals("texto colado", criadoNoCelular.textoLei)
        val resumo = dC.pontosResumo().first().first { it.ponto.chave == "trabalho-1" }
        assertEquals(Avaliacao.FACIL, resumo.ultimaAvaliacao)
        assertEquals(2, resumo.totalRevisoes)

        // 3) Com os dois abertos (ouvindo a nuvem): anotação feita no celular aparece no tablet
        val pararT = nuvem.ouvir(Sincronizador.ANOTACOES, aoMudar = { runBlocking { sT.aplicarAnotacoes(it) } })
        val pararTR = nuvem.ouvir(Sincronizador.REVISOES, aoMudar = { runBlocking { sT.aplicarRevisoes(it) } })
        val pararC = nuvem.ouvir(Sincronizador.REVISOES, aoMudar = { runBlocking { sC.aplicarRevisoes(it) } })
        val pararCA = nuvem.ouvir(Sincronizador.ANOTACOES, aoMudar = { runBlocking { sC.aplicarAnotacoes(it) } })
        dC.inserirAnotacao(Anotacao(pontoId = celular.ponto("trabalho-3").id, texto = "feita no celular", criadaEm = 3_000, atualizadoEm = 3_000))
        sC.enviarPendentes()
        assertTrue(dT.anotacoes().first().any { it.texto == "feita no celular" && it.pontoId == tablet.ponto("trabalho-3").id })

        // 4) Revisão apagada no tablet some do celular
        val primeira = dT.revisoes().first().first { it.data == 1_000L }
        dT.apagarRevisao(primeira.id, agora = 4_000)
        sT.enviarPendentes()
        assertFalse(dC.revisoes().first().any { it.uuid == primeira.uuid })
        assertEquals(2, dC.revisoes().first().size)

        // 5) Mesma anotação editada nos dois: vale a edição mais recente
        val nT = dT.anotacoes().first().first { it.texto == "anotação do tablet" }
        val nC = dC.anotacaoPorUuid(nT.uuid)!!
        dT.atualizarAnotacao(nT.copy(texto = "versão antiga", atualizadoEm = 5_000, pendente = true))
        dC.atualizarAnotacao(nC.copy(texto = "versão mais nova", atualizadoEm = 6_000, pendente = true))
        sC.enviarPendentes()
        sT.enviarPendentes()
        assertEquals("versão mais nova", dT.anotacaoPorUuid(nT.uuid)!!.texto)
        assertEquals("versão mais nova", dC.anotacaoPorUuid(nT.uuid)!!.texto)

        // 6) Tablet estava offline com uma edição antiga e a envia depois da mais nova do celular
        val nota = dC.anotacaoPorUuid(nT.uuid)!!
        dC.atualizarAnotacao(nota.copy(texto = "celular, 8h", atualizadoEm = 8_000, pendente = true))
        val antigaTablet = dT.anotacaoPorUuid(nT.uuid)!!.copy(texto = "tablet offline, 7h", atualizadoEm = 7_000, pendente = true)
        pararT()  // tablet sem internet: não ouve a nuvem
        sC.enviarPendentes()
        dT.atualizarAnotacao(antigaTablet)
        sT.enviarPendentes()              // volta a internet e sobe a versão antiga
        sC.enviarPendentes()              // celular percebeu e reenviou a dele
        sT.sincronizarTudo()              // tablet volta a ouvir/baixar
        assertEquals("celular, 8h", dT.anotacaoPorUuid(nT.uuid)!!.texto)
        assertEquals("celular, 8h", nuvem.dados[Sincronizador.ANOTACOES]!![nT.uuid]!!["texto"])

        listOf(pararTR, pararC, pararCA).forEach { it() }
    }
}
