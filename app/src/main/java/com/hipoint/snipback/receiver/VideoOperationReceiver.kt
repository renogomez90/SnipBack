package com.hipoint.snipback.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.AppMainActivity.Companion.doReplace
import com.hipoint.snipback.AppMainActivity.Companion.fileToReplace
import com.hipoint.snipback.AppMainActivity.Companion.parentChanged
import com.hipoint.snipback.AppMainActivity.Companion.parentSnip
import com.hipoint.snipback.AppMainActivity.Companion.replacedWith
import com.hipoint.snipback.Utils.BufferDataDetails
import com.hipoint.snipback.Utils.isPathInList
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.application.AppClass.showInGallery
import com.hipoint.snipback.application.AppClass.swipeProcessed
import com.hipoint.snipback.control.CameraControl
import com.hipoint.snipback.dialog.SettingsDialog
import com.hipoint.snipback.enums.CurrentOperation
import com.hipoint.snipback.enums.SwipeAction
import com.hipoint.snipback.fragment.SnapbackFragment
import com.hipoint.snipback.fragment.VideoEditingFragment
import com.hipoint.snipback.fragment.VideoMode
import com.hipoint.snipback.fragment.VideoMode.Companion.VIDEO_DIRECTORY_NAME
import com.hipoint.snipback.listener.IVideoOpListener
import com.hipoint.snipback.room.entities.Hd_snips
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.room.repository.AppRepository
import com.hipoint.snipback.videoControl.VideoOpItem
import com.hipoint.snipback.videoControl.VideoService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max

class VideoOperationReceiver: BroadcastReceiver(), AppRepository.OnTaskCompleted {
    private val TAG = VideoOperationReceiver::class.java.simpleName
    private val THUMBS_DIRECTORY_NAME = "Thumbs"

    private var receivedContext: Context?          = null
    private var addedToSnip    : ArrayList<String> = arrayListOf()

    private val appRepository by lazy { AppRepository(AppClass.getAppInstance()) }
    private val pref: SharedPreferences by lazy { receivedContext!!.getSharedPreferences(
        SettingsDialog.SETTINGS_PREFERENCES, Context.MODE_PRIVATE) }

    override fun onReceive(context: Context?, intent: Intent?) {
        receivedContext = context
        intent?.let{
            val operation = intent.getStringExtra("operation")
            val showProgress = intent.getIntExtra("progress", -1)
            val processedVideoPath = intent.getStringExtra("processedVideoPath")
            val currentOperationString = intent.getStringExtra(VideoService.LAUNCHED_FROM_EXTRA)
            val swipeActionString = intent.getStringExtra(VideoService.SWIPE_ACTION_EXTRA)
            val currentOperation = getCurrentOperation(currentOperationString)
            val swipeAction = getCurrentSwipeAction(swipeActionString)

            if (processedVideoPath.isNullOrBlank())
                return

            Log.d(TAG, "onReceive: $operation completed for $processedVideoPath")

            when (intent.getIntExtra("status", VideoService.STATUS_NO_VALUE)) {
                VideoService.STATUS_OP_SUCCESS -> {
                    when (operation) {
                        IVideoOpListener.VideoOp.CONCAT.name -> {
                            videoConcatCompleted(processedVideoPath, currentOperation, swipeAction)
                        }
                        IVideoOpListener.VideoOp.TRIMMED.name -> {
                            videoTrimCompleted(processedVideoPath, currentOperation, swipeAction)
                        }
                        IVideoOpListener.VideoOp.SPLIT.name -> {
                            videoSplitCompleted(processedVideoPath, currentOperation, swipeAction)
                        }
                        IVideoOpListener.VideoOp.SPEED.name -> {
                            videoSpeedChangeCompleted(processedVideoPath, currentOperation, swipeAction)
                        }
                        IVideoOpListener.VideoOp.FRAMES.name -> {
                            videoPreviewFramesCompleted(processedVideoPath, currentOperation, swipeAction)
                        }
                        IVideoOpListener.VideoOp.KEY_FRAMES.name -> {
                            videoFramesAdded(processedVideoPath, currentOperation, swipeAction)
                        }
                        else -> {
                        }
                    }
                }
                VideoService.STATUS_OP_FAILED -> {
                    Log.e(TAG, "onReceive: $operation failed")
                    val dismissIntent = Intent(VideoEditingFragment.DISMISS_ACTION)
                    receivedContext?.sendBroadcast(dismissIntent)

//                    dismissEditFragmentProcessingDialog(null)
//                    dismissQuickEditProcessingDialog()
                }
                else -> {
                }
            }
        }
    }

