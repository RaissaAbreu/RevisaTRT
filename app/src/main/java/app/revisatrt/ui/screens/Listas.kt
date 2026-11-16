package app.revisatrt.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revisatrt.data.Analise
import app.revisatrt.data.PontoResumo
import app.revisatrt.ui.BarraProgresso
import app.revisatrt.ui.CartaoClicavel
import app.revisatrt.ui.CartaoPonto
import app.revisatrt.ui.EtiquetaAtraso
import app.revisatrt.ui.LocalRepo
import app.revisatrt.ui.Nav
import app.revisatrt.ui.TituloSecao
import app.revisatrt.ui.Vazio
import kotlinx.coroutines.launch

enum class Filtro(val rotulo: String, val teste: (PontoResumo) -> Boolean) {
    TODOS("Todos", { true }),
    NUNCA("Nunca revisados", { it.nuncaRevisado }),
    ATRASADOS("Atrasados (7+ dias)", { Analise.faixaAtraso(it.ultimaRevisao) > 0 }),
    ATENCAO("Atenção", { it.atencao }),
    FAVORITOS("Favoritos", { it.ponto.favorito }),
}

@Composable
fun DisciplinaTela(id: Long, nav: Nav) {
    val repo = LocalRepo.current
    val escopo = rememberCoroutineScope()
    val disciplinas by repo.disciplinas.collectAsStateWithLifecycle(emptyList())
    val todos by repo.pontos.collectAsStateWithLifecycle(emptyList())
    val d = disciplinas.find { it.id == id }
    val pontos = todos.filter { it.ponto.disciplinaId == id }.sortedWith(Analise.ordemImportancia)
    var filtro by rememberSaveable { mutableStateOf(Filtro.TODOS) }
    var editalAberto by rememberSaveable { mutableStateOf(false) }
    val revisados = pontos.count { !it.nuncaRevisado }

    TelaBase(
        titulo = d?.nome ?: "",
        subtitulo = if (pontos.isEmpty()) null else "$revisados de ${pontos.size} pontos revisados",
        voltar = nav::voltar,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { nav.editar(0, id) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Novo ponto") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (pontos.isNotEmpty()) item { BarraProgresso(revisados / pontos.size.toFloat()) }
            if (d != null && d.edital.isNotBlank()) item {
                Column(
                    Modifier.fillMaxWidth().animateContentSize()
                        .clickable { editalAberto = !editalAberto }.padding(vertical = 4.dp)
                ) {
                    Row {
                        Text("Conteúdo do edital", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(if (editalAberto) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                    }
                    if (editalAberto) {
                        Spacer(Modifier.height(6.dp))
                        Text(d.edital, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Filtro.entries) { f ->
                        val n = pontos.count(f.teste)
                        FilterChip(selected = filtro == f, onClick = { filtro = f }, label = { Text("${f.rotulo} ($n)") })
                    }
                }
            }
            val lista = pontos.filter(filtro.teste)
            if (pontos.isEmpty()) item {
                Vazio("Ainda não há pontos nesta disciplina.\nQuando a sua planilha estiver pronta, eles entram aqui. Você também pode adicionar pelo botão \"Novo ponto\".")
            } else if (lista.isEmpty()) item { Vazio("Nenhum ponto neste filtro.") }
            items(lista, key = { it.id }) { r ->
                CartaoPonto(r, onClick = { nav.ponto(r.id) }, onFavorito = { escopo.launch { repo.favoritar(r.id, !r.ponto.favorito) } })
            }
        }
    }
}

private enum class Grupo(val rotulo: String, val teste: (PontoResumo) -> Boolean) {
    ATENCAO("Atenção", { it.atencao }),
    D30("30+ dias", { Analise.faixaAtraso(it.ultimaRevisao) == 30 }),
    D15("15+ dias", { Analise.faixaAtraso(it.ultimaRevisao) == 15 }),
    D7("7+ dias", { Analise.faixaAtraso(it.ultimaRevisao) == 7 }),
    NUNCA("Nunca revisados", { it.nuncaRevisado }),
}

@Composable
fun RevisarTela(nav: Nav) {
    val repo = LocalRepo.current
    val escopo = rememberCoroutineScope()
    val disciplinas by repo.disciplinas.collectAsStateWithLifecycle(emptyList())
    val pontos by repo.pontos.collectAsStateWithLifecycle(emptyList())
    val nomes = disciplinas.associate { it.id to it.nome }
    val sit = situacoes(disciplinas, pontos).filter { it.total > 0 }
    var grupo by rememberSaveable { mutableStateOf<String?>(null) }
    val atual = Grupo.entries.find { it.name == grupo } ?: Grupo.entries.firstOrNull { g -> pontos.any(g.teste) } ?: Grupo.NUNCA
    val lista = pontos.filter(atual.teste).sortedWith(Analise.ordemImportancia)

    TelaBase(titulo = "Revisar", subtitulo = "Sugestões por tempo sem revisão e dificuldade") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (sit.isNotEmpty()) {
                item { TituloSecao("Por disciplina") }
                items(sit, key = { "d${it.disciplina.id}" }) { s ->
                    CartaoClicavel({ nav.disciplina(s.disciplina.id) }, MaterialTheme.colorScheme.surfaceContainerHigh, Modifier.fillMaxWidth()) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(s.disciplina.nome, fontWeight = FontWeight.SemiBold)
                                val texto = when {
                                    s.ultima == null -> "Nunca revisada"
                                    else -> "Última revisão: ${Analise.relativo(s.ultima).removePrefix("Revisado ").replaceFirstChar { it.uppercase() }}"
                                }
                                Text(
                                    "$texto · ${s.atrasados} atrasados · ${s.total - s.revisados} nunca revisados",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            EtiquetaAtraso(Analise.faixaAtraso(s.ultima))
                        }
                    }
                }
            }
            item { TituloSecao("Pontos sugeridos") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Grupo.entries) { g ->
                        FilterChip(selected = atual == g, onClick = { grupo = g.name }, label = { Text("${g.rotulo} (${pontos.count(g.teste)})") })
                    }
                }
            }
            if (lista.isEmpty()) item { Vazio("Nada aqui. Muito bem!") }
            items(lista, key = { it.id }) { r ->
                CartaoPonto(
                    r, onClick = { nav.ponto(r.id) },
                    onFavorito = { escopo.launch { repo.favoritar(r.id, !r.ponto.favorito) } },
                    disciplina = nomes[r.ponto.disciplinaId],
                )
            }
        }
    }
}

