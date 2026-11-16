package app.revisatrt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revisatrt.data.Analise
import app.revisatrt.data.Ponto
import app.revisatrt.ui.LocalRepo
import app.revisatrt.ui.Nav
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditarPontoTela(id: Long, disciplinaInicial: Long, nav: Nav) {
    val repo = LocalRepo.current
    val escopo = rememberCoroutineScope()
    val disciplinas by repo.disciplinas.collectAsStateWithLifecycle(emptyList())

    var original by rememberSaveable { mutableStateOf<Long?>(null) } // marca que já carregou
    var disciplinaId by rememberSaveable { mutableStateOf(disciplinaInicial) }
    var titulo by rememberSaveable { mutableStateOf("") }
    var importancia by rememberSaveable { mutableStateOf(2) }
    var vigencia by rememberSaveable { mutableStateOf("Vigente") }
    var oQueCai by rememberSaveable { mutableStateOf("") }
    var atencao by rememberSaveable { mutableStateOf("") }
    var textoLei by rememberSaveable { mutableStateOf("") }
    var ondeCaiu by rememberSaveable { mutableStateOf("") }
    var base by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        if (id != 0L && original == null) {
            val p = repo.dao.ponto(id).first() ?: return@LaunchedEffect
            disciplinaId = p.disciplinaId; titulo = p.titulo; importancia = p.importancia; vigencia = p.vigencia
            oQueCai = p.oQueCai; atencao = p.atencao; textoLei = p.textoLei; ondeCaiu = p.ondeCaiu
            base = p.chave
            original = id
        }
    }
    LaunchedEffect(disciplinas) {
        if (disciplinaId == 0L && disciplinas.isNotEmpty()) disciplinaId = disciplinas.first().id
    }

    TelaBase(titulo = if (id == 0L) "Novo ponto" else "Editar ponto", voltar = nav::voltar) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            var aberto by rememberSaveable { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = aberto, onExpandedChange = { aberto = it }) {
                OutlinedTextField(
                    value = disciplinas.find { it.id == disciplinaId }?.nome ?: "",
                    onValueChange = {}, readOnly = true, label = { Text("Disciplina") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(aberto) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
                    disciplinas.forEach { d ->
                        DropdownMenuItem(text = { Text(d.nome) }, onClick = { disciplinaId = d.id; aberto = false })
                    }
                }
            }
            OutlinedTextField(
                titulo, { titulo = it }, Modifier.fillMaxWidth(),
                label = { Text("Dispositivo / título") },
                placeholder = { Text("Ex.: Súmula 331 do TST (terceirização)") },
            )
            Text("Importância", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (4 downTo 0).forEach { i ->
                    FilterChip(selected = importancia == i, onClick = { importancia = i }, label = { Text(Analise.importanciaNome(i)) })
                }
            }
            OutlinedTextField(vigencia, { vigencia = it }, Modifier.fillMaxWidth(), label = { Text("Vigência / status") })
            OutlinedTextField(oQueCai, { oQueCai = it }, Modifier.fillMaxWidth(), label = { Text("O que a FCC cobra") }, minLines = 3)
            OutlinedTextField(atencao, { atencao = it }, Modifier.fillMaxWidth(), label = { Text("Atenção: pegadinhas") }, minLines = 2)
            OutlinedTextField(textoLei, { textoLei = it }, Modifier.fillMaxWidth(), label = { Text("Texto da lei (completo)") }, minLines = 5)
            OutlinedTextField(ondeCaiu, { ondeCaiu = it }, Modifier.fillMaxWidth(), label = { Text("Onde caiu") }, minLines = 2)
            if (base != null) Text(
                "Este ponto veio da sua planilha. Depois de editado, futuras atualizações da base não vão sobrescrever o que você mudou.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = titulo.isNotBlank() && disciplinaId != 0L,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                onClick = {
                    escopo.launch {
                        val atual = if (id == 0L) null else repo.dao.ponto(id).first()
                        val p = (atual ?: Ponto(disciplinaId = disciplinaId, titulo = "", ordem = 9999)).copy(
                            disciplinaId = disciplinaId, titulo = titulo.trim(), importancia = importancia,
                            vigencia = vigencia.trim(), oQueCai = oQueCai.trim(), atencao = atencao.trim(),
                            textoLei = textoLei.trim(), ondeCaiu = ondeCaiu.trim(),
                        ).let { if (atual != null && it.textoLei != atual.textoLei) it.copy(textoEditado = true) else it }
                        repo.salvarPonto(p)
                        nav.voltar()
                    }
                },
            ) { Text("Salvar") }
        }
    }
}
