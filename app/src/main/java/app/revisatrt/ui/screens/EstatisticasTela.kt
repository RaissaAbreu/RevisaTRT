package app.revisatrt.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revisatrt.data.Ajustes
import app.revisatrt.data.Analise
import app.revisatrt.data.Avaliacao
import app.revisatrt.ui.BarraProgresso
import app.revisatrt.ui.CartaoClicavel
import app.revisatrt.ui.LocalRepo
import app.revisatrt.ui.Nav
import app.revisatrt.ui.TituloSecao
import app.revisatrt.ui.theme.LocalCores
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun EstatisticasTela(nav: Nav) {
    val repo = LocalRepo.current
    val cores = LocalCores.current
    val disciplinas by repo.disciplinas.collectAsStateWithLifecycle(emptyList())
    val pontos by repo.pontos.collectAsStateWithLifecycle(emptyList())
    val revisoes by repo.revisoes.collectAsStateWithLifecycle(emptyList())
    val ajustes by repo.ajustes.collectAsStateWithLifecycle(Ajustes())

    val total = pontos.size
    val revisados = pontos.count { !it.nuncaRevisado }
    val porDia = Analise.porDia(revisoes, 14)
    val sit = situacoes(disciplinas, pontos).filter { it.total > 0 }

    TelaBase(titulo = "Estatísticas", voltar = nav::voltar) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Numero("Revisados", if (total == 0) "0%" else "${revisados * 100 / total}%", "$revisados de $total pontos", Modifier.weight(1f))
                    Numero("Revisões", "${revisoes.size}", "no total", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Numero("Sequência", "${Analise.sequencia(revisoes)} dias", "recorde: ${Analise.recorde(revisoes)} dias", Modifier.weight(1f))
                    Numero("Hoje", "${Analise.revisadosHoje(revisoes)}/${ajustes.metaDiaria}", "semana: ${Analise.revisadosNaSemana(revisoes)}/${ajustes.metaSemanal}", Modifier.weight(1f))
                }
            }
            item {
                TituloSecao("Revisões nos últimos 14 dias")
                GraficoBarras(porDia.map { it.second }, porDia.map { it.first.dayOfMonth.toString() },
                    porDia.map { "${it.first.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pt", "BR"))} ${Analise.data(Analise.inicioDoDia(it.first))}" })
            }
            item {
                TituloSecao("Como foi a última revisão de cada ponto")
                val ult = pontos.mapNotNull { it.ultimaAvaliacao }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Contagem("Fácil", ult.count { it == Avaliacao.FACIL }, cores.facil, Modifier.weight(1f))
                    Contagem("Médio", ult.count { it == Avaliacao.MEDIO }, cores.medio, Modifier.weight(1f))
                    Contagem("Difícil", ult.count { it == Avaliacao.DIFICIL }, cores.dificil, Modifier.weight(1f))
                }
            }
            item { TituloSecao("Por importância") }
            items((4 downTo 0).toList()) { i ->
                val ps = pontos.filter { it.ponto.importancia == i }
                if (ps.isNotEmpty()) {
                    val r = ps.count { !it.nuncaRevisado }
                    Column {
                        Row {
                            Text(Analise.importanciaNome(i), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("$r/${ps.size}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        BarraProgresso(r / ps.size.toFloat())
                    }
                }
            }
            item { TituloSecao("Por disciplina") }
            items(sit, key = { it.disciplina.id }) { s ->
                CartaoClicavel({ nav.disciplina(s.disciplina.id) }, MaterialTheme.colorScheme.surfaceContainerLowest, Modifier.fillMaxWidth()) {
                    Column {
                        Row {
                            Text(s.disciplina.nome, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${s.revisados * 100 / s.total}%", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "${s.revisados}/${s.total} revisados · ${s.atrasados} atrasados · ${s.atencao} de atenção",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        BarraProgresso(s.revisados / s.total.toFloat())
                    }
                }
            }
            val dificeis = pontos.filter { it.atencao }.sortedWith(Analise.ordemImportancia)
            if (dificeis.isNotEmpty()) {
                item { TituloSecao("Pontos de atenção (${dificeis.size})") }
                items(dificeis, key = { "a${it.id}" }) { r ->
                    CartaoClicavel({ nav.ponto(r.id) }, cores.atencaoFundo, Modifier.fillMaxWidth()) {
                        Text(r.ponto.titulo, color = cores.noAtencao, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Numero(rotulo: String, valor: String, detalhe: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp)) {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(valor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Contagem(rotulo: String, n: Int, cor: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(cor))
            Spacer(Modifier.width(6.dp))
            Text(rotulo, style = MaterialTheme.typography.labelMedium)
        }
        Text("$n", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/** Barras de uma série só; toque numa barra para ver o valor. */
@Composable
private fun GraficoBarras(valores: List<Int>, rotulos: List<String>, detalhes: List<String>) {
    val cor = MaterialTheme.colorScheme.primary
    val grade = MaterialTheme.colorScheme.outlineVariant
    val destaque = MaterialTheme.colorScheme.onSurface
    var selecionado by remember { mutableStateOf(valores.lastIndex) }
    val max = (valores.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(14.dp)
    ) {
        val s = selecionado.coerceIn(0, valores.lastIndex.coerceAtLeast(0))
        Text(
            if (valores.isEmpty()) "" else "${detalhes[s]}: ${valores[s]} revisão(ões)",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
        )
        Text("Máximo no período: ${valores.maxOrNull() ?: 0}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier.fillMaxWidth().height(140.dp).pointerInput(valores.size) {
                detectTapGestures { o -> selecionado = (o.x / (size.width / valores.size.toFloat())).toInt().coerceIn(0, valores.lastIndex) }
            }
        ) {
            val n = valores.size
            if (n == 0) return@Canvas
            val passo = size.width / n
            val largura = passo * 0.6f
            // linha de base
            drawLine(grade, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 2f)
            valores.forEachIndexed { i, v ->
                if (v == 0) return@forEachIndexed
                val h = (size.height - 4f) * v / max
                val x = i * passo + (passo - largura) / 2
                barraArredondada(Offset(x, size.height - h), Size(largura, h), if (i == s) destaque else cor)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            rotulos.forEachIndexed { i, r ->
                Text(
                    if (i % 2 == (rotulos.size - 1) % 2) r else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** Barra com cantos de 4dp só no topo, apoiada na linha de base. */
private fun DrawScope.barraArredondada(topo: Offset, tam: Size, cor: Color) {
    val r = minOf(4.dp.toPx(), tam.width / 2, tam.height)
    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = topo.x, top = topo.y, right = topo.x + tam.width, bottom = topo.y + tam.height,
                topLeftCornerRadius = CornerRadius(r, r), topRightCornerRadius = CornerRadius(r, r),
                bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
            )
        )
    }
    drawPath(path, cor)
}
