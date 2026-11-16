package app.revisatrt

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.revisatrt.data.AjustesRepo
import app.revisatrt.data.AppDatabase
import app.revisatrt.data.MIGRACAO_1_2
import app.revisatrt.data.MIGRACAO_2_3
import app.revisatrt.data.Repositorio
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Simula um celular com a versão 1.0.1 (banco v1, base com 116 pontos) e progresso salvo,
 * aplica a atualização e confere que revisões, anotações, favoritos e edições continuam lá.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MigracaoTest {
    private val nome = "revisatrt.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @After
    fun fim() = AppDatabase.descartar()

    @Test
    fun atualizarDa101MantemProgresso() {
        // Banco v1 como a 1.0.1 deixaria: disciplina, 2 pontos da base, 1 criado pela usuária, revisões, anotação.
        helper.createDatabase(nome, 1).apply {
            execSQL("INSERT INTO disciplina (id, chave, nome, grupo, ordem, edital) VALUES (8, 'trabalho', 'Direito do Trabalho', 'Conhecimentos Específicos', 8, '')")
            val cols = "(id, chave, disciplinaId, ordem, titulo, oQueCai, importancia, cadernos, questoes, areaJudiciaria, oficialJustica, vigencia, atencao, ondeCaiu, textoLei, favorito, editado, oculto, criadoEm)"
            execSQL("INSERT INTO ponto $cols VALUES (1, 'trabalho-1', 8, 1, 'Férias', '', 4, 11, 11, 8, 3, 'Vigente', '', '', 'texto colado por mim', 1, 0, 0, 0)")
            execSQL("INSERT INTO ponto $cols VALUES (2, 'trabalho-2', 8, 2, 'Meu título editado', '', 4, 11, 11, 6, 5, 'Vigente', '', '', '', 0, 1, 0, 0)")
            execSQL("INSERT INTO ponto $cols VALUES (3, NULL, 8, 9999, 'Ponto criado no app', '', 2, 0, 0, 0, 0, '', '', '', '', 0, 0, 0, 0)")
            execSQL("INSERT INTO revisao (pontoId, data, avaliacao) VALUES (1, 1000, 3), (1, 2000, 1), (3, 3000, 2)")
            execSQL("INSERT INTO anotacao (pontoId, texto, criadaEm) VALUES (1, 'minha anotação', 1000)")
            close()
        }
        helper.runMigrationsAndValidate(nome, 3, true, MIGRACAO_1_2, MIGRACAO_2_3).close()

        val ctx = ApplicationProvider.getApplicationContext<Application>()
        runBlocking {
            AjustesRepo(ctx).salvarVersaoSeed(1)
            val repo = Repositorio(ctx)
            repo.sincronizarBase()

            val pontos = repo.dao.todosPontos()
            val porChave = pontos.filter { it.chave != null }.associateBy { it.chave }
            assertEquals(269 + 1, pontos.size) // base nova + ponto criado no app
            assertEquals(7, repo.dao.disciplinasAgora().count { it.totalCadernos > 0 })

            val p1 = porChave["trabalho-1"]!!
            assertEquals(1L, p1.id)
            assertTrue(p1.favorito)
            assertEquals("texto colado por mim", p1.textoLei)
            assertEquals("Meu título editado", porChave["trabalho-2"]!!.titulo) // edição preservada
            assertTrue(pontos.any { it.chave == null && it.titulo == "Ponto criado no app" })

            assertEquals(3, repo.dao.todasRevisoes().size)
            assertEquals(1, repo.dao.todasAnotacoes().size)
            val resumo = repo.pontos.first().first { it.id == 1L }
            assertEquals(2, resumo.totalRevisoes)

            val trab = repo.dao.disciplinasAgora().first { it.chave == "trabalho" }
            assertEquals(25, trab.totalCadernos)
            val civil = repo.dao.disciplinasAgora().first { it.chave == "civil" }
            assertEquals(24, civil.totalCadernos)
            assertTrue(porChave["constitucional-1"]!!.outrasProvas.contains("TRF-3"))

            // textos de lei da base entram; o texto colado pela usuária é mantido
            assertTrue(p1.textoEditado)
            assertTrue(porChave["direitos_humanos-7"]!!.textoLei.contains("Pacto de San José"))
            assertTrue(porChave["direitos_humanos-3"]!!.textoLei.contains("§ 3º Os tratados"))
        }
    }
}
