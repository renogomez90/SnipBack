package com.hipoint.snipback.videoControl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.JobIntentService
import androidx.core.app.NotificationCompat
import com.hipoint.snipback.Utils.VideoUtils
import com.hipoint.snipback.listener.IVideoOpListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import java.io.File
import java.util.*


/**
 * Queues and executes video edits
 */
class VideoService : JobIntentService(), IVideoOpListener {
    companion object {
        val ACTION = "com.hipoint.snipback.VideoOpAction"
        val VIDEO_OP_ITEM = "VIDEO_OP_ITEM"
        val NOTIFICATION_ID = 6020

        val STATUS_NO_VALUE = -1
        val STATUS_OP_SUCCESS = 1
        val STATUS_OP_FAILED = 2
        val STATUS_SHOW_PROGRESS = 3
        val STATUS_HIDE_PROGRESS = 4

        private var workQueue: Queue<VideoOpItem> = LinkedList<VideoOpItem>()
        private var isProcessing = false

        fun enqueueWork(context: Context, work:Intent){
            enqueueWork(context, VideoService::class.java, 10, work)
        }
    }

    private val TAG = VideoService::class.java.simpleName
    private val channelId = "Snipback_notification"
    private val vUtil = VideoUtils(this@VideoService)
    private val broadcastIntent = Intent()

/*
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Service starting")
*/
/*
            createNotificationChannel()
            val notification: Notification = NotificationCompat.Builder(this, channelId)
                    .setContentTitle("Foreground Service")
                    .setContentText("Snipback Video Processing")
                    .setContentText("Your videos are being prepared and will be available shortly")
                    .build()

            startForeground(NOTIFICATION_ID, notification)
//        }*//*


        val items = intent?.getParcelableArrayListExtra<VideoOpItem>(VIDEO_OP_ITEM)
        items?.forEach {
            workQueue.add(it)
            Log.d(TAG, "onStartCommand: $it")
        }

        processQueue()
        return START_STICKY
    }
*/

    override fun onHandleWork(intent: Intent) {

        val items = intent.getParcelableArrayListExtra<VideoOpItem>(VIDEO_OP_ITEM)
        items?.forEach {
            workQueue.add(it)
            Log.d(TAG, "onHandleWork: $it")
        }

        processQueue()
    }

    /*private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(channelId,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
*/
    private fun processQueue() {
        Log.d(TAG, "processQueue: processing")
        isProcessing = true

        if (workQueue.isNotEmpty()) {

            broadcastIntent.apply {
                action = ACTION
                putExtra("progress", STATUS_SHOW_PROGRESS)
            }
            sendBroadcast(broadcastIntent)

            val work = workQueue.remove()
            with(work) {
                when (operation) {
                    IVideoOpListener.VideoOp.CONCAT -> {
                        if (clip2.isBlank()) {
                            failed(IVideoOpListener.VideoOp.CONCAT)
                            return@with
                        } else {
                            CoroutineScope(IO).launch {
                                vUtil.concatenateFiles(File(clip1), File(clip2), outputPath)
                                return@launch
                            }
                        }
                    }
                    IVideoOpListener.VideoOp.MERGED -> {
                        if (clip2.isBlank()) {
                            failed(IVideoOpListener.VideoOp.MERGED)
                            return@with
                        } else {
                            CoroutineScope(IO).launch {
                                vUtil.mergeRecordedFiles(File(clip1), File(clip2), outputPath)
                                return@launch
                            }
                        }
                    }
                    IVideoOpListener.VideoOp.TRIMMED -> {
                        if (startTime == -1 || endTime == -1) {
                            failed(IVideoOpListener.VideoOp.TRIMMED)
                            return@with
                        } else {
                            CoroutineScope(IO).launch {
                                vUtil.trimToClip(File(clip1), outputPath, startTime, endTime)
                                return@launch
                            }
                        }
                    }
                    IVideoOpListener.VideoOp.SPLIT -> {
                        if (splitTime == -1) {
                            failed(IVideoOpListener.VideoOp.SPLIT)
                            return@with
                        } else {
                            CoroutineScope(IO).launch {
                                vUtil.splitVideo(File(clip1), splitTime, outputPath)
                                return@launch
                            }
                        }
                    }
                    else ->{}
                }
            }
        } else {
            stopSelf()
        }
    }

    override fun failed(operation: IVideoOpListener.VideoOp) {
        Log.e(TAG, "failed: ${operation.name}")
        isProcessing = false
        //  only process the next item after the previous one is completed

        broadcastIntent.apply {
            action = ACTION
            putExtra("status", STATUS_OP_FAILED)
            putExtra("progress", STATUS_HIDE_PROGRESS)
            putExtra("operation", operation.name)
        }
        sendBroadcast(broadcastIntent)

        processQueue()
    }

    override fun changed(operation: IVideoOpListener.VideoOp, processedVideoPath: String) {
        Log.i(TAG, "${operation.name} completed: $processedVideoPath")
        isProcessing = false
        broadcastIntent.apply {
            action = ACTION
            putExtra("status", STATUS_OP_SUCCESS)
            putExtra("progress", STATUS_HIDE_PROGRESS)
            putExtra("operation", operation.name)
            putExtra("processedVideoPath", processedVideoPath)
        }
        sendBroadcast(broadcastIntent)

        //  only process the next item after the previous one is completed
        processQueue()
    }
}