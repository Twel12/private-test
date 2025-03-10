package foundation.e.geolocationsms.ui.buttons

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import foundation.e.elib.R

@Composable
fun buttonColor(): ButtonColors {
    return ButtonDefaults.buttonColors(
        containerColor = colorResource(R.color.e_accent),
        contentColor =
            if (isSystemInDarkTheme()) colorResource(R.color.e_primary_text_color_light)
            else colorResource(R.color.e_primary_text_color_dark)
    )
}