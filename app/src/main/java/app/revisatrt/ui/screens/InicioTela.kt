package app.revisatrt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revisatrt.data.Ajustes
import app.revisatrt.data.Analise
import app.revisatrt.data.Disciplina
import app.revisatrt.data.PontoResumo
import app.revisatrt.ui.BarraProgresso
import app.revisatrt.ui.CartaoClicavel
import app.revisatrt.ui.EtiquetaAtraso
import app.revisatrt.ui.LocalRepo
import app.revisatrt.ui.Nav
import app.revisatrt.ui.TituloSecao
import app.revisatrt.ui.theme.LocalCores
import app.revisatrt.ui.theme.corDaDisciplina

/** Situação de uma disciplina. */
data class SituacaoDisciplina(
    val disciplina: Disciplina,
    val total: Int,
    val revisados: Int,
    val ultima: Long?,
    val atrasados: Int,
    val atencao: Int,
)

fun situacoes(disciplinas: List<Disciplina>, pontos: List<PontoResumo>): List<SituacaoDisciplina> {
    val porDisc = pontos.groupBy { it.ponto.disciplinaId }
    return disciplinas.map { d ->
        val ps = porDisc[d.id].orEmpty()
        SituacaoDisciplina(
            d, ps.size, ps.count { !it.nuncaRevisado }, ps.mapNotNull { it.ultimaRevisao }.maxOrNull(),
            ps.count { Analise.faixaAtraso(it.ultimaRevisao) > 0 }, ps.count { it.atencao },
        )
    }
}

@Composable
fun InicioTela(nav: Nav) {
    val repo = LocalRepo.current
    val disciplinas by repo.disciplinas.collectAsStateWithLifecycle(emptyList())
    val pontos by repo.pontos.collectAsStateWithLifecycle(emptyList())
    val revisoes by repo.revisoes.collectAsStateWithLifecycle(emptyList())
    val ajustes by repo.ajustes.collectAsStateWithLifecycle(Ajustes())
    val cores = LocalCores.current

    val sit = situacoes(disciplinas, pontos)
    val hoje = Analise.revisadosHoje(revisoes)
    val semana = Analise.revisadosNaSemana(revisoes)
    val atencao = pontos.count { it.atencao }
    val atrasados = pontos.count { Analise.faixaAtraso(it.ultimaRevisao) > 0 }
    val nunca = pontos.count { it.nuncaRevisado }
    val dias = Analise.diasParaProva()

    TelaBase(titulo = "RevisaTRT", subtitulo = "TRT-RS · Analista Judiciário · FCC") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // Contagem regressiva + meta
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.primaryContainer).padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DateRange, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (dias > 0) "Faltam $dias dias para a prova" else if (dias == 0L) "A prova é hoje! Boa prova!" else "Prova realizada",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text("31/01/2027", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Meta de hoje: $hoje de ${ajustes.metaDiaria} pontos" + if (hoje >= ajustes.metaDiaria) "  ✓" else "",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    BarraProgresso(hoje / ajustes.metaDiaria.coerceAtLeast(1).toFloat())
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Semana: $semana de ${ajustes.metaSemanal} revisões",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    BarraProgresso(semana / ajustes.metaSemanal.coerceAtLeast(1).toFloat(), cor = MaterialTheme.colorScheme.secondary)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CartaoClicavel({ nav.estatisticas() }, MaterialTheme.colorScheme.tertiaryContainer, Modifier.weight(1f)) {
                        Column {
                            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.height(6.dp))
                            Text("Estatísticas", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("Ver progresso", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                    CartaoClicavel({ nav.aba("revisar") }, cores.atencaoFundo, Modifier.weight(1f)) {
                        Column {
                            Icon(Icons.Filled.Warning, null, tint = cores.atencao)
                            Spacer(Modifier.height(6.dp))
                            Text("$atencao de atenção", fontWeight = FontWeight.SemiBold, color = cores.noAtencao)
                            Text("$atrasados atrasados · $nunca novos", style = MaterialTheme.typography.bodySmall, color = cores.noAtencao)
                        }
                    }
                }
            }

            val semRevisao = sit.filter { it.total > 0 && it.ultima != null && Analise.faixaAtraso(it.ultima) > 0 }
            if (semRevisao.isNotEmpty()) {
                item { TituloSecao("Disciplinas para retomar") }
                items(semRevisao, key = { "alerta-${it.disciplina.id}" }) { s ->
                    CartaoClicavel({ nav.disciplina(s.disciplina.id) }, MaterialTheme.colorScheme.surfaceContainerHigh, Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.disciplina.nome, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Sem revisão há ${Analise.diasDesde(s.ultima!!)} dias",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            EtiquetaAtraso(Analise.faixaAtraso(s.ultima))
                        }
                    }
                }
            }

            for (grupo in listOf("Conhecimentos Específicos", "Conhecimentos Gerais")) {
                val doGrupo = sit.filter { it.disciplina.grupo == grupo }
                if (doGrupo.isEmpty()) continue
                item(key = grupo) { TituloSecao(grupo) }
                items(doGrupo, key = { it.disciplina.id }) { s -> LinhaDisciplina(s) { nav.disciplina(s.disciplina.id) } }
            }
        }
    }
}

@Composable
private fun LinhaDisciplina(s: SituacaoDisciplina, onClick: () -> Unit) {
    val cores = LocalCores.current
    CartaoClicavel(onClick, MaterialTheme.colorScheme.surfaceContainerLowest, Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(corDaDisciplina(cores, s.disciplina.ordem)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.disciplina.nome, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                if (s.total == 0) {
                    Text("Aguardando a base de pontos · toque para adicionar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val partes = mutableListOf("${s.revisados}/${s.total} revisados")
                    s.ultima?.let { partes += "última: ${Analise.data(it)}" }
                    if (s.atencao > 0) partes += "${s.atencao} de atenção"
                    Text(partes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    BarraProgresso(s.revisados / s.total.toFloat())
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
