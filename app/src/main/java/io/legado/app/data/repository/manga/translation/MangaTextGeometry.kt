package io.legado.app.data.repository.manga.translation

import android.graphics.Rect

/** A tall paragraph of horizontal lines is not vertical lettering. */
internal fun mangaVerticalText(lines: List<Rect>): Boolean = lines.isNotEmpty() &&
    lines.count { it.height() > it.width() * 1.4f } > lines.size / 2
