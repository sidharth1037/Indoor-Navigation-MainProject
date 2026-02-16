package `in`.project.enroute.feature.pdr.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.util.Locale

/**
 * Small pill-shaped button displaying the current motion classification and confidence.
 * Shown only while PDR is actively tracking.
 */
@Composable
fun MotionLabel(
    label: String,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val pct = (confidence * 100).toInt()
    val displayLabel = label.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
    }

    Button(
        onClick = { },
        enabled = false,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier
    ) {
        Text(
            text = "$displayLabel ($pct%)",
            style = MaterialTheme.typography.labelLarge
        )
    }
}
