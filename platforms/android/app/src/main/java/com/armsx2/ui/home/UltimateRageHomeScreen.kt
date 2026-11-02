package com.armsx2.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RageBackground = Color(0xFF171A2C)
private val RageCard = Color(0xFF191D34)
private val RageMuted = Color(0xFFB9B9CC)
private val RageOrange = Color(0xFFFF922E)
private val RageYellow = Color(0xFFFFB52E)
private val RageOutline = Color(0xFF77758C)

enum class UltimateRageTab(val label: String, val symbol: String) {
    HOME("Início", "⌂"),
    TEXTURES("Texturas", "▧"),
    MODS("Mods", "✣"),
    CONTROLS("Controles", "⌘"),
    CHEATS("Cheats", "▣"),
    DRIVERS("Drivers", "▦"),
    DEBUG("Depuração", "⚙"),
    SETTINGS("Ajustes", "☷")
}

/**
 * Interface principal do Ultimate Rage.
 *
 * Este componente contém somente a camada visual. A integração com o runtime do emulador
 * deve ser feita pelo app chamador através de onPlay e onTabSelected.
 */
@Composable
fun UltimateRageHomeScreen(
    gameDetected: Boolean = false,
    gameVersion: String = "Ultimate Spider-Man · PS2",
    gameFileSummary: String = "Aguardando verificação dos arquivos",
    engineStatus: String = "Motor Android ARM64 incluído",
    onPlay: () -> Unit = {},
    onTabSelected: (UltimateRageTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = RageBackground
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF30221F), Color(0xFF211E2B), RageBackground)
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    Spacer(Modifier.height(34.dp))
                    Header()
                    Spacer(Modifier.height(34.dp))

                    OnlineStyleStatusCard(
                        title = if (gameDetected) "Jogo identificado" else "Estado do jogo",
                        message = if (gameDetected)
                            "Os arquivos do jogo foram identificados e estão prontos para iniciar."
                        else
                            "O Ultimate Spider-Man ainda não foi verificado. A área de jogo será atualizada quando o app confirmar os arquivos.",
                        detail = gameFileSummary
                    )

                    Spacer(Modifier.height(20.dp))
                    PlayButton(onClick = onPlay)
                    Spacer(Modifier.height(18.dp))

                    ShortcutButton(
                        text = "Criar atalho do jogo na tela inicial",
                        onClick = { /* integração de atalho feita pelo app chamador */ }
                    )
                    Text(
                        text = "Abra o Ultimate Spider-Man diretamente pela tela inicial, sem navegar pelos menus do aplicativo.",
                        color = RageMuted,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(top = 20.dp, bottom = 28.dp)
                    )

                    InfoCard(
                        title = "Dados do jogo",
                        icon = "◉",
                        accent = RageOrange
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (gameDetected) "✓" else "●",
                                color = if (gameDetected) Color(0xFF36D995) else RageOrange,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "$gameVersion  ·  $gameFileSummary",
                                color = Color(0xFFE1E1EC),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    InfoCard(
                        title = "Motor do jogo",
                        icon = "▣",
                        accent = Color(0xFF8BA8FF)
                    ) {
                        Text(
                            engineStatus,
                            color = Color(0xFFE1E1EC),
                            fontSize = 15.sp
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SocialPlaceholder("YouTube", "▶", Color(0xFFFF4D43))
                        SocialPlaceholder("Comunidade", "▤", Color(0xFF718BFF))
                        SocialPlaceholder("Apoiar", "☕", Color(0xFF31B9E8))
                        SocialPlaceholder("GitHub", "</>", Color(0xFFB9B9CC))
                    }

                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("1", "Jogo", Color(0xFFFFC94A), Modifier.weight(1f))
                        StatTile("ARM64", "Arquitetura", RageOrange, Modifier.weight(1f))
                        StatTile("PS2", "Plataforma", Color(0xFF63C8F4), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(28.dp))
                }

                BottomNavigation(
                    selectedIndex = selectedTab,
                    onSelect = {
                        selectedTab = it
                        onTabSelected(UltimateRageTab.values()[it])
                    }
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFFFD36A), Color(0xFFFFA12C), Color(0xFFE66B12))
                    )
                )
                .border(2.dp, Color(0xFFFFC56A), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("UR", color = Color(0xFF30170D), fontSize = 36.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(18.dp))
        Column {
            Text(
                "ULTIMATE",
                color = Color(0xFFFFD36D),
                fontSize = 34.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "RAGE",
                color = RageOrange,
                fontSize = 42.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "Ultimate Spider-Man · Android",
                color = Color(0xFFD0CDDA),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OnlineStyleStatusCard(title: String, message: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0x332F2B39))
            .border(1.5.dp, RageOutline, RoundedCornerShape(28.dp))
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(11.dp).clip(CircleShape).background(RageOrange))
            Spacer(Modifier.width(10.dp))
            Text(
                "SITUAÇÃO DO EMULADOR",
                color = Color(0xFFB8B5C9),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(title, color = Color(0xFFF4F1FA), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(message, color = Color(0xFFE0DDE8), fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(12.dp))
        Text(detail, color = RageMuted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun PlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.horizontalGradient(listOf(Color(0xFFEF6A0A), Color(0xFFFFA52C)))
            )
            .border(3.dp, Color(0xFFFFC06B), RoundedCornerShape(30.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(54.dp).clip(CircleShape)
                    .background(Color(0x553D1A08)),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color.White, fontSize = 25.sp)
            }
            Spacer(Modifier.width(20.dp))
            Text("JOGAR", color = Color(0xFF261406), fontSize = 32.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ShortcutButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(1.5.dp, RageOutline, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("↗  $text", color = RageOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoCard(
    title: String,
    icon: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(RageCard)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = Color(0xFFE5E4EF), fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        content()
    }
}

@Composable
private fun SocialPlaceholder(label: String, symbol: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(54.dp).clip(CircleShape)
                .background(tint.copy(alpha = 0.12f))
                .border(1.dp, tint.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = tint, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = RageMuted, fontSize = 11.sp)
    }
}

@Composable
private fun StatTile(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.13f))
            .padding(13.dp)
    ) {
        Text(value, color = accent, fontSize = 19.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text(label, color = Color(0xFFD0CDDA), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun BottomNavigation(selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFF171A2E))
            .navigationBarsPadding()
            .padding(horizontal = 3.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        UltimateRageTab.values().forEachIndexed { index, tab ->
            Column(
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelect(index) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    tab.symbol,
                    color = if (selectedIndex == index) RageOrange else Color(0xFFC7C6D8),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    tab.label,
                    color = if (selectedIndex == index) RageOrange else Color(0xFFC7C6D8),
                    fontSize = 9.sp,
                    fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}
