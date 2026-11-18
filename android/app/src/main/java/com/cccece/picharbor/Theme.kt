package com.cccece.picharbor

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

@Composable
fun PicHarborTheme(settings:HarborSettings,content:@Composable ()->Unit) {
    val dark=when(settings.theme){"dark"->true;"light"->false;else->isSystemInDarkTheme()}
    val scheme=if(settings.palette=="system"&&Build.VERSION.SDK_INT>=31) {
        if(dark)dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else {
        val colors=when(settings.palette){
            "sage"->arrayOf(Color(0xFF386652),Color(0xFFC6ECD7),Color(0xFF143C2A),Color(0xFFA2D2B8),Color(0xFF315440))
            "peach"->arrayOf(Color(0xFF8C503B),Color(0xFFFFDBCB),Color(0xFF4D2619),Color(0xFFF0B89C),Color(0xFF6A3E2C))
            "blue"->arrayOf(Color(0xFF365E9D),Color(0xFFD6E3FF),Color(0xFF001B3E),Color(0xFFA8C7FA),Color(0xFF294677))
            "rose"->arrayOf(Color(0xFF984061),Color(0xFFFFD9E2),Color(0xFF3E001D),Color(0xFFFFB1CA),Color(0xFF75314A))
            "teal"->arrayOf(Color(0xFF006B5E),Color(0xFF9EF2DF),Color(0xFF00201B),Color(0xFF82D5C3),Color(0xFF005046))
            "amber"->arrayOf(Color(0xFF795900),Color(0xFFFFDEA6),Color(0xFF261900),Color(0xFFF7BD48),Color(0xFF5C4200))
            else->arrayOf(Color(0xFF67518E),Color(0xFFEADDFF),Color(0xFF281341),Color(0xFFD1BCF3),Color(0xFF4F3C68))
        }
        val base=if(dark)darkColorScheme(primary=colors[3],onPrimary=colors[2],primaryContainer=colors[4],onPrimaryContainer=colors[1],
            background=Color(0xFF151218),surface=Color(0xFF151218),surfaceContainer=Color(0xFF211C25),surfaceContainerLow=Color(0xFF1F1B23),surfaceContainerHigh=Color(0xFF2B2630))
        else lightColorScheme(primary=colors[0],onPrimary=Color.White,primaryContainer=colors[1],onPrimaryContainer=colors[2],
            background=Color(0xFFFDF8FF),surface=Color(0xFFFDF8FF),surfaceContainer=Color(0xFFF1EAF5),surfaceContainerLow=Color(0xFFF7F1FA),surfaceContainerHigh=Color(0xFFEEE7F2))
        base.copy(secondary=base.primary,onSecondary=base.onPrimary,secondaryContainer=base.primaryContainer,onSecondaryContainer=base.onPrimaryContainer,
            tertiary=base.primary,onTertiary=base.onPrimary,tertiaryContainer=base.primaryContainer,onTertiaryContainer=base.onPrimaryContainer,
            surfaceTint=base.primary,background=lerp(if(dark)Color(0xFF131315) else Color(0xFFFCFCFF),colors[0],.025f),
            surface=lerp(if(dark)Color(0xFF131315) else Color(0xFFFCFCFF),colors[0],.025f),
            surfaceContainerLow=lerp(if(dark)Color(0xFF1C1C1F) else Color(0xFFF6F6FA),colors[0],.035f),
            surfaceContainer=lerp(if(dark)Color(0xFF212125) else Color(0xFFF0F0F5),colors[0],.045f),
            surfaceContainerHigh=lerp(if(dark)Color(0xFF29292D) else Color(0xFFEAEAF0),colors[0],.055f))
    }
    val view=LocalView.current
    val activity=findActivity(view.context)
    SideEffect {
        activity?.window?.let { window ->
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(scheme.background.toArgb()))
            androidx.core.view.WindowCompat.getInsetsController(window,view).apply {
                isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark
            }
        }
    }
    MaterialTheme(colorScheme=scheme,shapes=Shapes(extraSmall=RoundedCornerShape(8.dp),small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(26.dp),extraLarge=RoundedCornerShape(30.dp))) {
        // MaterialTheme defines the palette, but does not set LocalContentColor.
        // Pages draw their own background after the navigation refactor, so give
        // unstyled text/icons the matching foreground without changing layout.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground,content=content)
    }
}

private fun findActivity(context:android.content.Context):android.app.Activity? {
    var current=context
    while(current is android.content.ContextWrapper) {
        if(current is android.app.Activity)return current
        val base=current.baseContext
        if(base===current)return null
        current=base
    }
    return current as? android.app.Activity
}
