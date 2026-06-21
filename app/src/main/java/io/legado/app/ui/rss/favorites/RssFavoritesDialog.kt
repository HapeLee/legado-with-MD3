package io.legado.app.ui.rss.favorites

import android.content.Context
import io.legado.app.data.entities.RssStar

/**
 * Bridge for the legacy video player to interact with RSS favorites.
 * The actual UI is implemented as a Compose screen; this dialog stub keeps
 * backward compatibility for callers that still reference it.
 */
class RssFavoritesDialog private constructor() {

    interface Callback {
        fun onRssStarSaved(rssStar: RssStar?)
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun show(context: Context, rssStar: RssStar?, callback: Callback? = null) {
            // Compose-based RssFavoritesScreen is the canonical implementation.
            // This stub exists only for legacy callers.
        }
    }
}
