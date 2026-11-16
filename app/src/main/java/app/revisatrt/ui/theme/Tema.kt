package app.revisatrt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.revisatrt.data.CorTema
import app.revisatrt.data.Tema

/** Tons pastel do app. */
object Pastel {
    val Rosa = Color(0xFFF8C8DC)
    val Azul = Color(0xFFBFDFF5)
    val Verde = Color(0xFFA8E6D5)
    val Roxo = Color(0xFFD9C6F5)
    val Pessego = Color(0xFFFFD8B8)
}

private data class Tons(
    val primaria: Color, val container: Color, val noContainer: Color,
    val pastel: Color, val escuroContainer: Color, val fundoClaro: Color,
)

private fun tons(c: CorTema) = when (c) {
    CorTema.ROSA -> Tons(Color(0xFFA8507A), Color(0xFFF9D5E5), Color(0xFF4A1430), Color(0xFFF4B6CF), Color(0xFF5E2A44), Color(0xFFFFF8FB))
    CorTema.AZUL -> Tons(Color(0xFF3F7CAC), Color(0xFFD4E8F7), Color(0xFF0E2F4A), Color(0xFFA9D2F2), Color(0xFF23435E), Color(0xFFF7FBFE))
    CorTema.VERDE -> Tons(Color(0xFF2E8B7A), Color(0xFFCFF3E8), Color(0xFF0B3B32), Color(0xFF9FE2D3), Color(0xFF1E4B42), Color(0xFFF6FDFA))
    CorTema.ROXO -> Tons(Color(0xFF7E5BB5), Color(0xFFE6DAF8), Color(0xFF2D1752), Color(0xFFCDB4F0), Color(0xFF45336A), Color(0xFFFBF8FF))
}

/** Cores de apoio (independentes do Material). */
@Immutable
data class CoresExtras(
    val atencao: Color,
    val atencaoFundo: Color,
    val noAtencao: Color,
    val disciplinas: List<Color>,
    val noDisciplina: Color,
    val importancia: List<Color>, // índice 0..4
    val noImportancia: Color,
    val sucesso: Color,
    val facil: Color, val medio: Color, val dificil: Color,
)

val LocalCores = staticCompositionLocalOf {
    CoresExtras(
        Color.Red, Color.Red, Color.White, listOf(Color.Gray), Color.Black, List(5) { Color.Gray }, Color.Black,
        Color.Green, Color.Green, Color.Blue, Color.Red,
    )
}

private fun extras(escuro: Boolean) = if (!escuro) CoresExtras(
    atencao = Color(0xFFD9822B),
    atencaoFundo = Color(0xFFFFEBD9),
    noAtencao = Color(0xFF5A3200),
    disciplinas = listOf(Pastel.Rosa, Pastel.Azul, Pastel.Verde, Pastel.Roxo),
    noDisciplina = Color(0xFF2B2530),
    importancia = listOf(Color(0xFFE4E1E6), Pastel.Verde, Pastel.Azul, Pastel.Roxo, Pastel.Rosa),
    noImportancia = Color(0xFF2B2530),
    sucesso = Color(0xFF2E8B7A),
    facil = Color(0xFF8FD9C2), medio = Color(0xFFA9D2F2), dificil = Color(0xFFF7B48A),
) else CoresExtras(
    atencao = Color(0xFFFFB77C),
    atencaoFundo = Color(0xFF4A2F18),
    noAtencao = Color(0xFFFFDCC2),
    disciplinas = listOf(Color(0xFF5E2A44), Color(0xFF23435E), Color(0xFF1E4B42), Color(0xFF45336A)),
    noDisciplina = Color(0xFFF3ECF4),
    importancia = listOf(Color(0xFF3A363D), Color(0xFF1E4B42), Color(0xFF23435E), Color(0xFF45336A), Color(0xFF5E2A44)),
    noImportancia = Color(0xFFF3ECF4),
    sucesso = Color(0xFF9FE2D3),
    facil = Color(0xFF6FC7AE), medio = Color(0xFF86B9E0), dificil = Color(0xFFF0A070),
)

private fun esquema(cor: CorTema, escuro: Boolean): ColorScheme {
    val t = tons(cor)
    val outras = CorTema.entries.filter { it != cor }.map { tons(it) }
    return if (!escuro) lightColorScheme(
        primary = t.primaria,
        onPrimary = Color.White,
        primaryContainer = t.container,
        onPrimaryContainer = t.noContainer,
        secondary = outras[0].primaria,
        secondaryContainer = outras[0].container,
        onSecondaryContainer = outras[0].noContainer,
        tertiary = outras[1].primaria,
        tertiaryContainer = outras[1].container,
        onTertiaryContainer = outras[1].noContainer,
        background = t.fundoClaro,
        onBackground = Color(0xFF221D24),
        surface = t.fundoClaro,
        onSurface = Color(0xFF221D24),
        surfaceVariant = t.container.copy(alpha = 0.55f).compositeOver(Color.White),
        onSurfaceVariant = Color(0xFF524A57),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = t.container.copy(alpha = 0.25f).compositeOver(Color.White),
        surfaceContainerHigh = t.container.copy(alpha = 0.40f).compositeOver(Color.White),
        surfaceContainerHighest = t.container.copy(alpha = 0.55f).compositeOver(Color.White),
        outline = Color(0xFF8E8593),
        outlineVariant = Color(0xFFE2D9E5),
    ) else darkColorScheme(
        primary = t.pastel,
        onPrimary = t.noContainer,
        primaryContainer = t.escuroContainer,
        onPrimaryContainer = t.container,
        secondary = outras[0].pastel,
        secondaryContainer = outras[0].escuroContainer,
        onSecondaryContainer = outras[0].container,
        tertiary = outras[1].pastel,
        tertiaryContainer = outras[1].escuroContainer,
        onTertiaryContainer = outras[1].container,
        background = Color(0xFF17151A),
        onBackground = Color(0xFFEDE6EF),
        surface = Color(0xFF17151A),
        onSurface = Color(0xFFEDE6EF),
        surfaceVariant = Color(0xFF2E2A33),
        onSurfaceVariant = Color(0xFFCFC5D3),
        surfaceContainerLowest = Color(0xFF121014),
        surfaceContainerLow = Color(0xFF1E1B21),
        surfaceContainer = Color(0xFF221F26),
        surfaceContainerHigh = Color(0xFF2B2730),
        surfaceContainerHighest = Color(0xFF36313B),
        outline = Color(0xFF978E9C),
        outlineVariant = Color(0xFF3F3944),
    )
}

private fun Color.compositeOver(fundo: Color): Color {
    val a = alpha
    return Color(
        red = red * a + fundo.red * (1 - a),
        green = green * a + fundo.green * (1 - a),
        blue = blue * a + fundo.blue * (1 - a),
        alpha = 1f,
    )
}

@Composable
fun RevisaTheme(tema: Tema, cor: CorTema, content: @Composable () -> Unit) {
    val escuro = when (tema) {
        Tema.SISTEMA -> isSystemInDarkTheme()
        Tema.CLARO -> false
        Tema.ESCURO -> true
    }
    CompositionLocalProvider(LocalCores provides extras(escuro)) {
        MaterialTheme(colorScheme = esquema(cor, escuro), content = content)
    }
}

fun corDaDisciplina(cores: CoresExtras, ordem: Int) = cores.disciplinas[(ordem - 1).mod(cores.disciplinas.size)]
