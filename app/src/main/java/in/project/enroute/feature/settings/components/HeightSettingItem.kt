package `in`.project.enroute.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.project.enroute.feature.settings.SettingsViewModel

@Composable
fun HeightSettingItem(
    viewModel: SettingsViewModel
) {
    val uiState = viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    LaunchedEffect(uiState.value.isEditingHeight) {
        if (uiState.value.isEditingHeight) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    
    Column {
        // Height label
        Text(
            text = "Height",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Height display/edit box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.value.isEditingHeight) {
                    BasicTextField(
                        value = uiState.value.heightInputValue,
                        onValueChange = { newValue ->
                            val isValid = if (newValue.isEmpty()) {
                                true
                            } else if (newValue.contains(".")) {
                                val parts = newValue.split(".")
                                parts.size == 2 && 
                                parts[0].length <= 3 && parts[0].all { it.isDigit() } &&
                                parts[1].length <= 1 && parts[1].all { it.isDigit() }
                            } else {
                                newValue.length <= 3 && newValue.all { it.isDigit() }
                            }
                            if (isValid) {
                                viewModel.updateHeightInput(newValue)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                viewModel.saveHeight()
                            }
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .focusRequester(focusRequester)
                    )
                } else {
                    Text(
                        text = if (uiState.value.currentHeight != null) "${uiState.value.currentHeight} cm" else "Not set",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Text(
                    text = if (uiState.value.isEditingHeight) "Save" else "Edit",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .clickable {
                            if (uiState.value.isEditingHeight) {
                                viewModel.saveHeight()
                            } else {
                                viewModel.toggleEditHeight()
                            }
                        }
                )
            }
        }
    }
}
