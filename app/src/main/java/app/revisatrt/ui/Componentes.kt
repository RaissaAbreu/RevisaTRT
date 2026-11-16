package app.revisatrt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.revisatrt.data.Analise
import app.revisatrt.data.Avaliacao
import app.revisatrt.data.PontoResumo
import app.revisatrt.data.Repositorio
import app.revisatrt.ui.theme.LocalCores

val LocalRepo = compositionLocalOf<Repositorio> { error("Repositorio não fornecido") }

@Composable
fun Etiqueta(texto: String, fundo: Color, cor: Color, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = cor,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(fundo)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun EtiquetaImportancia(importancia: Int) {
    val c = LocalCores.current
    Etiqueta(Analise.importanciaNome(importancia), c.importancia[importancia.coerceIn(0, 4)], c.noImportancia)
}

@Composable
fun EtiquetaAtraso(faixa: Int) {
    if (faixa == 0) return
    val c = LocalCores.current
    val fundo = when (faixa) {
        30 -> MaterialTheme.colorScheme.errorContainer
        15 -> c.atencaoFundo
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val cor = when (faixa) {
        30 -> MaterialTheme.colorScheme.onErrorContainer
        15 -> c.noAtencao
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Etiqueta("$faixa+ dias", fundo, cor)
}

@Composable
fun EtiquetaAtencao() {
    val c = LocalCores.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.atencaoFundo).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Icon(Icons.Filled.Warning, null, tint = c.atencao, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text("Atenção", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = c.noAtencao)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartaoPonto(
    r: PontoResumo,
    onClick: () -> Unit,
    onFavorito: () -> Unit,
    disciplina: String? = null,
) {
    val c = LocalCores.current
    val atencao = r.atencao
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (atencao) Modifier.border(1.5.dp, c.atencao, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Column(Modifier.weight(1f)) {
                if (disciplina != null) {
                    Text(disciplina, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    r.ponto.titulo,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EtiquetaImportancia(r.ponto.importancia)
                    if (atencao) EtiquetaAtencao()
                    EtiquetaAtraso(Analise.faixaAtraso(r.ultimaRevisao))
                    if (r.ponto.vigencia.isNotBlank() && r.ponto.vigencia != "Vigente") {
                        Etiqueta(r.ponto.vigencia, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(6.dp))
                val extra = buildString {
                    append(Analise.relativo(r.ultimaRevisao))
                    if (r.ultimaAvaliacao != null && r.ultimaAvaliacao != Avaliacao.SEM) append(" · ${Avaliacao.nome(r.ultimaAvaliacao)}")
                    if (r.totalRevisoes > 0) append(" · ${r.totalRevisoes}x")
                    if (r.totalAnotacoes > 0) append(" · ${r.totalAnotacoes} anotaç${if (r.totalAnotacoes == 1) "ão" else "ões"}")
                }
                Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onFavorito) {
                Icon(
                    if (r.ponto.favorito) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (r.ponto.favorito) "Remover dos favoritos" else "Favoritar",
                    tint = if (r.ponto.favorito) Color(0xFFE57FA6) else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
fun TituloSecao(texto: String, modifier: Modifier = Modifier, acao: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        acao?.invoke()
    }
}

@Composable
fun BarraProgresso(fracao: Float, cor: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fracao.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
        color = cor,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        drawStopIndicator = {},
    )
}

@Composable
fun Vazio(texto: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CartaoClicavel(onClick: () -> Unit, fundo: Color, modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(fundo)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) { conteudo() }
}
