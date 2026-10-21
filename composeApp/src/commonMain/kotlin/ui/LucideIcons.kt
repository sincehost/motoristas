package ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Ícones de contorno do conjunto Lucide (lucide-static 0.460.0, licença ISC —
 * https://lucide.dev), convertidos dos SVGs originais pra ImageVector.
 * Usados nas telas principais (tela inicial, barra inferior, menu de
 * despesas) — o resto do app continua com Material Icons.
 *
 * Uso igual a qualquer ícone: Icon(Lucide.Truck, null, tint = ...). O traço
 * é desenhado em preto e o `tint` do Icon pinta por cima.
 */
object Lucide {
    val ArrowRight: ImageVector by lazy {
        lucide(
        "ArrowRight",
        "M5 12h14",
        "m12 5 7 7-7 7"
        )
    }

    val Calendar: ImageVector by lazy {
        lucide(
        "Calendar",
        "M8 2v4",
        "M16 2v4",
        "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z",
        "M3 10h18"
        )
    }

    val ChartColumn: ImageVector by lazy {
        lucide(
        "ChartColumn",
        "M3 3v16a2 2 0 0 0 2 2h16",
        "M18 17V9",
        "M13 17V5",
        "M8 17v-3"
        )
    }

    val ChevronRight: ImageVector by lazy {
        lucide(
        "ChevronRight",
        "m9 18 6-6-6-6"
        )
    }

    val ClipboardCheck: ImageVector by lazy {
        lucide(
        "ClipboardCheck",
        "M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1Z",
        "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2",
        "m9 14 2 2 4-4"
        )
    }

    val ClipboardList: ImageVector by lazy {
        lucide(
        "ClipboardList",
        "M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1Z",
        "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2",
        "M12 11h4",
        "M12 16h4",
        "M8 11h.01",
        "M8 16h.01"
        )
    }

    val Droplet: ImageVector by lazy {
        lucide(
        "Droplet",
        "M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z"
        )
    }

    val Ellipsis: ImageVector by lazy {
        lucide(
        "Ellipsis",
        "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
        "M18 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
        "M4 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0"
        )
    }

    val Flag: ImageVector by lazy {
        lucide(
        "Flag",
        "M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z",
        "M4 22L4 15"
        )
    }

    val Fuel: ImageVector by lazy {
        lucide(
        "Fuel",
        "M3 22L15 22",
        "M4 9L14 9",
        "M14 22V4a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v18",
        "M14 13h2a2 2 0 0 1 2 2v2a2 2 0 0 0 2 2a2 2 0 0 0 2-2V9.83a2 2 0 0 0-.59-1.42L18 5"
        )
    }

    val Gauge: ImageVector by lazy {
        lucide(
        "Gauge",
        "m12 14 4-4",
        "M3.34 19a10 10 0 1 1 17.32 0"
        )
    }

    val Hammer: ImageVector by lazy {
        lucide(
        "Hammer",
        "m15 12-8.373 8.373a1 1 0 1 1-3-3L12 9",
        "m18 15 4-4",
        "m21.5 11.5-1.914-1.914A2 2 0 0 1 19 8.172V7l-2.26-2.26a6 6 0 0 0-4.202-1.756L9 2.96l.92.82A6.18 6.18 0 0 1 12 8.4V10l2 2h1.172a2 2 0 0 1 1.414.586L18.5 14.5"
        )
    }

    val House: ImageVector by lazy {
        lucide(
        "House",
        "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
        )
    }

    val Info: ImageVector by lazy {
        lucide(
        "Info",
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
        "M12 16v-4",
        "M12 8h.01"
        )
    }

    val List: ImageVector by lazy {
        lucide(
        "List",
        "M3 12h.01",
        "M3 18h.01",
        "M3 6h.01",
        "M8 12h13",
        "M8 18h13",
        "M8 6h13"
        )
    }

    val LogOut: ImageVector by lazy {
        lucide(
        "LogOut",
        "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4",
        "M16 17 L21 12 L16 7",
        "M21 12L9 12"
        )
    }

    val Moon: ImageVector by lazy {
        lucide(
        "Moon",
        "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"
        )
    }

    val Package: ImageVector by lazy {
        lucide(
        "Package",
        "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z",
        "M12 22V12",
        "m3.3 7 7.703 4.734a2 2 0 0 0 1.994 0L20.7 7",
        "m7.5 4.27 9 5.15"
        )
    }

    val Receipt: ImageVector by lazy {
        lucide(
        "Receipt",
        "M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z",
        "M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8",
        "M12 17.5v-11"
        )
    }

    val RefreshCwOff: ImageVector by lazy {
        lucide(
        "RefreshCwOff",
        "M21 8L18.74 5.74A9.75 9.75 0 0 0 12 3C11 3 10.03 3.16 9.13 3.47",
        "M8 16H3v5",
        "M3 12C3 9.51 4 7.26 5.64 5.64",
        "m3 16 2.26 2.26A9.75 9.75 0 0 0 12 21c2.49 0 4.74-1 6.36-2.64",
        "M21 12c0 1-.16 1.97-.47 2.87",
        "M21 3v5h-5",
        "M22 22 2 2"
        )
    }

    val RefreshCw: ImageVector by lazy {
        lucide(
        "RefreshCw",
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
        "M8 16H3v5"
        )
    }

    val Sun: ImageVector by lazy {
        lucide(
        "Sun",
        "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
        "M12 2v2",
        "M12 20v2",
        "m4.93 4.93 1.41 1.41",
        "m17.66 17.66 1.41 1.41",
        "M2 12h2",
        "M20 12h2",
        "m6.34 17.66-1.41 1.41",
        "m19.07 4.93-1.41 1.41"
        )
    }

    val TriangleAlert: ImageVector by lazy {
        lucide(
        "TriangleAlert",
        "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
        "M12 9v4",
        "M12 17h.01"
        )
    }

    val Truck: ImageVector by lazy {
        lucide(
        "Truck",
        "M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2",
        "M15 18H9",
        "M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.624l-3.48-4.35A1 1 0 0 0 17.52 8H14",
        "M15 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        "M5 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0"
        )
    }

    val Wrench: ImageVector by lazy {
        lucide(
        "Wrench",
        "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"
        )
    }
}

private fun lucide(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
