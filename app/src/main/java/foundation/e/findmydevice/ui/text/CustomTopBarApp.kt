package foundation.e.findmydevice.ui.text

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import foundation.e.findmydevice.util.Dimens

@SuppressLint("ComposableNaming")
@Composable
fun customTopAppBar(title: String, onClick: () -> Unit, hideBackButton: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = Dimens.SCREEN_PADDING, bottom = Dimens.SCREEN_PADDING)
    ) {
        // Back button
        IconButton(
            onClick = { onClick() },
            enabled = !hideBackButton,
            modifier = Modifier.alpha(if (hideBackButton) 0f else 1f)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
            )
        }
        Text(
            text = title,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            color = colorResource(foundation.e.elib.R.color.e_primary_text_color),
            modifier = Modifier.padding(start = Dimens.SCREEN_PADDING)
        )
    }
}
