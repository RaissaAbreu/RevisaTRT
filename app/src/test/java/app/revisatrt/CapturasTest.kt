package app.revisatrt

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureScreenRoboImage
import app.revisatrt.data.Analise
import app.revisatrt.data.Anotacao
import app.revisatrt.data.Avaliacao
import app.revisatrt.data.Revisao
import app.revisatrt.data.Tema
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Percorre as telas principais com dados de exemplo e salva capturas em /capturas.
 * Também confere a carga da base (planilha) e a lógica de atraso.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class CapturasTest {
    @get:Rule
    val regra = createAndroidComposeRule<MainActivity>()

    private val dia = 24L * 60 * 60 * 1000

    @Test
    fun percorrerTelas() {
        val repo = (regra.activity.application as RevisaApp).repo
        runBlocking {
            repo.sincronizarBase()
            val pontos = repo.dao.todosPontos().associateBy { it.chave }
            assertEquals(116, pontos.size)
            val agora = System.currentTimeMillis()
            fun rev(chave: String, diasAtras: Long, av: Int) = Revisao(pontoId = pontos[chave]!!.id, data = agora - diasAtras * dia, avaliacao = av)
            repo.dao.inserirRevisoes(
                listOf(
                    rev("trabalho-1", 0, Avaliacao.FACIL), rev("trabalho-1", 9, Avaliacao.MEDIO),
                    rev("trabalho-2", 20, Avaliacao.DIFICIL), rev("trabalho-3", 35, Avaliacao.MEDIO),
                    rev("trabalho-4", 1, Avaliacao.FACIL), rev("trabalho-5", 2, Avaliacao.DIFICIL),
                    rev("trabalho-6", 3, Avaliacao.FACIL), rev("trabalho-7", 8, Avaliacao.MEDIO),
                    rev("processo_trabalho-1", 16, Avaliacao.MEDIO), rev("processo_trabalho-2", 0, Avaliacao.FACIL),
                    rev("processo_trabalho-3", 5, Avaliacao.DIFICIL), rev("trabalho-8", 0, Avaliacao.MEDIO),
                )
            )
            repo.dao.inserirAnotacao(Anotacao(pontoId = pontos["trabalho-1"]!!.id, texto = "Fracionamento: 14 + 5 + 5. Regra de menor de 18 / maior de 50 foi REVOGADA.", criadaEm = agora - dia))
            repo.dao.inserirAnotacao(Anotacao(pontoId = pontos["trabalho-1"]!!.id, texto = "Abono: 1/3 das férias, requerer até 15 dias antes do fim do período aquisitivo.", criadaEm = agora))
            repo.favoritar(pontos["trabalho-42"]!!.id, true)
            repo.favoritar(pontos["trabalho-1"]!!.id, true)

            val resumo = repo.pontos.first()
            assertTrue(resumo.first { it.ponto.chave == "trabalho-2" }.atencao)
            assertEquals(30, Analise.faixaAtraso(resumo.first { it.ponto.chave == "trabalho-3" }.ultimaRevisao))
        }

        fun esperar(texto: String) = regra.waitUntil(10_000) {
            regra.onAllNodes(hasText(texto, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        fun foto(nome: String) { regra.waitForIdle(); captureScreenRoboImage("../docs/capturas/$nome.png") }

        esperar("Direito Processual do Trabalho")
        foto("01-inicio")

        regra.onNodeWithText("Estatísticas").performClick()
        esperar("Revisões nos últimos 14 dias")
        foto("02-estatisticas")
        regra.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        esperar("Conhecimentos Específicos")
        regra.onNodeWithText("Direito do Trabalho").performClick()
        esperar("Nunca revisados")
        foto("03-disciplina")

        regra.onNodeWithText("CLT, arts. 129 a 145", substring = true).performClick()
        esperar("Marcar como revisado")
        foto("04-ponto")
        regra.onNodeWithText("Marcar como revisado").performClick()
        esperar("Como foi a revisão?")
        foto("05-avaliacao")
        regra.onNodeWithText("Cancelar").performClick()
        regra.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        regra.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        esperar("Conhecimentos Específicos")
        regra.onNodeWithText("Revisar").performClick()
        esperar("Pontos sugeridos")
        foto("06-revisar")

        regra.onNodeWithText("Busca").performClick()
        esperar("Ex.: 331")
        regra.onNodeWithText("Ex.: 331", substring = true).performTextInput("terceirização")
        esperar("resultado(s)")
        foto("07-busca")

        regra.onNodeWithText("Favoritos").performClick()
        esperar("Pontos que sempre esqueço")
        foto("08-favoritos")

        regra.onNodeWithText("Ajustes").performClick()
        esperar("Aparência")
        foto("09-ajustes")

        runBlocking {
            val a = repo.ajustes.first()
            repo.salvarAjustes(a.copy(tema = Tema.ESCURO, cor = app.revisatrt.data.CorTema.ROXO))
        }
        regra.onNodeWithText("Início").performClick()
        esperar("Conhecimentos Específicos")
        foto("10-inicio-escuro")
        regra.onNodeWithText("Direito do Trabalho").performClick()
        esperar("Nunca revisados")
        foto("11-disciplina-escuro")
    }
}
