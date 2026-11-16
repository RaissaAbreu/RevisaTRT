package app.revisatrt.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.revisatrt.ui.screens.AjustesTela
import app.revisatrt.ui.screens.BuscaTela
import app.revisatrt.ui.screens.DisciplinaTela
import app.revisatrt.ui.screens.EditarPontoTela
import app.revisatrt.ui.screens.EstatisticasTela
import app.revisatrt.ui.screens.FavoritosTela
import app.revisatrt.ui.screens.InicioTela
import app.revisatrt.ui.screens.PontoTela
import app.revisatrt.ui.screens.RevisarTela

private data class Aba(val rota: String, val rotulo: String, val icone: ImageVector)

private val abas = listOf(
    Aba("inicio", "Início", Icons.Filled.Home),
    Aba("revisar", "Revisar", Icons.Filled.Refresh),
    Aba("busca", "Busca", Icons.Filled.Search),
    Aba("favoritos", "Favoritos", Icons.Filled.Favorite),
    Aba("ajustes", "Ajustes", Icons.Filled.Settings),
)

/** Ações de navegação usadas pelas telas. */
class Nav(private val nav: NavHostController) {
    fun voltar() { nav.popBackStack() }
    fun disciplina(id: Long) = nav.navigate("disciplina/$id")
    fun ponto(id: Long) = nav.navigate("ponto/$id")
    fun editar(id: Long, disciplinaId: Long = 0) = nav.navigate("editar/$id?disc=$disciplinaId")
    fun estatisticas() = nav.navigate("estatisticas")
    fun aba(rota: String) = nav.navigate(rota) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavegacao() {
    val controller = rememberNavController()
    val nav = Nav(controller)
    val entrada by controller.currentBackStackEntryAsState()
    val rotaAtual = entrada?.destination?.route
    Scaffold(
        bottomBar = {
            if (abas.any { it.rota == rotaAtual }) {
                NavigationBar {
                    abas.forEach { a ->
                        NavigationBarItem(
                            selected = rotaAtual == a.rota,
                            onClick = { nav.aba(a.rota) },
                            icon = { Icon(a.icone, contentDescription = null) },
                            label = { Text(a.rotulo) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(controller, startDestination = "inicio", modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable("inicio") { InicioTela(nav) }
            composable("revisar") { RevisarTela(nav) }
            composable("busca") { BuscaTela(nav) }
            composable("favoritos") { FavoritosTela(nav) }
            composable("ajustes") { AjustesTela() }
            composable("estatisticas") { EstatisticasTela(nav) }
            composable("disciplina/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                DisciplinaTela(it.arguments!!.getLong("id"), nav)
            }
            composable("ponto/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                PontoTela(it.arguments!!.getLong("id"), nav)
            }
            composable(
                "editar/{id}?disc={disc}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("disc") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) {
                EditarPontoTela(it.arguments!!.getLong("id"), it.arguments!!.getLong("disc"), nav)
            }
        }
    }
}
