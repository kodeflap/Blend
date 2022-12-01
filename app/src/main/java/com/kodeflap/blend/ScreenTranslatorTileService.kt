package com.kodeflap.blend

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class ScreenTranslatorTileService: TileService() {
    override fun onClick() {
        super.onClick()
        Intent(this, ScreenTranslationService::class.java).also { intent ->
            startService(intent)
        }
    }
}