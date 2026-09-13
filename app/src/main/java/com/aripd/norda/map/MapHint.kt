package com.aripd.norda.map

import android.content.Context
import com.aripd.norda.R
import com.aripd.norda.core.map.MapCoverage

/**
 * Turns the empty-map reason into the one line the screens show
 * (docs/MVP.md, 7.4). Shared by the recording screen and the map screen so
 * the field gets the same sentence wherever the grid appears.
 */
object MapHint {

    /** The line to show, or null when there is nothing to say. */
    fun text(
        context: Context,
        state: MapCoverage.State,
        packageName: String?,
        zoom: Int
    ): String? = when (state) {
        MapCoverage.State.OK -> null
        MapCoverage.State.NO_PACKAGE -> context.getString(R.string.map_hint_no_package)
        // An unreadable package leaves no name to print; the sentence then
        // says only that nothing covers this spot.
        MapCoverage.State.OUT_OF_BOUNDS ->
            if (packageName.isNullOrBlank()) context.getString(R.string.map_hint_no_cover)
            else context.getString(R.string.map_hint_out_of_bounds, packageName)
        MapCoverage.State.MISSING_TILES ->
            context.getString(R.string.map_hint_missing_tiles, packageName.orEmpty(), zoom)
    }
}
