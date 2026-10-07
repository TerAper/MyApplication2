package com.teraper.printmaster.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.teraper.printmaster.core.designsystem.theme.PmTheme

/** Labelled form field; shows [error] under it in red when set. */
@Composable
fun PmTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    singleLine: Boolean = true,
    minLines: Int = 1,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        trailingIcon = trailingIcon,
        shape = PmTheme.shapes.button,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = PmTheme.colors.surface,
            unfocusedContainerColor = PmTheme.colors.surface,
            errorContainerColor = PmTheme.colors.surface,
            unfocusedBorderColor = PmTheme.colors.outlineStrong,
        ),
    )
}
