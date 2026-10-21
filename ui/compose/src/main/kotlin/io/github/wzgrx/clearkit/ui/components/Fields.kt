package io.github.wzgrx.clearkit.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.wzgrx.clearkit.ui.theme.Clear
import io.github.wzgrx.clearkit.ui.theme.Radius

/**
 * 输入框（规范 v1）：白底、14 圆角、浅描边，聚焦时强调色描边。参数和 Material 的 OutlinedTextField 一样（常用的那些）。
 */
@Composable
fun ClearTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    textStyle: androidx.compose.ui.text.TextStyle? = null,
) {
    // 在开着的卡片里（分组标题下面）时，放进卡片的一段里；调用方给的左右留白正好是卡片内边距
    val flow = LocalCardFlow.current
    val nested = LocalInCard.current
    val inCard = remember { flow.open && !nested }
    if (inCard) {
        CardSegment(top = false, bottom = false) {
            ClearTextFieldBody(value, onValueChange, modifier, enabled, readOnly, label, placeholder, leadingIcon, trailingIcon, prefix, suffix, supportingText, isError, visualTransformation, keyboardOptions, keyboardActions, singleLine, maxLines, minLines, textStyle)
        }
    } else {
        ClearTextFieldBody(value, onValueChange, modifier, enabled, readOnly, label, placeholder, leadingIcon, trailingIcon, prefix, suffix, supportingText, isError, visualTransformation, keyboardOptions, keyboardActions, singleLine, maxLines, minLines, textStyle)
    }
}

@Composable
private fun ClearTextFieldBody(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    readOnly: Boolean,
    label: @Composable (() -> Unit)?,
    placeholder: @Composable (() -> Unit)?,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    prefix: @Composable (() -> Unit)?,
    suffix: @Composable (() -> Unit)?,
    supportingText: @Composable (() -> Unit)?,
    isError: Boolean,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions,
    singleLine: Boolean,
    maxLines: Int,
    minLines: Int,
    textStyle: androidx.compose.ui.text.TextStyle?,
) {
    val c = Clear.colors
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
        textStyle = textStyle ?: Clear.type.item.copy(color = c.text),
        label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
        supportingText = supportingText, isError = isError, visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        shape = RoundedCornerShape(Radius.field),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, disabledContainerColor = c.muted, errorContainerColor = c.surface,
            focusedBorderColor = c.accent, unfocusedBorderColor = c.chipBorder, cursorColor = c.accent,
            focusedLabelColor = c.accent, unfocusedLabelColor = c.textSecondary,
        ),
    )
}
