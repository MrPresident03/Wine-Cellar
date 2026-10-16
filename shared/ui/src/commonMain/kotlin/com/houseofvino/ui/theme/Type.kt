package com.houseofvino.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.houseofvino.ui.generated.resources.Res
import com.houseofvino.ui.generated.resources.caslon_bold
import com.houseofvino.ui.generated.resources.caslon_italic
import com.houseofvino.ui.generated.resources.caslon_regular
import com.houseofvino.ui.generated.resources.hovsans_medium
import com.houseofvino.ui.generated.resources.hovsans_regular
import com.houseofvino.ui.generated.resources.hovsans_semibold
import org.jetbrains.compose.resources.Font

/**
 * Type scale from docs/design-system.md: Libre Caslon for wine, Source Sans 3 for controls (bundled as
 * "HoV Sans", a renamed Latin subset, as the OFL's Reserved Font Name clause requires).
 */
@Immutable
data class HovType(
    val display: TextStyle,
    val title: TextStyle,
    val vintage: TextStyle,
    val vintageSmall: TextStyle,
    val heading: TextStyle,
    val cuvee: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val labelStrong: TextStyle,
    val detail: TextStyle,
    val figure: TextStyle,
    val figureLarge: TextStyle,
    val caption: TextStyle,
)

val LocalHovType = staticCompositionLocalOf<HovType> { error("HouseOfVinoTheme not applied") }

@Composable
fun caslon(): FontFamily = FontFamily(
    Font(Res.font.caslon_regular, FontWeight.Normal, FontStyle.Normal),
    Font(Res.font.caslon_italic, FontWeight.Normal, FontStyle.Italic),
    Font(Res.font.caslon_bold, FontWeight.Bold, FontStyle.Normal),
)

@Composable
fun sansFamily(): FontFamily = FontFamily(
    Font(Res.font.hovsans_regular, FontWeight.Normal),
    Font(Res.font.hovsans_medium, FontWeight.Medium),
    Font(Res.font.hovsans_semibold, FontWeight.SemiBold),
)

@Composable
fun hovType(): HovType {
    val serif = caslon()
    val sans = sansFamily()
    return HovType(
        display = TextStyle(fontFamily = serif, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal),
        title = TextStyle(fontFamily = serif, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
        vintage = TextStyle(fontFamily = serif, fontSize = 28.sp, lineHeight = 30.sp, fontFeatureSettings = "onum"),
        vintageSmall = TextStyle(fontFamily = serif, fontSize = 20.sp, lineHeight = 24.sp, fontFeatureSettings = "onum"),
        heading = TextStyle(fontFamily = serif, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
        cuvee = TextStyle(fontFamily = serif, fontSize = 16.sp, lineHeight = 21.sp, fontStyle = FontStyle.Italic),
        body = TextStyle(fontFamily = sans, fontSize = 16.sp, lineHeight = 24.sp),
        label = TextStyle(fontFamily = sans, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelStrong = TextStyle(fontFamily = sans, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        detail = TextStyle(fontFamily = sans, fontSize = 15.sp, lineHeight = 20.sp),
        figure = TextStyle(fontFamily = sans, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
        figureLarge = TextStyle(fontFamily = sans, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
        caption = TextStyle(fontFamily = sans, fontSize = 13.sp, lineHeight = 18.sp),
    )
}
