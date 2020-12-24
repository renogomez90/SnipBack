package com.hipoint.snipback.videoControl

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService
import com.hipoint.snipback.Utils.BufferDataDetails
import com.hipoint.snipback.Utils.VideoUtils
import com.hipoint.snipback.enums.CurrentOperation
import com.hipoint.snipback.enums.SwipeAction
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
        val ACTION             = "com.hipoint.snipback.VideoOpAction"
        val VIDEO_OP_ITEM      = "VIDEO_OP_ITEM"
        val LAUNCHED_FROM_EXTRA      = "LAUNCHED_FROM"
        val SWIPE_ACTION_EXTRA = "SWIPE_ACTION"

        val STATUS_NO_VALUE      = -1
        val STATUS_OP_SUCCESS    = 1
        val STATUS_OP_FAILED     = 2
        val STATUS_SHOW_PROGRESS = 3
        val STATUS_HIDE_PROGRESS = 4

        var isProcessing = false
        val ignoreResultOf: ArrayList<IVideoOpListener.VideoOp> = arrayListOf()
        val bufferDetails: ArrayList<BufferDataDetails> = arrayListOf() // saves path to video and corresponding video-buffer

        private var workQueue: Queue<VideoOpItem> = LinkedList()

        fun enqueueWork(context: Context, work: Intent) {
            enqueueWork(context, VideoService::class.java, 10, work)
        }
    }

    private val TAG = VideoService::class.java.simpleName
    private val channelId = "Snipback_notification"
    private val vUtil = VideoUtils(this@VideoService)
    private val broadcastIntent = Intent()

    override fun onHandleWork(intent: Intent) {

        val items = intent.getParcelableArrayListExtra<VideoOpItem>(VIDEO_OP_ITEM)
        items?.forEach {
            workQueue.add(it)
            Log.d(TAG, "onHandleWork: $it")
        }

        processQueue()
    }

    private fun processQueue() {
        isProcessing = true

        if (workQueue.isNotEmpty()) {
            val work = workQueue.remove()

            broadcastIntent.apply {
                action = ACTION
                putExtra("progress", STATUS_SHOW_PROGRESS)
                putExtra("operation", work.operation.name)
                putExtra(LAUNCHED_FROM_EXTRA, work.comingFrom.name)
            }
            sendBroadcast(broadcastIntent)

            with(work) {
                Log.d(TAG, "processQueue: operation = $operation")
                when (operation) {
                    IVideoOpListener.VideoOp.CONCAT -> {
                        if (clips.isEmpty() || clips.size < 2) {
                            failed(IVideoOpListener.VideoOp.CONCAT, comingFrom)
                            return@with
                        } else {
                            CoroutineScope(IO).launch {
                                val clipFiles = arrayListOf<File>()
                                clips.forEach { clipFiles.add(File(it)) }
                                VideoUtils(this@VideoService).concatenateMultiple(clipFiles, outputPath, comingFrom, swipeAction)
                                return@launch
                            }
                        }
                    }
                    IVideoOpListener.VideoOp.MERGED -> {
                        if (clips.isEmpty()) {
                            failed(IVideoOpListener.VideoOp.MERGED, comingFrom)
                            return@with
                        } else {
                            CoroutineScope(IO).launch {
                                val clip1 = clips[0]
                                val clip2 = clips[1]
                                VideoUtils(this@VideoService).mergeRecordedFiles(File(clip1), File(clip2), outputPath, comingFrom, swipeAction)
                                return@launch
                            }
                        }
                    }
                    IVideoOpListener.VideoOp.TRIMMED -> {
                        if (startTime == -1 || endTime == -1) {
                            failed(IVideoOpListener.VideoOp.TRIMMED, comingFrom)
                            return@with
                        } else {
                            CoroutineScope(IO).launch {
                                VideoUtils(this@VideoService).trimToClip(File(clips[0]), outputPath, startTime, endTime, comingFrom, swipeAction)
                                return@launch
                            }
                        }
                    }
                    IVideoOpListener.VideoOp.SPLIT -> {
                        if (splitTime == -1) {
                            failed(IVideoOpListener.VideoOp.SPLIT, comingFrom)
                            return@with
                        } else {
                            CoroutineScope(IO).launch {
                                VideoUtils(this@VideoService).splitVideo(File(clips[0]), splitTime, outputPath, comingFrom, swipeAction)
                                return@launch
                            }
                        }
                    }
                    IVideoOpListener.VideoOp.SPEED -> {
                        if (speedDetailsList == null) {
                            failed(IVideoOpListener.VideoOp.SPEED, comingFrom)
                            return@with
                        } else {
                            CoroutineScope(IO).launch {
                                VideoUtils(this@VideoService).changeSpeed(File(clips[0]), speedDetailsList, outputPath, comingFrom, swipeAction)
                                return@launch
                            }
                        }
                    }
                    IVideoOpListener.VideoOp.FRAMES -> {
                        if (outputPath.isBlank() || clips[0].isBlank()) {
                            failed(IVideoOpListener.VideoOp.FRAMES, comingFrom)
                            return@with
                        } else {
                            CoroutineScope(IO).launch {
                                VideoUtils(this@VideoService).getThumbnails(File(clips[0]), outputPath, comingFrom, swipeAction)
                                return@launch
                            }
                        }
                    }
                    IVideoOpListener.VideoOp.KEY_FRAMES -> {
                        if(outputPath.isBlank() || clips[0].isBlank()){
                            failed(IVideoOpListener.VideoOp.KEY_FRAMES, comingFrom)
                            return@with
                        }else{
                            CoroutineScope(IO).launch {
                                VideoUtils(this@VideoService).addIDRFrame(File(clips[0]), outputPath, comingFrom, swipeAction)
                            }
                        }
                    }
                    else -> {
                    }
                }
            }
        } else {
            stopSelf()
        }
    }

    override fun failed(operation: IVideoOpListener.VideoOp, calledFrom: CurrentOperation) {
        Log.e(TAG, "failed: ${operation.name}")
        isProcessing = false
        //  only process the next item after the previous one is completed

        broadcastIntent.apply {
            action = ACTION
            putExtra("status", STATUS_OP_FAILED)
            putExtra("progress", STATUS_HIDE_PROGRESS)
            putExtra("operation", operation.name)
            putExtra(LAUNCHED_FROM_EXTRA, calledFrom.name)
        }
        sendBroadcast(broadcastIntent)

        processQueue()
    }

    override fun changed(
        operation: IVideoOpListener.VideoOp,
        calledFrom: CurrentOperation,
        swipeAction: SwipeAction,
        processedVideoPath: String
    ) {
        Log.i(TAG, "${operation.name} completed: $processedVideoPath")
        isProcessing = false
        broadcastIntent.apply {
            action = ACTION
            putExtra("status", STATUS_OP_SUCCESS)
            putExtra("progress", STATUS_HIDE_PROGRESS)
            putExtra("operation", operation.name)
            putExtra(SWIPE_ACTION_EXTRA, swipeAction.name)
            putExtra("processedVideoPath", processedVideoPath)
            putExtra(LAUNCHED_FROM_EXTRA, calledFrom.name)
        }
        sendBroadcast(broadcastIntent)

        //  only process the next item after the previous one is completed
        processQueue()
    }
}