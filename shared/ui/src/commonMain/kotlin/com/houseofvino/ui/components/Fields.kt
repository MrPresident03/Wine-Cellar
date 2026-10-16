package com.houseofvino.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.houseofvino.ui.theme.Hov
import com.houseofvino.ui.theme.Space

/** Label above, value in a quiet box, help or error below. */
@Composable
fun HovTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    help: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val c = Hov.colors
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(label, style = Hov.type.label, color = if (error != null) c.danger else c.inkMuted)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Space.touch)
                .clip(RoundedCornerShape(4.dp))
                .background(c.surface)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = when {
                        error != null -> c.danger
                        focused -> c.burgundyBright
                        else -> c.hairline
                    },
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = Space.m, vertical = Space.m),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = Hov.type.body, color = c.inkFaint)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    textStyle = Hov.type.body.copy(color = if (enabled) c.ink else c.inkFaint),
                    cursorBrush = SolidColor(c.burgundyBright),
                    keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, imeAction = imeAction),
                    keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                        .semantics {
                            contentDescription = label
                            if (error != null) error(error)
                        },
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(Space.s))
                trailing()
            }
        }
        val below = error ?: help
        if (below != null) {
            Spacer(Modifier.height(4.dp))
            Text(below, style = Hov.type.caption, color = if (error != null) c.danger else c.inkMuted)
        }
    }
}

/** −  6  + with large touch targets; long values can also be typed. */
@Composable
fun QuantityStepper(value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier, min: Int = 1, max: Int = 10_000, label: String = "Quantity") {
    val c = Hov.colors
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, c.hairline, RoundedCornerShape(8.dp))
            .semantics(mergeDescendants = false) { stateDescription = "$label $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HovIconButton(HovIcons.Minus, "Fewer", enabled = value > min) { onValueChange((value - 1).coerceAtLeast(min)) }
        BasicTextField(
            value = value.toString(),
            onValueChange = { t -> t.filter { it.isDigit() }.take(5).toIntOrNull()?.let { onValueChange(it.coerceIn(min, max)) } },
            singleLine = true,
            textStyle = Hov.type.figureLarge.copy(color = c.ink, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(c.burgundyBright),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.widthIn(min = 48.dp).width(64.dp).semantics { contentDescription = label },
        )
        HovIconButton(HovIcons.Add, "More", enabled = value < max) { onValueChange((value + 1).coerceAtMost(max)) }
    }
}

/** A row of mutually exclusive choices shown as chips (sizes, vintages, styles). */
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        options.forEach { o -> Chip(label(o), selected = o == selected, onClick = { onSelect(o) }) }
    }
}

/** A tappable row that opens a picker ("Place: Cellar › Rack A"). */
@Composable
fun PickerField(label: String, value: String?, placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier, error: String? = null) {
    val c = Hov.colors
    Column(modifier.fillMaxWidth()) {
        Text(label, style = Hov.type.label, color = if (error != null) c.danger else c.inkMuted)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Space.touch)
                .clip(RoundedCornerShape(4.dp))
                .background(c.surface)
                .border(1.dp, if (error != null) c.danger else c.hairline, RoundedCornerShape(4.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "$label: ${value ?: placeholder}" }
                .padding(horizontal = Space.m, vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value ?: placeholder, style = Hov.type.body, color = if (value != null) c.ink else c.inkFaint, modifier = Modifier.weight(1f))
            Icon(HovIcons.ChevronDown, null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
        }
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(error, style = Hov.type.caption, color = c.danger)
        }
    }
}

/** Plain checkbox row with a generous touch target. */
@Composable
fun CheckRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    val c = Hov.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch)
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) }
            .semantics { stateDescription = if (checked) "Checked" else "Not checked" }
            .padding(horizontal = Space.gutter, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(4.dp))
                .background(if (checked) c.burgundy else c.surface)
                .border(1.dp, if (checked) c.burgundy else c.inkFaint, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(HovIcons.Check, null, tint = c.onBurgundy, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(text, style = Hov.type.body, color = c.ink)
            if (detail != null) Text(detail, style = Hov.type.caption, color = c.inkMuted)
        }
    }
}
