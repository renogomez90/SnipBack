package com.hipoint.snipback.Utils

import android.content.Context
import java.io.File

class Constants(context: Context) {
    private val INTERNAL_VIDEO_DIR_NAME = "SnipBackVirtual"
    private val EXTERNAL_VIDEO_DIR_NAME = "Snipback"
    private val PREVIEW_TILE_DIR_NAME   = "PreviewTiles"

    val INTERNAL_PARENT_DIR = context.dataDir.absolutePath
    val INTERNAL_VIDEO_DIR  = INTERNAL_PARENT_DIR + File.separator + INTERNAL_VIDEO_DIR_NAME
    val EXTERNAL_VIDEO_DIR  = context.externalMediaDirs[0].absolutePath + File.separator + EXTERNAL_VIDEO_DIR_NAME
}