package app.revisatrt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revisatrt.ui.LocalRepo
import app.revisatrt.ui.Nav
import java.text.Normalizer

/** Divide o texto da lei em parágrafos (linhas em branco ou quebras simples). */
fun paragrafosLei(texto: String): List<String> =
    texto.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

/** Minúscula e sem acento, caractere a caractere (mantém os índices do texto original). */
fun dobrar(s: String): String = buildString(s.length) {
    for (c in s) append(Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull()?.lowercaseChar() ?: c)
}

private enum class TipoParagrafo { NORMA, ARTIGO, OBSERVACAO, ESTRUTURA, TEXTO }

private fun tipo(p: String) = when {
    p.startsWith("## ") -> TipoParagrafo.NORMA
    p.startsWith("### ") -> TipoParagrafo.ARTIGO
    p.startsWith("Observação:") -> TipoParagrafo.OBSERVACAO
    p.length < 60 && p == p.uppercase() && p.any { it.isLetter() } -> TipoParagrafo.ESTRUTURA
    else -> TipoParagrafo.TEXTO
}

private fun semMarcador(p: String) = p.removePrefix("### ").removePrefix("## ")

/** Destaca as ocorrências de [termo] (já dobrado) em [texto]. */
private fun destacar(texto: String, termo: String, fundo: Color, inicioNegrito: Int = 0): AnnotatedString = buildAnnotatedString {
    append(texto)
    if (inicioNegrito > 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), 0, inicioNegrito)
    if (termo.isNotEmpty()) {
        val d = dobrar(texto)
        var i = d.indexOf(termo)
        while (i >= 0) {
            addStyle(SpanStyle(background = fundo), i, i + termo.length)
            i = d.indexOf(termo, i + termo.length)
        }
    }
}

/** Tamanho do rótulo de dispositivo no início do parágrafo ("Art. 5º", "§ 1º", "I –"), para deixar em negrito. */
private val rotulo = Regex("^(Art\\.\\s*[\\d.]*\\d(?:-[A-Z])?[º°]?\\.?|§\\s*\\d+º?|Parágrafo único\\.?|[IVXLC]+\\s*[–-])")

@Composable
fun ParagrafoLei(p: String, termo: String = "", modifier: Modifier = Modifier) {
    val destaque = MaterialTheme.colorScheme.tertiaryContainer
    when (tipo(p)) {
        TipoParagrafo.NORMA -> Text(
            destacar(semMarcador(p), termo, destaque), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(top = 8.dp),
        )
        TipoParagrafo.ARTIGO -> Text(
            destacar(semMarcador(p), termo, destaque), style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, modifier = modifier.padding(top = 4.dp),
        )
        TipoParagrafo.OBSERVACAO -> Text(
            destacar(p, termo, destaque), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier,
        )
        TipoParagrafo.ESTRUTURA -> Text(
            destacar(p, termo, destaque), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary, modifier = modifier.padding(top = 4.dp),
        )
        TipoParagrafo.TEXTO -> Text(
            destacar(p, termo, destaque, rotulo.find(p)?.value?.length ?: 0),
            style = MaterialTheme.typography.bodyMedium, modifier = modifier,
        )
    }
}

@Composable
fun TextoLeiTela(id: Long, nav: Nav) {
    val repo = LocalRepo.current
    val ponto by repo.dao.ponto(id).collectAsStateWithLifecycle(null)
    val p = ponto ?: return
    val paragrafos = remember(p.textoLei) { paragrafosLei(p.textoLei) }
    val dobrados = remember(paragrafos) { paragrafos.map { dobrar(semMarcador(it)) } }
    var busca by rememberSaveable { mutableStateOf("") }
    val termo = dobrar(busca.trim())
    val achados = remember(termo, dobrados) {
        if (termo.length < 2) emptyList() else dobrados.indices.filter { termo in dobrados[it] }
    }
    var atual by remember { mutableIntStateOf(0) }
    val lista = rememberLazyListState()
    LaunchedEffect(achados, atual) {
        if (achados.isNotEmpty()) lista.animateScrollToItem(achados[atual.coerceIn(0, achados.lastIndex)])
    }

    TelaBase(titulo = "Texto da lei", subtitulo = p.titulo, voltar = nav::voltar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = busca, onValueChange = { busca = it; atual = 0 },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Buscar no texto (ex.: art. 33)") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (busca.isNotEmpty()) IconButton(onClick = { busca = "" }) { Icon(Icons.Filled.Clear, "Limpar") } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = MaterialTheme.shapes.extraLarge,
                )
                if (termo.length >= 2) {
                    Text(
                        if (achados.isEmpty()) "0" else "${atual + 1}/${achados.size}",
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp),
                    )
                    IconButton(enabled = achados.isNotEmpty(), onClick = { atual = (atual - 1 + achados.size) % achados.size }) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Anterior")
                    }
                    IconButton(enabled = achados.isNotEmpty(), onClick = { atual = (atual + 1) % achados.size }) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Próximo")
                    }
                }
            }
            SelectionContainer {
                LazyColumn(
                    state = lista,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(paragrafos) { _, par -> ParagrafoLei(par, if (termo.length >= 2) termo else "") }
                }
            }
        }
    }
}
