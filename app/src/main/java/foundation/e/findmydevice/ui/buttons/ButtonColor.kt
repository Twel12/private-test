package foundation.e.findmydevice.ui.buttons
import foundation.e.elib.R
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource

@Composable
fun buttonColor(): ButtonColors {
    return ButtonDefaults.buttonColors(
        containerColor = colorResource(R.color.e_accent),
        contentColor =
            if (isSystemInDarkTheme()) colorResource(R.color.e_primary_text_color_light)
            else colorResource(R.color.e_primary_text_color_dark)
    )
}

@Composable
fun actionColor(): ButtonColors {
    return ButtonDefaults.buttonColors(
        containerColor = Color.Transparent,
        contentColor = colorResource(R.color.e_accent_light)
    )
}
