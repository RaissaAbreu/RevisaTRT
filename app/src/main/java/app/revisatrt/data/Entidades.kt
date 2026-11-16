package app.revisatrt.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "disciplina", indices = [Index(value = ["chave"], unique = true)])
data class Disciplina(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chave: String,
    val nome: String,
    val grupo: String,
    val ordem: Int,
    val edital: String = "",
    /** Tamanho da amostra de provas analisadas (ex.: 25 cadernos, 15 de Área Judiciária, 10 de Oficial de Justiça). */
    @ColumnInfo(defaultValue = "0") val totalCadernos: Int = 0,
    @ColumnInfo(defaultValue = "0") val totalAJ: Int = 0,
    @ColumnInfo(defaultValue = "0") val totalOJ: Int = 0,
)

/** Um ponto de estudo: artigo(s), súmula, OJ, tese etc. */
@Serializable
@Entity(
    tableName = "ponto",
    indices = [Index(value = ["chave"], unique = true), Index("disciplinaId")],
    foreignKeys = [ForeignKey(
        entity = Disciplina::class, parentColumns = ["id"], childColumns = ["disciplinaId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class Ponto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Chave estável vinda da planilha (ex.: "trabalho-1"); nula para pontos criados no app. */
    val chave: String? = null,
    val disciplinaId: Long,
    val ordem: Int = 0,
    val titulo: String,
    val oQueCai: String = "",
    /** 4 = Altíssima, 3 = Alta, 2 = Média, 1 = Baixa, 0 = Não caiu na amostra */
    val importancia: Int = 2,
    val cadernos: Int = 0,
    val questoes: Int = 0,
    val areaJudiciaria: Int = 0,
    val oficialJustica: Int = 0,
    val vigencia: String = "",
    val atencao: String = "",
    val ondeCaiu: String = "",
    /** Questões em provas fora da contagem (TRF-3, TRF-4, TJ-CE). */
    @ColumnInfo(defaultValue = "") val outrasProvas: String = "",
    val textoLei: String = "",
    val favorito: Boolean = false,
    /** Usuário editou o conteúdo: atualizações da base não sobrescrevem. */
    val editado: Boolean = false,
    /** Ponto da base que o usuário apagou (fica oculto para não voltar na próxima atualização). */
    val oculto: Boolean = false,
    val criadoEm: Long = System.currentTimeMillis(),
)

object Avaliacao {
    const val SEM = 0
    const val FACIL = 1
    const val MEDIO = 2
    const val DIFICIL = 3

    fun nome(a: Int) = when (a) {
        FACIL -> "Fácil"
        MEDIO -> "Médio"
        DIFICIL -> "Difícil"
        else -> "Sem avaliação"
    }
}

@Serializable
@Entity(
    tableName = "revisao",
    indices = [Index("pontoId"), Index("data")],
    foreignKeys = [ForeignKey(
        entity = Ponto::class, parentColumns = ["id"], childColumns = ["pontoId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class Revisao(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pontoId: Long,
    val data: Long,
    val avaliacao: Int,
)

@Serializable
@Entity(
    tableName = "anotacao",
    indices = [Index("pontoId")],
    foreignKeys = [ForeignKey(
        entity = Ponto::class, parentColumns = ["id"], childColumns = ["pontoId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class Anotacao(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pontoId: Long,
    val texto: String,
    val criadaEm: Long,
    val editadaEm: Long? = null,
)

/** Ponto com dados agregados de revisão. */
data class PontoResumo(
    @Embedded val ponto: Ponto,
    @ColumnInfo(name = "ultimaRevisao") val ultimaRevisao: Long?,
    @ColumnInfo(name = "ultimaAvaliacao") val ultimaAvaliacao: Int?,
    @ColumnInfo(name = "totalRevisoes") val totalRevisoes: Int,
    @ColumnInfo(name = "totalAnotacoes") val totalAnotacoes: Int,
) {
    val id get() = ponto.id
    val atencao get() = ultimaAvaliacao == Avaliacao.DIFICIL
    val nuncaRevisado get() = ultimaRevisao == null
}