    private fun getCurrentOperation(fromOperation: String?): CurrentOperation {
        if (fromOperation == null)
            return CurrentOperation.CLIP_RECORDING

        return when (fromOperation) {
            CurrentOperation.VIDEO_RECORDING.name -> CurrentOperation.VIDEO_RECORDING
            CurrentOperation.CLIP_RECORDING.name -> CurrentOperation.CLIP_RECORDING
            CurrentOperation.VIDEO_EDITING.name -> CurrentOperation.VIDEO_EDITING
            else -> {
                CurrentOperation.CLIP_RECORDING
            }
        }
    }

    private fun getCurrentSwipeAction(swipeAction: String?): SwipeAction {
        if (swipeAction == null)
            return SwipeAction.NO_ACTION

        return when (swipeAction) {
            SwipeAction.SWIPE_RIGHT.name -> SwipeAction.SWIPE_RIGHT
            SwipeAction.SWIPE_LEFT.name -> SwipeAction.SWIPE_LEFT
            SwipeAction.SWIPE_DOWN.name -> SwipeAction.SWIPE_DOWN
            SwipeAction.SWIPE_UP.name -> SwipeAction.SWIPE_UP
            else -> SwipeAction.NO_ACTION
        }
    }

    /**
     * Video concatenation was successfully done and is available at processedVideoPath
     * Processed video is added to snip
     *
     * @param processedVideoPath
     */
    fun videoConcatCompleted(
        processedVideoPath: String,
        comingFrom: CurrentOperation,
        swipeAction: SwipeAction
    ) {
        if (VideoService.ignoreResultOf.isNotEmpty()) {
            if (VideoService.ignoreResultOf[0] == IVideoOpListener.VideoOp.CONCAT) {
                VideoService.ignoreResultOf.removeAt(0)
                val concatCompleteReceiver = Intent(VideoEditingFragment.EXTEND_TRIM_ACTION)
                concatCompleteReceiver.putExtra("operation", IVideoOpListener.VideoOp.CONCAT.name)
                concatCompleteReceiver.putExtra("fileName", processedVideoPath)
                receivedContext?.sendBroadcast(concatCompleteReceiver)
                return
            }
        }
        Log.d(TAG, "Concat $processedVideoPath completed")
        val totalDuration = getMetadataDurations(arrayListOf(processedVideoPath))[0]
        /*if (comingFrom != CurrentOperation.VIDEO_RECORDING) {
            addSnip(
                processedVideoPath,
                totalDuration,
                totalDuration
            )     //  merged file is saved to DB
        }*/

        val swipeClipDuration = (pref.getInt(SettingsDialog.QB_DURATION, 5) * 1000).toLong() / 1000
        val bufferDuration = (pref.getInt(SettingsDialog.BUFFER_DURATION, 1) * 60 * 1000).toLong() / 1000

        if (comingFrom == CurrentOperation.CLIP_RECORDING) {  //  concat was triggered when automatic capture was ongoing
            //  merged clips to be trimmed to size
            val intentService = Intent(receivedContext, VideoService::class.java)
            val buffFile =
                "${File(processedVideoPath).parent}/buff-${File(processedVideoPath).nameWithoutExtension}-1.mp4"    //  this is the file that is the buffer
            val split2File =
                "${File(processedVideoPath).parent}/${File(processedVideoPath).nameWithoutExtension}-1.mp4"    //  this is the file that the user will see
            val taskList = arrayListOf<VideoOpItem>()

            if(swipeAction == SwipeAction.SWIPE_LEFT) {
                val bufferFile = VideoOpItem(
                    operation = IVideoOpListener.VideoOp.TRIMMED,
                    clips = arrayListOf(processedVideoPath),
                    startTime = Integer.max((totalDuration - swipeClipDuration - bufferDuration).toInt(),0)
                        .toFloat(),
                    endTime = (totalDuration - swipeClipDuration).toFloat(),
                    outputPath = buffFile,
                    comingFrom = comingFrom,
                    swipeAction = swipeAction
                )

                VideoService.bufferDetails.add(BufferDataDetails(buffFile, split2File))
                taskList.add(bufferFile)
            }

            val videoFile = VideoOpItem(
                operation = IVideoOpListener.VideoOp.TRIMMED,
                clips = arrayListOf(processedVideoPath),
                startTime = (totalDuration - swipeClipDuration).toFloat(),
                endTime = totalDuration.toFloat(),
                outputPath = split2File,
                comingFrom = comingFrom,
                swipeAction = swipeAction
            )

            taskList.add(videoFile)

            intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, taskList)
            VideoService.enqueueWork(receivedContext!!, intentService)
        } else if (!swipeProcessed && comingFrom == CurrentOperation.VIDEO_RECORDING) {

            val startPendingProcessIntent = Intent(VideoMode.PENDING_SWIPE_ACTION)
            startPendingProcessIntent.putExtra("processedVideoPath", processedVideoPath)
            startPendingProcessIntent.putExtra("comingFrom", comingFrom)
            LocalBroadcastManager.getInstance(receivedContext!!).sendBroadcast(startPendingProcessIntent)
//            videoModeFragment.processPendingSwipes(processedVideoPath, comingFrom)
        }
    }

    /**
     * Video was successfully trimmed and is available at processedVideoPath
     *
     * @param processedVideoPath String
     */
    private fun videoTrimCompleted(
        processedVideoPath: String,
        fromOperation: CurrentOperation,
        swipeAction: SwipeAction
    ) {
        if (VideoService.ignoreResultOf.isNotEmpty()) {
            if (VideoService.ignoreResultOf[0] == IVideoOpListener.VideoOp.TRIMMED) {
                VideoService.ignoreResultOf.removeAt(0)
                if(fromOperation == CurrentOperation.VIDEO_EDITING) {
                    val trimCompleteReceiver = Intent(VideoEditingFragment.EXTEND_TRIM_ACTION)
                    trimCompleteReceiver.putExtra("operation",IVideoOpListener.VideoOp.TRIMMED.name)
                    trimCompleteReceiver.putExtra("fileName", processedVideoPath)
                    receivedContext?.sendBroadcast(trimCompleteReceiver)
                }else if(fromOperation == CurrentOperation.CLIP_RECORDING && swipeAction == SwipeAction.SWIPE_RIGHT){
                    val snapbackCompleteReceiver = Intent(SnapbackFragment.SNAPBACK_PATH_ACTION)
                    snapbackCompleteReceiver.putExtra("operation",IVideoOpListener.VideoOp.TRIMMED.name)
                    snapbackCompleteReceiver.putExtra(SnapbackFragment.EXTRA_VIDEO_PATH, processedVideoPath)
                    receivedContext?.sendBroadcast(snapbackCompleteReceiver)
                }
                return
            }
        }

        Log.d(TAG, "videoTrimCompleted $processedVideoPath Completed")
        if (AppMainActivity.virtualToReal) {
            AppMainActivity.virtualToReal = false
            val virtualToRealCompletedIntent = Intent(VideoEditingFragment.VIRTUAL_TO_REAL_ACTION)
            virtualToRealCompletedIntent.putExtra("video_path", processedVideoPath)
            receivedContext?.sendBroadcast(virtualToRealCompletedIntent)
        } else {
            if (fromOperation == CurrentOperation.VIDEO_EDITING)
                return

            CoroutineScope(Dispatchers.IO).launch {
                val duration = getMetadataDurations(arrayListOf(processedVideoPath))[0]
                addSnip(processedVideoPath, duration, duration, fromOperation)
            }
            if (!swipeProcessed && fromOperation == CurrentOperation.VIDEO_RECORDING) {
                val startPendingProcessIntent = Intent(VideoMode.PENDING_SWIPE_ACTION)
                startPendingProcessIntent.putExtra("processedVideoPath", processedVideoPath)
                startPendingProcessIntent.putExtra("comingFrom", fromOperation)
                LocalBroadcastManager.getInstance(receivedContext!!).sendBroadcast(startPendingProcessIntent)
//                videoModeFragment.processPendingSwipes()
            }
        }
    }

    /**
     * Video was successfully split and is available at processedVideoPath
     * Processed video is added to snip
     *
     * @param processedVideoPath String
     */
    private fun videoSplitCompleted(
        processedVideoPath: String,
        comingFrom: CurrentOperation,
        swipeAction: SwipeAction
    ) {
        if (VideoService.ignoreResultOf.isNotEmpty()) {
            if (VideoService.ignoreResultOf[0] == IVideoOpListener.VideoOp.SPLIT) {
                VideoService.ignoreResultOf.removeAt(0)
                return
            }
        }
        Log.d(TAG, "split $processedVideoPath Completed")
        CoroutineScope(Dispatchers.IO).launch {
            val pathList = arrayListOf("$processedVideoPath-0.mp4", "$processedVideoPath-1.mp4")
            getMetadataDurations(pathList).forEachIndexed { index, dur ->
                addSnip(
                    "${File(processedVideoPath).parent}/${File(processedVideoPath).nameWithoutExtension}-${index}.mp4",
                    dur,
                    dur,
                comingFrom)
            }
        }
        if (!swipeProcessed) {
            val startPendingProcessIntent = Intent(VideoMode.PENDING_SWIPE_ACTION)
            startPendingProcessIntent.putExtra("processedVideoPath", processedVideoPath)
            startPendingProcessIntent.putExtra("comingFrom", comingFrom)
            LocalBroadcastManager.getInstance(receivedContext!!).sendBroadcast(startPendingProcessIntent)
//            videoModeFragment.processPendingSwipes(currentOperation = comingFrom)
        }
    }

    /**
     * Video speed change was successfully done and is available at processedVideoPath
     * Processed video is added to snip
     *
     * @param processedVideoPath
     */
    private fun videoSpeedChangeCompleted(
        processedVideoPath: String,
        comingFrom: CurrentOperation,
        swipeAction: SwipeAction
    ) {
        if (VideoService.ignoreResultOf.isNotEmpty()) {
            if (VideoService.ignoreResultOf[0] == IVideoOpListener.VideoOp.SPEED) {
                VideoService.ignoreResultOf.removeAt(0)
                return
            }
        }

        Log.d(TAG, "SpeedChange Completed: Video Saved at $processedVideoPath")
//        Toast.makeText(this, "Video Saved at $processedVideoPath", Toast.LENGTH_SHORT).show()
        val duration = getMetadataDurations(arrayListOf(processedVideoPath))[0]

        if (doReplace) {
            CoroutineScope(Dispatchers.IO).launch {
                //  DB update is not required since we are just replacing the existing file with modified content
                if (fileToReplace.isNotNullOrEmpty() && replacedWith.equals(processedVideoPath)) {
                    parentSnip = appRepository.getSnipByVideoPath(fileToReplace!!)
                    parentSnip!!.snip_duration = duration.toDouble()
                    parentSnip!!.total_video_duration = duration
                    appRepository.updateSnip(parentSnip!!)

                    File(parentSnip!!.videoFilePath!!).delete()
                    File(processedVideoPath).renameTo(File(parentSnip!!.videoFilePath!!))

                    fileToReplace = null
                    replacedWith  = null
                    doReplace     = false

                    val dismissIntent = Intent(VideoEditingFragment.DISMISS_ACTION)
                    dismissIntent.putExtra("processedVideoPath", parentSnip!!.videoFilePath!!)
                    receivedContext?.sendBroadcast(dismissIntent)
//                    dismissEditFragmentProcessingDialog(parentSnip!!.videoFilePath!!)
                }
            }
        } else {
            //  IReplaceRequired.parent must be called before this point for proper functioning
            addSnip(processedVideoPath, duration, duration, comingFrom)
        }
    }

    private fun videoPreviewFramesCompleted(
        processedVideoPath: String,
        comingFrom: CurrentOperation,
        swipeAction: SwipeAction
    ) {
        if (VideoService.ignoreResultOf.isNotEmpty()) {
            if (VideoService.ignoreResultOf[0] == IVideoOpListener.VideoOp.FRAMES) {
                VideoService.ignoreResultOf.removeAt(0)
                return
            }
        }

        val previewIntent = Intent()
        previewIntent.putExtra("preview_path", processedVideoPath)
        previewIntent.action = VideoEditingFragment.PREVIEW_ACTION
        receivedContext?.sendBroadcast(previewIntent)
    }

    private fun videoFramesAdded(processedVideoPath: String, comingFrom: CurrentOperation, swipeAction: SwipeAction) {
        if (VideoService.ignoreResultOf.isNotEmpty()) {
            if (VideoService.ignoreResultOf[0] == IVideoOpListener.VideoOp.KEY_FRAMES) {
                VideoService.ignoreResultOf.removeAt(0)
                return
            }
        }

        val intent = Intent("frames_added")
        receivedContext?.sendBroadcast(intent)
    }

    /**
     * Adds the required snip with durations to the snip DB
     * Sets up snip hierarchy based on file names.
     *
     * @param snipFilePath String
     * @param snipDuration Int
     * @param totalDuration Int
     */
    private fun addSnip(snipFilePath: String, snipDuration: Int, totalDuration: Int, fromOperation: CurrentOperation) {
        Log.d(TAG, "addSnip: adding snip $snipFilePath")
        if (addedToSnip.contains(snipFilePath))  //  This is a work around till we figure out the cause of duplication
            return
        else
            addedToSnip.add(snipFilePath)
        Log.d(TAG, "addSnip: added to list ")
        val pSnip = Snip()
        pSnip.apply {
            start_time = 0.0
            end_time = 0.0
            is_virtual_version = 0
            Log.d(TAG, "addSnip: parentSnip = $parentSnip, $parentChanged")
            parent_snip_id = if (parentSnip != null) {
                if (File(snipFilePath).nameWithoutExtension.contains(File(parentSnip!!.videoFilePath).nameWithoutExtension) &&
                    (fromOperation == CurrentOperation.VIDEO_RECORDING || fromOperation == CurrentOperation.CLIP_RECORDING) ||    //  while in videoMode check with file names for parent
                    fromOperation == CurrentOperation.VIDEO_EDITING ||
                    parentChanged
                ) {    //  while in editMode check is parentSnip was already set
                    parentSnip?.snip_id!!
                } else 0
            } else 0
            snip_duration = snipDuration.toDouble()
            total_video_duration = totalDuration
            if (parentChanged && parentSnip != null) {  //  if we are coming from edit
                vid_creation_date = parentSnip!!.vid_creation_date
                event_id = parentSnip!!.event_id
            } else {
                vid_creation_date = System.currentTimeMillis()
                event_id = AppClass.getAppInstance().lastEventId
            }
            has_virtual_versions = (if (AppClass.getAppInstance().snipDurations.size > 0) 1 else 0)
            videoFilePath = snipFilePath
        }

        if (!parentChanged) {
            VideoService.bufferDetails.forEach {
                if (it.bufferPath == snipFilePath) { //  this is a buffer clip
                    return
                }
            }
        }

        AppClass.getAppInstance().isInsertionInProgress = true
        // So that the order of the videos don't change
        runBlocking {
            appRepository.insertSnip(this@VideoOperationReceiver, pSnip)
        }
    }

    /**
     * Triggered after the snip has been added,
     * Items to be displayed in the gallery are to be inserted in HR snips
     *
     * @param snip Snip?
     */
    override suspend fun onTaskCompleted(snip: Snip?) {
        if (snip?.is_virtual_version == 0) {

            val hdSnips = Hd_snips()
            hdSnips.video_path_processed = snip.videoFilePath
            hdSnips.snip_id = snip.snip_id

            val snipTitle = File(snip.videoFilePath).nameWithoutExtension

            if (snipTitle.contentEquals(File(VideoMode.getSwipedRecording().originalFilePath!!).nameWithoutExtension) &&
                !parentChanged) {
                // if the snip has the same name it is a parent
                parentSnip = snip
            }
            Log.d(TAG, "onTaskCompleted: ${parentSnip?.snip_id}")

            if (showInGallery.isPathInList(snip.videoFilePath)) {
                appRepository.insertHd_snips(hdSnips)
                saveSnipToDB(parentSnip, hdSnips.video_path_processed)
                getVideoThumbnail(snip, File(hdSnips.video_path_processed))
                showInGallery.remove(File(snip.videoFilePath).nameWithoutExtension) // house keeping
            }

            if (parentChanged)   //  resetting the parent changed flag if it was set, since at this point it must have been consumed
                parentChanged = false

            val dismissIntent = Intent(VideoEditingFragment.DISMISS_ACTION)
            dismissIntent.putExtra("processedVideoPath", snip.videoFilePath)
            receivedContext?.sendBroadcast(dismissIntent)

            //  adding the buffer video into the DB
            checkIfBufferAvailableForSnip(snip)
        }
    }

    private fun saveSnipToDB(parentSnip: Snip?, filePath: String?) {
        val snipDurations = AppClass.getAppInstance().snipDurations
        if (snipDurations.size > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                val event = AppClass.getAppInstance().getLastCreatedEvent()
                for (endSecond in snipDurations) {
                    val startSecond = (endSecond - 5).coerceAtLeast(0)
                    val snip = Snip()

                    snip.apply {
                        start_time = startSecond.toDouble()
                        end_time = endSecond.toDouble()
                        is_virtual_version = 1
                        has_virtual_versions = 0
                        parent_snip_id = parentSnip?.snip_id ?: 0
                        /*parent_snip_id = if (parentSnip != null) {
                            if (File(filePath!!).nameWithoutExtension.contains(File(parentSnip.videoFilePath).nameWithoutExtension)) {
                                parentSnip.snip_id
                            } else 0
                        } else 0*/
                        snip_duration = endSecond - startSecond.toDouble()
                        vid_creation_date = System.currentTimeMillis()
                        event_id = event.event_id
                    }
                    appRepository.insertSnip(this@VideoOperationReceiver, snip)
                    snip.videoFilePath = filePath
                }
                AppClass.getAppInstance().clearSnipDurations()
            }
        }
    }

    /**
     * Checks the bufferDetails and adds to db if available
     *
     * @param snip Snip
     */
    private suspend fun checkIfBufferAvailableForSnip(snip: Snip) {
        val bufferDetails = VideoService.bufferDetails

        bufferDetails.forEach {
            if (it.videoPath == snip.videoFilePath) {
                addToDBAsBuffer(it.bufferPath, snip)
                return@forEach
            }
        }
    }

    /**
     * Adds bufferPath to the DB under the same snip_id as the snip with the video path specified with videoSnip
     *
     * @param bufferPath String
     * @param videoSnip Snip
     */
    private suspend fun addToDBAsBuffer(bufferPath: String, videoSnip: Snip) {
        val hdSnip = Hd_snips()
        hdSnip.video_path_processed = bufferPath
        hdSnip.snip_id = videoSnip.snip_id

        appRepository.insertHd_snips(hdSnip)
    }

    /**
     *  Takes in a list of media files and returns a list of durations
     *
     *  @param List<String> filePathList
     *  @return List<Int> durations
     */
    private fun getMetadataDurations(filePathList: List<String>): List<Int> {
        val durationList = arrayListOf<Int>()
        val retriever = MediaMetadataRetriever()
        var duration: Int
        filePathList.forEach {
            try {
                retriever.setDataSource(it)
                duration = TimeUnit.MILLISECONDS.toSeconds(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
                ).toInt()
                durationList.add(duration)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                Log.e(TAG, "getMetadataDurations: data file error; setting duration to -1")
                durationList.add(-1)
            }
        }
        retriever.release()
        return durationList
    }

    /**
     * Creates the thumbnail image for the video file at the location passed in.
     *
     * @param snip Snip?
     * @param videoFile File
     */
    private fun getVideoThumbnail(snip: Snip?, videoFile: File) {
        try {
//            File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
//                    VIDEO_DIRECTORY_NAME);
            val thumbsStorageDir = File(
                "${receivedContext!!.dataDir}/$VIDEO_DIRECTORY_NAME",
                THUMBS_DIRECTORY_NAME
            )
            if (!thumbsStorageDir.exists()) {
                if (!thumbsStorageDir.mkdirs()) {
                    Log.d(
                        TAG, "Oops! Failed create "
                                + VIDEO_DIRECTORY_NAME + " directory"
                    )
                    return
                }
            }
            val fullThumbPath: File
            fullThumbPath = File(
                thumbsStorageDir.path + File.separator
                        + "snip_" + snip!!.snip_id + ".png"
            )
            Log.d(
                TAG,
                "saving video thumbnail at path: " + fullThumbPath + ", video path: " + videoFile.absolutePath
            )
            //Save the thumbnail in a PNG compressed format, and close everything. If something fails, return null
            val streamThumbnail = FileOutputStream(fullThumbPath)

            //Other method to get a thumbnail. The problem is that it doesn't allow to get at a specific time
            val thumb: Bitmap
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(videoFile.absolutePath)
                thumb = if (snip.is_virtual_version != 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            snip.start_time.toInt() * 1000000.toLong(),
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 100, 100
                        )
                    } else {
                        retriever.getFrameAtTime(
                            snip.start_time.toInt() * 1000000.toLong(),
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                    }
                } else {
                    retriever.frameAtTime
                }
                thumb.compress(Bitmap.CompressFormat.PNG, 80, streamThumbnail)
                thumb.recycle() //ensure the image is freed;
            } catch (ex: Exception) {
                Log.i(TAG, "MediaMetadataRetriever got exception:$ex")
            }

            streamThumbnail.close()
            Log.d(TAG, "thumbnail saved successfully")
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "File Not Found Exception : check directory path")
            e.printStackTrace()
        } catch (e: IOException) {
            Log.d(TAG, "IOException while closing the stream")
            e.printStackTrace()
        }
    }

}