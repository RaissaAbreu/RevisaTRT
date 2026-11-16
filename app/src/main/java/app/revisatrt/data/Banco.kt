package app.revisatrt.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface EstudoDao {
    // ---- Disciplinas
    @Query("SELECT * FROM disciplina ORDER BY ordem")
    fun disciplinas(): Flow<List<Disciplina>>

    @Query("SELECT * FROM disciplina ORDER BY ordem")
    suspend fun disciplinasAgora(): List<Disciplina>

    @Insert
    suspend fun inserirDisciplina(d: Disciplina): Long

    @Update
    suspend fun atualizarDisciplina(d: Disciplina)

    // ---- Pontos
    @Query(
        """
        SELECT p.*,
          (SELECT MAX(r.data) FROM revisao r WHERE r.pontoId = p.id) AS ultimaRevisao,
          (SELECT r.avaliacao FROM revisao r WHERE r.pontoId = p.id ORDER BY r.data DESC LIMIT 1) AS ultimaAvaliacao,
          (SELECT COUNT(*) FROM revisao r WHERE r.pontoId = p.id) AS totalRevisoes,
          (SELECT COUNT(*) FROM anotacao a WHERE a.pontoId = p.id) AS totalAnotacoes
        FROM ponto p WHERE p.oculto = 0
        """
    )
    fun pontosResumo(): Flow<List<PontoResumo>>

    @Query("SELECT * FROM ponto WHERE id = :id")
    fun ponto(id: Long): Flow<Ponto?>

    @Query("SELECT * FROM ponto")
    suspend fun todosPontos(): List<Ponto>

    @Insert
    suspend fun inserirPonto(p: Ponto): Long

    @Update
    suspend fun atualizarPonto(p: Ponto)

    @Query("DELETE FROM ponto WHERE id = :id")
    suspend fun apagarPonto(id: Long)

    @Query("UPDATE ponto SET favorito = :fav WHERE id = :id")
    suspend fun marcarFavorito(id: Long, fav: Boolean)

    // ---- Revisões
    @Query("SELECT * FROM revisao ORDER BY data DESC")
    fun revisoes(): Flow<List<Revisao>>

    @Query("SELECT * FROM revisao WHERE pontoId = :pontoId ORDER BY data DESC")
    fun revisoesDo(pontoId: Long): Flow<List<Revisao>>

    @Query("SELECT * FROM revisao")
    suspend fun todasRevisoes(): List<Revisao>

    @Query("SELECT COUNT(DISTINCT pontoId) FROM revisao WHERE data >= :desde")
    suspend fun pontosRevisadosDesde(desde: Long): Int

    @Insert
    suspend fun inserirRevisao(r: Revisao): Long

    @Query("DELETE FROM revisao WHERE id = :id")
    suspend fun apagarRevisao(id: Long)

    // ---- Anotações
    @Query("SELECT * FROM anotacao ORDER BY criadaEm DESC")
    fun anotacoes(): Flow<List<Anotacao>>

    @Query("SELECT * FROM anotacao WHERE pontoId = :pontoId ORDER BY criadaEm DESC")
    fun anotacoesDo(pontoId: Long): Flow<List<Anotacao>>

    @Query("SELECT * FROM anotacao")
    suspend fun todasAnotacoes(): List<Anotacao>

    @Insert
    suspend fun inserirAnotacao(a: Anotacao): Long

    @Update
    suspend fun atualizarAnotacao(a: Anotacao)

    @Query("DELETE FROM anotacao WHERE id = :id")
    suspend fun apagarAnotacao(id: Long)

    // ---- Backup
    @Query("DELETE FROM revisao")
    suspend fun limparRevisoes()

    @Query("DELETE FROM anotacao")
    suspend fun limparAnotacoes()

    @Query("DELETE FROM ponto")
    suspend fun limparPontos()

    @Query("DELETE FROM disciplina")
    suspend fun limparDisciplinas()

    @Insert
    suspend fun inserirDisciplinas(l: List<Disciplina>)

    @Insert
    suspend fun inserirPontos(l: List<Ponto>)

    @Insert
    suspend fun inserirRevisoes(l: List<Revisao>)

    @Insert
    suspend fun inserirAnotacoes(l: List<Anotacao>)
}

/** v2: tamanho da amostra por disciplina e coluna de provas fora da contagem. */
val MIGRACAO_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE disciplina ADD COLUMN totalCadernos INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE disciplina ADD COLUMN totalAJ INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE disciplina ADD COLUMN totalOJ INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ponto ADD COLUMN outrasProvas TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [Disciplina::class, Ponto::class, Revisao::class, Anotacao::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): EstudoDao

    companion object {
        @Volatile private var instancia: AppDatabase? = null

        /** Só para testes: fecha e descarta a instância. */
        fun descartar() = synchronized(this) { instancia?.close(); instancia = null }

        fun get(context: Context): AppDatabase = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "revisatrt.db")
                .addMigrations(MIGRACAO_1_2)
                .build().also { instancia = it }
        }
    }
}
