package foundation.e.geolocationsms.ui.buttons

import android.annotation.SuppressLint
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import foundation.e.elib.R
import foundation.e.geolocationsms.util.Dimens
import kotlin.math.roundToInt

@SuppressLint("ComposableNaming")
@Composable
fun toggleWithText(
    text: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
        Arrangement.SpaceBetween, // Aligns children at the start and end of the row
        modifier = Modifier.padding(top = Dimens.SCREEN_PADDING, bottom = Dimens.SCREEN_PADDING)
    ) {
        Text(text, fontWeight = fontWeight, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(Dimens.SCREEN_PADDING / 2))
        eSwitch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun eSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val thumbSize = 24.dp
    val trackWidth = 52.dp
    val trackHeight = 28.dp
    val switchPadding = 2.dp

    val transition = updateTransition(targetState = checked, label = "switch")

    val thumbOffsetX by
    transition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "Thumb offset"
    ) { state ->
        if (state) trackWidth - thumbSize - switchPadding else switchPadding
    }

    Box(
        modifier =
        Modifier.width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(
                color =
                if (checked) colorResource(R.color.e_switch_track_on)
                else colorResource(R.color.e_switch_track_off)
            )
            .clickable(
                onClick = { onCheckedChange(!checked) },
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
    ) {
        Box(
            modifier =
            Modifier.size(thumbSize)
                .offset {
                    IntOffset(thumbOffsetX.roundToPx(), switchPadding.toPx().roundToInt())
                }
                .clip(CircleShape)
                .background(
                    color =
                    if (checked) colorResource(R.color.e_switch_thumb_on)
                    else colorResource(R.color.e_switch_thumb_off)
                )
                .clickable(
                    onClick = { onCheckedChange(!checked) },
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                )
        )
    }
}
