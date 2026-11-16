package app.revisatrt.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revisatrt.data.Analise
import app.revisatrt.data.Anotacao
import app.revisatrt.data.Avaliacao
import app.revisatrt.data.Revisao
import app.revisatrt.ui.Etiqueta
import app.revisatrt.ui.EtiquetaAtencao
import app.revisatrt.ui.EtiquetaAtraso
import app.revisatrt.ui.EtiquetaImportancia
import app.revisatrt.ui.LocalRepo
import app.revisatrt.ui.Nav
import app.revisatrt.ui.TituloSecao
import app.revisatrt.ui.theme.LocalCores
import kotlinx.coroutines.launch
import java.net.URLEncoder

/** Links para consultar o texto oficial, deduzidos do título do ponto. */
fun fontesOficiais(titulo: String): List<Pair<String, String>> {
    val l = mutableListOf<Pair<String, String>>()
    if (Regex("\\bCLT\\b").containsMatchIn(titulo)) l += "CLT (Planalto)" to "https://www.planalto.gov.br/ccivil_03/decreto-lei/del5452compilado.htm"
    if (Regex("\\bCF\\b|ADCT").containsMatchIn(titulo)) l += "Constituição (Planalto)" to "https://www.planalto.gov.br/ccivil_03/constituicao/constituicaocompilado.htm"
    if (Regex("\\bCPC\\b").containsMatchIn(titulo)) l += "CPC (Planalto)" to "https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2015/lei/l13105.htm"
    l += "Pesquisar no Google" to "https://www.google.com/search?q=" + URLEncoder.encode(titulo, "UTF-8")
    return l
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PontoTela(id: Long, nav: Nav) {
    val repo = LocalRepo.current
    val cores = LocalCores.current
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val ponto by repo.dao.ponto(id).collectAsStateWithLifecycle(null)
    val revisoes by remember(id) { repo.dao.revisoesDo(id) }.collectAsStateWithLifecycle(emptyList())
    val notas by remember(id) { repo.dao.anotacoesDo(id) }.collectAsStateWithLifecycle(emptyList())
    val disciplinas by repo.disciplinas.collectAsStateWithLifecycle(emptyList())
    val p = ponto ?: return
    val disc = disciplinas.find { it.id == p.disciplinaId }

    var escolherAvaliacao by remember { mutableStateOf(false) }
    var editandoNota by remember { mutableStateOf<Anotacao?>(null) }
    var novaNota by remember { mutableStateOf(false) }
    var notaApagar by remember { mutableStateOf<Anotacao?>(null) }
    var revisaoApagar by remember { mutableStateOf<Revisao?>(null) }
    var editandoTexto by remember { mutableStateOf(false) }
    var apagarPonto by remember { mutableStateOf(false) }
    var ondeAberto by rememberSaveable { mutableStateOf(false) }

    val ultima = revisoes.firstOrNull()
    val atencao = ultima?.avaliacao == Avaliacao.DIFICIL

    TelaBase(
        titulo = disc?.nome ?: "",
        voltar = nav::voltar,
        snackbar = snackbar,
        acoes = {
            IconButton(onClick = { escopo.launch { repo.favoritar(p.id, !p.favorito) } }) {
                Icon(
                    if (p.favorito) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (p.favorito) "Remover dos favoritos" else "Favoritar",
                    tint = if (p.favorito) Color(0xFFE57FA6) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { nav.editar(p.id) }) { Icon(Icons.Filled.Edit, "Editar ponto") }
            IconButton(onClick = { apagarPonto = true }) { Icon(Icons.Filled.Delete, "Apagar ponto") }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(p.titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EtiquetaImportancia(p.importancia)
                    if (atencao) EtiquetaAtencao()
                    EtiquetaAtraso(Analise.faixaAtraso(ultima?.data))
                    if (p.vigencia.isNotBlank()) Etiqueta(p.vigencia, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (disc != null && disc.totalCadernos > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Caiu em ${p.cadernos} de ${disc.totalCadernos} cadernos · ${p.questoes} questões · " +
                            "Área Judiciária ${p.areaJudiciaria}/${disc.totalAJ} · Oficial de Justiça ${p.oficialJustica}/${disc.totalOJ}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                // Bloco de revisão
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp)
                ) {
                    Text(
                        if (ultima == null) "Ainda não revisado"
                        else "Última revisão: ${Analise.dataHora(ultima.data)} · ${Avaliacao.nome(ultima.avaliacao)}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium,
                    )
                    if (revisoes.size > 1) Text(
                        "${revisoes.size} revisões no total",
                        color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { escolherAvaliacao = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Marcar como revisado")
                    }
                }
            }
            if (atencao) item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cores.atencaoFundo).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Warning, null, tint = cores.atencao)
                    Spacer(Modifier.width(10.dp))
                    Text("Ponto de atenção: na última revisão você marcou como difícil.", color = cores.noAtencao, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (p.oQueCai.isNotBlank()) item { Bloco("O que a FCC cobra", p.oQueCai, MaterialTheme.colorScheme.surfaceContainerLowest) }
            if (p.atencao.isNotBlank()) item { Bloco("Atenção: vigência e pegadinhas", p.atencao, cores.atencaoFundo, cores.noAtencao) }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Texto da lei", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editandoTexto = true }) { Icon(Icons.Filled.Edit, "Editar texto da lei") }
                    }
                    if (p.textoLei.isBlank()) {
                        Text(
                            "O texto ainda não foi adicionado. Cole aqui o texto oficial (toque no lápis) ou consulte a fonte:",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val pars = remember(p.textoLei) { paragrafosLei(p.textoLei) }
                        // prévia: primeiros parágrafos (até ~700 caracteres)
                        var soma = 0
                        val previa = pars.takeWhile { soma += it.length; soma - it.length < 700 }.take(6)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { previa.forEach { ParagrafoLei(it) } }
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { nav.texto(p.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (previa.size < pars.size) "Ler texto completo (${pars.size} parágrafos)" else "Abrir no leitor")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fontesOficiais(p.titulo).forEach { (rotulo, url) ->
                            AssistChip(onClick = {
                                runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            }, label = { Text(rotulo) })
                        }
                    }
                }
            }
            if (p.ondeCaiu.isNotBlank() || p.outrasProvas.isNotBlank()) item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .clickable { ondeAberto = !ondeAberto }.padding(14.dp).animateContentSize()
                ) {
                    Row {
                        Text("Onde caiu", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(if (ondeAberto) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                    }
                    if (ondeAberto) {
                        if (p.ondeCaiu.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(p.ondeCaiu.replace("; ", "\n"), style = MaterialTheme.typography.bodySmall)
                        }
                        if (p.outrasProvas.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text("TRF-3, TRF-4 e TJ-CE (fora da contagem)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text(p.outrasProvas, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                TituloSecao("Anotações (${notas.size})") {
                    TextButton(onClick = { novaNota = true }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Nova")
                    }
                }
            }
            if (notas.isEmpty()) item {
                Text("Registre macetes, dúvidas e o que você errou. As anotações ficam salvas para as próximas revisões.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(notas, key = { "n${it.id}" }) { n ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 14.dp, top = 10.dp, bottom = 4.dp, end = 4.dp)
                ) {
                    SelectionContainer { Text(n.texto, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(end = 10.dp)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Analise.dataHora(n.criadaEm) + (n.editadaEm?.let { " · editada em ${Analise.dataHora(it)}" } ?: ""),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { editandoNota = n }) { Icon(Icons.Filled.Edit, "Editar anotação", Modifier.size(20.dp)) }
                        IconButton(onClick = { notaApagar = n }) { Icon(Icons.Filled.Delete, "Apagar anotação", Modifier.size(20.dp)) }
                    }
                }
            }
            item { TituloSecao("Histórico de revisões (${revisoes.size})") }
            if (revisoes.isEmpty()) item {
                Text("Nenhuma revisão registrada ainda.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(revisoes, key = { "r${it.id}" }) { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Analise.dataHora(r.data), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    val (fundo, rotulo) = when (r.avaliacao) {
                        Avaliacao.FACIL -> cores.facil to "Fácil"
                        Avaliacao.MEDIO -> cores.medio to "Médio"
                        Avaliacao.DIFICIL -> cores.dificil to "Difícil"
                        else -> MaterialTheme.colorScheme.surfaceVariant to "—"
                    }
                    Etiqueta(rotulo, fundo, Color(0xFF221D24))
                    IconButton(onClick = { revisaoApagar = r }) { Icon(Icons.Filled.Close, "Apagar revisão", Modifier.size(18.dp)) }
                }
            }
        }
    }

    if (escolherAvaliacao) {
        AlertDialog(
            onDismissRequest = { escolherAvaliacao = false },
            title = { Text("Como foi a revisão?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(Avaliacao.FACIL, "Fácil", cores.facil),
                        Triple(Avaliacao.MEDIO, "Médio", cores.medio),
                        Triple(Avaliacao.DIFICIL, "Difícil (vira ponto de atenção)", cores.dificil),
                    ).forEach { (valor, rotulo, cor) ->
                        Button(
                            onClick = {
                                escolherAvaliacao = false
                                escopo.launch {
                                    val idRev = repo.revisar(p.id, valor)
                                    val res = snackbar.showSnackbar("Revisão registrada: ${Avaliacao.nome(valor)}", "Desfazer", duration = SnackbarDuration.Short)
                                    if (res == SnackbarResult.ActionPerformed) repo.apagarRevisao(idRev)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = cor, contentColor = Color(0xFF221D24)),
                        ) { Text(rotulo) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { escolherAvaliacao = false }) { Text("Cancelar") } },
        )
    }

    if (novaNota || editandoNota != null) {
        EditorTexto(
            titulo = if (editandoNota == null) "Nova anotação" else "Editar anotação",
            inicial = editandoNota?.texto ?: "",
            onFechar = { novaNota = false; editandoNota = null },
            onSalvar = { t ->
                val existente = editandoNota
                escopo.launch { if (t.isNotBlank()) repo.salvarAnotacao(p.id, t.trim(), existente) }
                novaNota = false; editandoNota = null
            },
        )
    }

    if (editandoTexto) {
        EditorTexto(
            titulo = "Texto da lei", inicial = p.textoLei,
            onFechar = { editandoTexto = false },
            onSalvar = { t ->
                escopo.launch { repo.salvarTextoLei(p, t.trim()) }
                editandoTexto = false
            },
        )
    }

    notaApagar?.let { n ->
        Confirmar("Apagar anotação?", "Esta anotação será apagada definitivamente.", { notaApagar = null }) {
            escopo.launch { repo.apagarAnotacao(n.id) }; notaApagar = null
        }
    }
    revisaoApagar?.let { r ->
        Confirmar("Apagar revisão?", "Remover a revisão de ${Analise.dataHora(r.data)} do histórico.", { revisaoApagar = null }) {
            escopo.launch { repo.apagarRevisao(r.id) }; revisaoApagar = null
        }
    }
    if (apagarPonto) {
        Confirmar("Apagar ponto?", "O ponto, suas anotações e seu histórico de revisões serão apagados.", { apagarPonto = false }) {
            apagarPonto = false
            escopo.launch { repo.apagarPonto(p) }
            nav.voltar()
        }
    }
}

@Composable
private fun Bloco(titulo: String, texto: String, fundo: Color, corTexto: Color = MaterialTheme.colorScheme.onSurface) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(fundo).padding(14.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = corTexto)
        Spacer(Modifier.height(6.dp))
        SelectionContainer { Text(texto, style = MaterialTheme.typography.bodyMedium, color = corTexto) }
    }
}

@Composable
fun EditorTexto(titulo: String, inicial: String, onFechar: () -> Unit, onSalvar: (String) -> Unit) {
    var texto by rememberSaveable { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(
                value = texto, onValueChange = { texto = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onSalvar(texto) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } },
    )
}

@Composable
fun Confirmar(titulo: String, texto: String, onCancelar: () -> Unit, onConfirmar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = onConfirmar) { Text("Apagar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

