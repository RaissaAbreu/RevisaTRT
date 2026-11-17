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
          (SELECT MAX(r.data) FROM revisao r WHERE r.pontoId = p.id AND r.apagada = 0) AS ultimaRevisao,
          (SELECT r.avaliacao FROM revisao r WHERE r.pontoId = p.id AND r.apagada = 0 ORDER BY r.data DESC LIMIT 1) AS ultimaAvaliacao,
          (SELECT COUNT(*) FROM revisao r WHERE r.pontoId = p.id AND r.apagada = 0) AS totalRevisoes,
          (SELECT COUNT(*) FROM anotacao a WHERE a.pontoId = p.id AND a.apagada = 0) AS totalAnotacoes
        FROM ponto p WHERE p.oculto = 0
        """
    )
    fun pontosResumo(): Flow<List<PontoResumo>>

    @Query("SELECT * FROM ponto WHERE id = :id")
    fun ponto(id: Long): Flow<Ponto?>

    @Query("SELECT * FROM ponto")
    suspend fun todosPontos(): List<Ponto>

    @Query("SELECT * FROM ponto WHERE chave = :chave")
    suspend fun pontoPorChave(chave: String): Ponto?

    @Query("SELECT * FROM ponto WHERE pendente = 1")
    suspend fun pontosPendentes(): List<Ponto>

    @Query("UPDATE ponto SET pendente = 0 WHERE id = :id AND atualizadoEm = :versao")
    suspend fun pontoEnviado(id: Long, versao: Long)

    @Query("UPDATE ponto SET pendente = 1, atualizadoEm = atualizadoEm + 1 WHERE id = :id")
    suspend fun reenviarPonto(id: Long)

    @Query("UPDATE revisao SET pendente = 1, atualizadoEm = atualizadoEm + 1 WHERE id = :id")
    suspend fun reenviarRevisao(id: Long)

    @Query("UPDATE anotacao SET pendente = 1, atualizadoEm = atualizadoEm + 1 WHERE id = :id")
    suspend fun reenviarAnotacao(id: Long)

    /** "Enviar tudo de novo": tudo o que a usuária fez volta para a fila de envio. */
    @Query(
        "UPDATE ponto SET pendente = 1, atualizadoEm = MAX(atualizadoEm, criadoEm, 1) WHERE favorito = 1 OR editado = 1 " +
            "OR textoEditado = 1 OR oculto = 1 OR chave LIKE 'usuario-%'"
    )
    suspend fun marcarPontosPendentes()

    @Insert
    suspend fun inserirPonto(p: Ponto): Long

    @Update
    suspend fun atualizarPonto(p: Ponto)

    @Query("UPDATE ponto SET favorito = :fav, atualizadoEm = :agora, pendente = 1 WHERE id = :id")
    suspend fun marcarFavorito(id: Long, fav: Boolean, agora: Long = System.currentTimeMillis())

    // ---- Revisões
    @Query("SELECT * FROM revisao WHERE apagada = 0 ORDER BY data DESC")
    fun revisoes(): Flow<List<Revisao>>

    @Query("SELECT * FROM revisao WHERE pontoId = :pontoId AND apagada = 0 ORDER BY data DESC")
    fun revisoesDo(pontoId: Long): Flow<List<Revisao>>

    @Query("SELECT * FROM revisao WHERE uuid = :uuid")
    suspend fun revisaoPorUuid(uuid: String): Revisao?

    @Query("SELECT * FROM revisao WHERE pendente = 1")
    suspend fun revisoesPendentes(): List<Revisao>

    @Query("UPDATE revisao SET pendente = 0 WHERE id = :id AND atualizadoEm = :versao")
    suspend fun revisaoEnviada(id: Long, versao: Long)

    @Update
    suspend fun atualizarRevisao(r: Revisao)

    @Query("SELECT * FROM revisao")
    suspend fun todasRevisoes(): List<Revisao>

    @Query("SELECT COUNT(DISTINCT pontoId) FROM revisao WHERE data >= :desde AND apagada = 0")
    suspend fun pontosRevisadosDesde(desde: Long): Int

    @Insert
    suspend fun inserirRevisao(r: Revisao): Long

    @Query("UPDATE revisao SET apagada = 1, atualizadoEm = :agora, pendente = 1 WHERE id = :id")
    suspend fun apagarRevisao(id: Long, agora: Long = System.currentTimeMillis())

    // ---- Anotações
    @Query("SELECT * FROM anotacao WHERE apagada = 0 ORDER BY criadaEm DESC")
    fun anotacoes(): Flow<List<Anotacao>>

    @Query("SELECT * FROM anotacao WHERE pontoId = :pontoId AND apagada = 0 ORDER BY criadaEm DESC")
    fun anotacoesDo(pontoId: Long): Flow<List<Anotacao>>

    @Query("SELECT * FROM anotacao WHERE uuid = :uuid")
    suspend fun anotacaoPorUuid(uuid: String): Anotacao?

    @Query("SELECT * FROM anotacao WHERE pendente = 1")
    suspend fun anotacoesPendentes(): List<Anotacao>

    @Query("UPDATE anotacao SET pendente = 0 WHERE id = :id AND atualizadoEm = :versao")
    suspend fun anotacaoEnviada(id: Long, versao: Long)

    @Query("SELECT * FROM anotacao")
    suspend fun todasAnotacoes(): List<Anotacao>

    @Insert
    suspend fun inserirAnotacao(a: Anotacao): Long

    @Update
    suspend fun atualizarAnotacao(a: Anotacao)

    @Query("UPDATE anotacao SET apagada = 1, atualizadoEm = :agora, pendente = 1 WHERE id = :id")
    suspend fun apagarAnotacao(id: Long, agora: Long = System.currentTimeMillis())

    /** Quantas alterações locais ainda não foram para a nuvem. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM revisao WHERE pendente = 1) + (SELECT COUNT(*) FROM anotacao WHERE pendente = 1) " +
            "+ (SELECT COUNT(*) FROM ponto WHERE pendente = 1)"
    )
    fun totalPendentes(): Flow<Int>

    @Query("UPDATE revisao SET pendente = 1, atualizadoEm = MAX(atualizadoEm, 1)")
    suspend fun marcarRevisoesPendentes()

    @Query("UPDATE anotacao SET pendente = 1, atualizadoEm = MAX(atualizadoEm, 1)")
    suspend fun marcarAnotacoesPendentes()

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

/**
 * v4: sincronização entre aparelhos. Cada revisão e anotação ganha um identificador único;
 * apagamentos passam a ser marcados; pontos criados no app ganham chave própria.
 * Tudo o que a usuária já fez fica pendente para subir na primeira sincronização.
 */
val MIGRACAO_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (t in listOf("revisao", "anotacao")) {
            db.execSQL("ALTER TABLE $t ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $t ADD COLUMN apagada INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $t ADD COLUMN atualizadoEm INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $t ADD COLUMN pendente INTEGER NOT NULL DEFAULT 1")
            db.execSQL("UPDATE $t SET uuid = lower(hex(randomblob(16)))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_${t}_uuid ON $t (uuid)")
        }
        db.execSQL("UPDATE revisao SET atualizadoEm = data")
        db.execSQL("UPDATE anotacao SET atualizadoEm = COALESCE(editadaEm, criadaEm)")
        db.execSQL("ALTER TABLE ponto ADD COLUMN atualizadoEm INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ponto ADD COLUMN pendente INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE ponto SET chave = 'usuario-' || lower(hex(randomblob(16))) WHERE chave IS NULL")
        db.execSQL(
            "UPDATE ponto SET pendente = 1, atualizadoEm = criadoEm WHERE favorito = 1 OR editado = 1 " +
                "OR textoEditado = 1 OR oculto = 1 OR chave LIKE 'usuario-%'"
        )
    }
}

/** v3: marca de texto da lei editado. Na v1/v2 a base não trazia textos, então qualquer texto existente foi colado pela usuária. */
val MIGRACAO_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ponto ADD COLUMN textoEditado INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE ponto SET textoEditado = 1 WHERE textoLei != ''")
    }
}

@Database(
    entities = [Disciplina::class, Ponto::class, Revisao::class, Anotacao::class],
    version = 4,
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
                .addMigrations(MIGRACAO_1_2, MIGRACAO_2_3, MIGRACAO_3_4)
                .build().also { instancia = it }
        }
    }
}
