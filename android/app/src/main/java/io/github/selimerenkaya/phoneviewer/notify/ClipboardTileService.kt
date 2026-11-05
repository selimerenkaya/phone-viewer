package io.github.selimerenkaya.phoneviewer.notify

import android.service.quicksettings.TileService

/** notify lane: Quick Settings tile. Must open ClipboardActivity: a tile cannot read the clipboard. */
class ClipboardTileService : TileService()
