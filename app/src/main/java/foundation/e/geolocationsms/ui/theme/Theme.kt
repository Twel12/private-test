package foundation.e.geolocationsms.ui.theme

import android.annotation.SuppressLint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource

@SuppressLint("ComposableNaming")
@Composable
fun geolocationSmsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme =
        if (darkTheme) {
            darkColorScheme(
                primary = colorResource(foundation.e.elib.R.color.e_action_bar_dark),
                secondary = colorResource(foundation.e.elib.R.color.e_action_bar_dark),
                tertiary = colorResource(foundation.e.elib.R.color.e_accent_dark),
                background = colorResource(foundation.e.elib.R.color.e_background_dark),
                surface = colorResource(foundation.e.elib.R.color.e_floating_background_dark),
                onPrimary = colorResource(foundation.e.elib.R.color.e_primary_text_color_dark),
                onSecondary = colorResource(foundation.e.elib.R.color.e_primary_text_color_light),
                onBackground = colorResource(foundation.e.elib.R.color.e_primary_text_color_dark),
                onSurface = colorResource(foundation.e.elib.R.color.e_primary_text_color_dark)
            )
        } else {
            lightColorScheme(
                primary = colorResource(foundation.e.elib.R.color.e_action_bar_light),
                secondary = colorResource(foundation.e.elib.R.color.e_action_bar_light),
                tertiary = colorResource(foundation.e.elib.R.color.e_accent_light),
                background = colorResource(foundation.e.elib.R.color.e_background_light),
                surface = colorResource(foundation.e.elib.R.color.e_floating_background_light),
                onPrimary = colorResource(foundation.e.elib.R.color.e_primary_text_color_light),
                onSecondary = colorResource(foundation.e.elib.R.color.e_primary_text_color_dark),
                onBackground = colorResource(foundation.e.elib.R.color.e_primary_text_color_light),
                onSurface = colorResource(foundation.e.elib.R.color.e_primary_text_color_light),
            )
        }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
