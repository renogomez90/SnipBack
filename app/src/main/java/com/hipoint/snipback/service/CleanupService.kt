package com.hipoint.snipback.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.hipoint.snipback.fragment.VideoMode
import com.hipoint.snipback.room.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import java.io.File

class CleanupService: Service() {
    private val TAG = CleanupService::class.java.simpleName

    private lateinit var appRepository : AppRepository

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        appRepository = AppRepository.instance!!
        CoroutineScope(IO).launch {
            val hdSnips = appRepository.getAllHDSnips()
            val currentSnips = appRepository.getAllSnips()

            val dbPaths = mutableSetOf<String>()
            currentSnips?.forEach {
                dbPaths.add(it.videoFilePath?: "")
            }
            hdSnips?.forEach {
                dbPaths.add(it.video_path_processed ?: "")
                dbPaths.add(it.video_path_unprocessed ?: "")
            }
            val existingFileList = File(dataDir, VideoMode.VIDEO_DIRECTORY_NAME).listFiles { pathname -> !pathname.isDirectory }
            existingFileList?.forEach {
                if (!dbPaths.contains(it.absolutePath)) {
                    it.delete()
                }
            }
        }

        return START_STICKY
    }
}