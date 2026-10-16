package com.houseofvino.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.houseofvino.core.model.DrinkStatus
import com.houseofvino.ui.theme.Hov
import com.houseofvino.ui.theme.Space

/** A 1 dp rule between rows of a list — it marks a row boundary, nothing else. */
@Composable
fun Hairline(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    Box(modifier.padding(start = inset).fillMaxWidth().height(1.dp).background(Hov.colors.hairline))
}

/**
 * Screen header: serif title, optional one-line summary under it, and actions on the right.
 * [offline] adds the quiet "changes are saved on this phone" line.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onBack: (() -> Unit)? = null,
    offline: Boolean = false,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Hov.colors.canvas)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = if (onBack != null) 4.dp else Space.gutter, end = 4.dp, top = Space.s, bottom = Space.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                HovIconButton(HovIcons.Back, "Back", onClick = onBack)
            }
            Text(
                title,
                style = Hov.type.title,
                color = Hov.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
        if (summary != null) {
            Text(
                summary,
                style = Hov.type.detail,
                color = Hov.colors.inkMuted,
                modifier = Modifier.padding(start = if (onBack != null) Space.l else 0.dp),
            )
        }
        if (offline) OfflineLine(Modifier.padding(start = if (onBack != null) Space.l else 0.dp, top = Space.xs))
    }
}

@Composable
fun OfflineLine(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(HovIcons.Offline, contentDescription = null, tint = Hov.colors.inkMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Space.s))
        Text("Offline — changes are saved on this phone and will sync.", style = Hov.type.caption, color = Hov.colors.inkMuted)
    }
}

@Composable
fun HovIconButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = Hov.colors.ink, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(Space.touch)) {
        Icon(icon, contentDescription = label, tint = if (enabled) tint else Hov.colors.inkFaint, modifier = Modifier.size(24.dp))
    }
}

/** The one filled burgundy button per screen. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val c = Hov.colors
    Row(
        modifier
            .heightIn(min = Space.touch)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) c.burgundy else c.surfaceRaised)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xl, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) c.onBurgundy else c.inkFaint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(text, style = Hov.type.labelStrong, color = if (enabled) c.onBurgundy else c.inkFaint)
    }
}

/** Outlined, quieter companion to [PrimaryButton]. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, danger: Boolean = false) {
    val c = Hov.colors
    val fg = when {
        !enabled -> c.inkFaint
        danger -> c.danger
        else -> c.ink
    }
    Row(
        modifier
            .heightIn(min = Space.touch)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (danger && enabled) c.danger else c.hairline, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(text, style = Hov.type.label, color = fg)
    }
}

/** Text-only action, e.g. "Undo" or "See all". */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Hov.colors.burgundyBright) {
    Box(
        modifier.heightIn(min = Space.touch).clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Hov.type.labelStrong, color = color)
    }
}

/** Serif section heading in sentence case. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = Space.gutter).padding(top = Space.xl, bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Hov.type.heading, color = Hov.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
        trailing()
    }
}

/** Drinking-window status: a small mark plus the word, never colour alone. */
@Composable
fun StatusMark(status: DrinkStatus, modifier: Modifier = Modifier, showUnknown: Boolean = false) {
    if (status == DrinkStatus.UNKNOWN && !showUnknown) return
    val color = Hov.colors.status(status)
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(status.label, style = Hov.type.caption, color = color)
    }
}

/** A filter/sort chip. Selected chips carry the burgundy accent. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, trailingCount: Int? = null) {
    val c = Hov.colors
    Row(
        modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) c.burgundy else c.surface)
            .border(1.dp, if (selected) c.burgundy else c.hairline, RoundedCornerShape(18.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = if (selected) "$text, selected" else text }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Hov.type.label, color = if (selected) c.onBurgundy else c.ink)
        if (trailingCount != null && trailingCount > 0) {
            Spacer(Modifier.width(6.dp))
            Text("$trailingCount", style = Hov.type.figure, color = if (selected) c.onBurgundy else c.burgundyBright)
        }
    }
}

/** Empty, error and permission states share one shape: a sentence and the action that helps. */
@Composable
fun MessageState(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.xxl),
        horizontalAlignment = Alignment.Start,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Hov.colors.inkMuted, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(Space.m))
        }
        Text(title, style = Hov.type.heading, color = Hov.colors.ink)
        if (body != null) {
            Spacer(Modifier.height(Space.s))
            Text(body, style = Hov.type.body, color = Hov.colors.inkMuted, modifier = Modifier.widthIn(max = 520.dp))
        }
        if (primary != null || secondary != null) {
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                primary?.let { (t, a) -> PrimaryButton(t, a) }
                secondary?.let { (t, a) -> SecondaryButton(t, a) }
            }
        }
    }
}

/** Placeholder rows that keep the vintage-column rhythm while a list loads. */
@Composable
fun LoadingRows(count: Int = 6, modifier: Modifier = Modifier) {
    val c = Hov.colors
    Column(modifier.semantics { contentDescription = "Loading" }) {
        repeat(count) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 44.dp, height = 22.dp).clip(RoundedCornerShape(3.dp)).background(c.surface))
                Spacer(Modifier.width(Space.l + 4.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(16.dp).clip(RoundedCornerShape(3.dp)).background(c.surface))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.35f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(c.surface))
                }
            }
            Hairline(inset = Space.gutter)
        }
    }
}

/** A labelled value in a detail screen ("Bought from", "Glengarry"). */
@Composable
fun Fact(label: String, value: String?, modifier: Modifier = Modifier) {
    if (value.isNullOrBlank()) return
    Row(modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = 6.dp)) {
        Text(label, style = Hov.type.detail, color = Hov.colors.inkMuted, modifier = Modifier.width(132.dp))
        Text(value, style = Hov.type.detail, color = Hov.colors.ink, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ProvideInk(color: Color, content: @Composable () -> Unit) = CompositionLocalProvider(LocalContentColor provides color, content = content)

val ListPadding = PaddingValues(bottom = 96.dp)
