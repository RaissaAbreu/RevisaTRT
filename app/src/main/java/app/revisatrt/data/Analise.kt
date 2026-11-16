package app.revisatrt.data

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Regras de revisão, atraso e estatísticas. */
object Analise {
    private val zona: ZoneId get() = ZoneId.systemDefault()
    private val fmtData = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("pt", "BR"))
    private val fmtDataHora = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))

    val DATA_PROVA: LocalDate = LocalDate.of(2027, 1, 31)

    fun dia(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zona).toLocalDate()
    fun hoje(): LocalDate = LocalDate.now(zona)
    fun inicioDoDia(d: LocalDate): Long = d.atStartOfDay(zona).toInstant().toEpochMilli()

    fun diasDesde(ms: Long): Long = ChronoUnit.DAYS.between(dia(ms), hoje())

    fun diasParaProva(): Long = ChronoUnit.DAYS.between(hoje(), DATA_PROVA)

    fun data(ms: Long): String = dia(ms).format(fmtData)
    fun dataHora(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zona).format(fmtDataHora)

    fun relativo(ms: Long?): String {
        if (ms == null) return "Nunca revisado"
        return when (val d = diasDesde(ms)) {
            0L -> "Revisado hoje"
            1L -> "Revisado ontem"
            else -> "Revisado há $d dias"
        }
    }

    /** 0 = em dia; 7, 15 ou 30 = faixa de atraso. */
    fun faixaAtraso(ultima: Long?): Int {
        if (ultima == null) return 0
        val d = diasDesde(ultima)
        return when {
            d >= 30 -> 30
            d >= 15 -> 15
            d >= 7 -> 7
            else -> 0
        }
    }

    fun importanciaNome(i: Int) = when (i) {
        4 -> "Altíssima"
        3 -> "Alta"
        2 -> "Média"
        1 -> "Baixa"
        else -> "Não caiu"
    }

    /** Ordem de estudo: importância, depois incidência em cadernos/questões, depois ordem da planilha. */
    val ordemImportancia = compareByDescending<PontoResumo> { it.ponto.importancia }
        .thenByDescending { it.ponto.cadernos }
        .thenByDescending { it.ponto.questoes }
        .thenBy { it.ponto.ordem }
        .thenBy { it.ponto.id }

    fun normalizar(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)

    /** Revisões (pontos distintos) feitas hoje. */
    fun revisadosHoje(revisoes: List<Revisao>): Int {
        val inicio = inicioDoDia(hoje())
        return revisoes.filter { it.data >= inicio }.map { it.pontoId }.distinct().size
    }

    fun revisadosNaSemana(revisoes: List<Revisao>): Int {
        val h = hoje()
        val inicio = inicioDoDia(h.minusDays((h.dayOfWeek.value - 1).toLong()))
        return revisoes.filter { it.data >= inicio }.size
    }

    /** Dias seguidos com pelo menos uma revisão (até hoje ou ontem). */
    fun sequencia(revisoes: List<Revisao>): Int {
        val dias = revisoes.map { dia(it.data) }.toSet()
        var d = hoje()
        if (d !in dias) d = d.minusDays(1)
        var n = 0
        while (d in dias) { n++; d = d.minusDays(1) }
        return n
    }

    fun recorde(revisoes: List<Revisao>): Int {
        val dias = revisoes.map { dia(it.data) }.toSortedSet()
        var melhor = 0; var atual = 0; var anterior: LocalDate? = null
        for (d in dias) {
            atual = if (anterior != null && anterior.plusDays(1) == d) atual + 1 else 1
            melhor = maxOf(melhor, atual); anterior = d
        }
        return melhor
    }

    /** Quantidade de revisões por dia nos últimos [n] dias (mais antigo primeiro). */
    fun porDia(revisoes: List<Revisao>, n: Int): List<Pair<LocalDate, Int>> {
        val contagem = revisoes.groupingBy { dia(it.data) }.eachCount()
        val h = hoje()
        return (n - 1 downTo 0).map { h.minusDays(it.toLong()) }.map { it to (contagem[it] ?: 0) }
    }
}