@Composable
fun BuscaTela(nav: Nav) {
    val repo = LocalRepo.current
    val escopo = rememberCoroutineScope()
    val disciplinas by repo.disciplinas.collectAsStateWithLifecycle(emptyList())
    val pontos by repo.pontos.collectAsStateWithLifecycle(emptyList())
    val anotacoes by repo.anotacoes.collectAsStateWithLifecycle(emptyList())
    val nomes = disciplinas.associate { it.id to it.nome }
    var termo by rememberSaveable { mutableStateOf("") }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (termo.isEmpty()) runCatching { foco.requestFocus() } }

    val palavras = Analise.normalizar(termo).split(Regex("\\s+")).filter { it.isNotBlank() }
    // índice normalizado (sem acento, minúsculo), recalculado só quando os dados mudam
    val indice = remember(pontos, anotacoes) {
        val notasPorPonto = anotacoes.groupBy { it.pontoId }
        pontos.map { r ->
            val p = r.ponto
            val campos = listOf(
                "título" to p.titulo, "o que a FCC cobra" to p.oQueCai, "pegadinhas" to p.atencao,
                "texto da lei" to p.textoLei, "onde caiu" to p.ondeCaiu + " " + p.outrasProvas, "vigência" to p.vigencia,
            ) + notasPorPonto[p.id].orEmpty().map { "anotação" to it.texto }
            val norm = campos.map { (nome, t) -> nome to Analise.normalizar(t) }
            Triple(r, norm, norm.joinToString(" ") { it.second })
        }
    }
    val resultados = if (palavras.isEmpty()) emptyList() else indice.mapNotNull { (r, campos, tudo) ->
        if (palavras.all { it in tudo }) {
            r to campos.filter { (_, t) -> palavras.any { it in t } }.map { it.first }.distinct()
        } else null
    }.sortedWith(compareBy<Pair<PontoResumo, List<String>>> { if ("título" in it.second) 0 else 1 }.thenComparator { a, b -> Analise.ordemImportancia.compare(a.first, b.first) })

    TelaBase(titulo = "Busca") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = termo, onValueChange = { termo = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(foco),
                    placeholder = { Text("Ex.: 331, terceirização, férias…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (termo.isNotEmpty()) IconButton(onClick = { termo = "" }) { Icon(Icons.Filled.Clear, "Limpar") } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = MaterialTheme.shapes.extraLarge,
                )
            }
            if (palavras.isEmpty()) item {
                Vazio("Busque por número (\"331\"), palavra (\"terceirização\") ou trecho. A busca olha título, conteúdo, pegadinhas, texto da lei e suas anotações.")
            } else {
                item {
                    Text("${resultados.size} resultado(s)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(resultados, key = { it.first.id }) { (r, onde) ->
                    Column {
                        CartaoPonto(
                            r, onClick = { nav.ponto(r.id) },
                            onFavorito = { escopo.launch { repo.favoritar(r.id, !r.ponto.favorito) } },
                            disciplina = nomes[r.ponto.disciplinaId],
                        )
                        Text(
                            "Encontrado em: ${onde.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritosTela(nav: Nav) {
    val repo = LocalRepo.current
    val escopo = rememberCoroutineScope()
    val disciplinas by repo.disciplinas.collectAsStateWithLifecycle(emptyList())
    val pontos by repo.pontos.collectAsStateWithLifecycle(emptyList())
    val ordem = disciplinas.associate { it.id to it.ordem }
    val nomes = disciplinas.associate { it.id to it.nome }
    val favs = pontos.filter { it.ponto.favorito }
        .sortedWith(compareBy<PontoResumo> { ordem[it.ponto.disciplinaId] ?: 0 }.then(Analise.ordemImportancia))

    TelaBase(titulo = "Favoritos", subtitulo = "Pontos que sempre esqueço") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (favs.isEmpty()) item {
                Vazio("Toque no coração de um ponto para guardá-lo aqui.")
            }
            items(favs, key = { it.id }) { r ->
                CartaoPonto(
                    r, onClick = { nav.ponto(r.id) },
                    onFavorito = { escopo.launch { repo.favoritar(r.id, !r.ponto.favorito) } },
                    disciplina = nomes[r.ponto.disciplinaId],
                )
            }
        }
    }
}
