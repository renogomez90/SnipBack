package com.hipoint.snipback

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.hipoint.snipback.Utils.CommonUtils
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.fragment.FragmentGalleryNew
import com.hipoint.snipback.fragment.FragmentPlayVideo2
import com.hipoint.snipback.fragment.VideoEditingFragment
import com.hipoint.snipback.fragment.VideoMode
import com.hipoint.snipback.listener.IReplaceRequired
import com.hipoint.snipback.listener.IVideoOpListener
import com.hipoint.snipback.room.entities.Event
import com.hipoint.snipback.room.entities.Hd_snips
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.room.repository.AppRepository
import com.hipoint.snipback.room.repository.AppViewModel
import com.hipoint.snipback.videoControl.VideoOpItem
import com.hipoint.snipback.videoControl.VideoService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class AppMainActivity : AppCompatActivity(), VideoMode.OnTaskCompleted, AppRepository.OnTaskCompleted, IReplaceRequired {
    var PERMISSION_ALL = 1
    var PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET)

    //    private static String VIDEO_DIRECTORY_NAME = "SnipBackVirtual";
    //    private static String THUMBS_DIRECTORY_NAME = "Thumbs";
    private val TAG = AppMainActivity::class.java.simpleName

    private val VIDEO_DIRECTORY_NAME  = "SnipBackVirtual"
    private val THUMBS_DIRECTORY_NAME = "Thumbs"

    private val videoModeFragment: VideoMode by lazy { VideoMode.newInstance() }

    private var onTouchListeners: MutableList<MyOnTouchListener>? = null
    private var appViewModel    : AppViewModel?                   = null
    private var parentSnip      : Snip?                           = null
    private var addedToSnip     : ArrayList<String>               = arrayListOf()

    var swipeProcessed: Boolean = false
    var showInGallery: ArrayList<String> = arrayListOf() //  names of files that need to be displayed in the gallery

    private val appRepository by lazy { AppRepository(AppClass.getAppInstance()) }

    //  edit file replacement
    private var fileToReplace: String? = null
    private var replacedWith : String? = null
    private var doReplace    : Boolean = false
    private var parentChanged: Boolean = false

    /**
     * Registers a listener for receiving service broadcast for video operation status
     */
    override fun onResume() {
        super.onResume()
        registerReceiver(videoOperationReceiver, IntentFilter(VideoService.ACTION))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        setContentView(R.layout.appmain_activity)
        if (onTouchListeners == null) {
            onTouchListeners = ArrayList()
        }
        appViewModel = ViewModelProvider(this).get(AppViewModel::class.java)

//        RegisterFragment videoMode = new RegisterFragment();
//        loadFragment(videoMode);
        if24HoursCompleted()

//        appViewModel.loadGalleryDataFromDB(this);

        if (!hasPermissions(this, *PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 50)
        }

        if (!videoModeFragment.isAdded && supportFragmentManager.findFragmentByTag(VIDEO_MODE_TAG) == null) {
            loadFragment(videoModeFragment, false)
        }
    }

    /**
     * Unregister broadcast receiver
     */
    override fun onPause() {
        unregisterReceiver(videoOperationReceiver)
        super.onPause()
    }

    fun registerMyOnTouchListener(listener: MyOnTouchListener) {
        onTouchListeners!!.add(listener)
    }

    private fun addDailyEvent() {
        val sdf = SimpleDateFormat("dd MMM yyyy")
        val currentDateandTime = sdf.format(Date())
        val event = Event()
        event.event_title = CommonUtils.today() + ", " + currentDateandTime
        event.event_created = System.currentTimeMillis()
        CoroutineScope(IO).launch {
            appRepository.insertEvent(event)
        }
    }

    private fun if24HoursCompleted() {
        val sdf = SimpleDateFormat("dd MMM yyyy")
        val currentDateandTime = sdf.format(Date())
        appViewModel!!.eventLiveData.observe(this, Observer { events: List<Event>? ->
            if (events != null && events.isNotEmpty()) {
                val lastEvent = events[events.size - 1]
                if (lastEvent.event_title != CommonUtils.today() + ", " + currentDateandTime) {
                    addDailyEvent()
                }
                //                long diff = System.currentTimeMillis() - lastEvent.getEvent_created();
////                long seconds = diff / 1000;
////                long minutes = diff / 1000 / 60;
//                long hours = diff / 1000 / 60 / 60;
//                if (hours >= 8) {
//                    addDailyEvent();
//                }
            } else {
                addDailyEvent()
            }
        })
    }

    //    public void loadFragment(Fragment fragment,boolean addtoBackStack) {
    fun loadFragment(fragment: Fragment?, addtoBackStack: Boolean) {
        val ft = supportFragmentManager.beginTransaction()

        val tag = when (fragment) {
            is VideoMode -> VIDEO_MODE_TAG
            is FragmentGalleryNew -> GALLERY_FRAGMENT_TAG
            is FragmentPlayVideo2 -> PLAY_VIDEO_TAG
            is VideoEditingFragment -> EDIT_VIDEO_TAG
            else -> ""
        }

        ft.replace(R.id.mainFragment, fragment!!, tag)

        if (addtoBackStack/* || fragment is FragmentGalleryNew*/) {
            ft.addToBackStack(null)
        }
        ft.commitAllowingStateLoss()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        for (listener in onTouchListeners!!) listener.onTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onBackPressed() {
        val myFragment = supportFragmentManager.findFragmentById(R.id.mainFragment)
        val count = supportFragmentManager.backStackEntryCount
        Log.d(TAG, "onBackPressed: stack count $count")
        for(entry in 0 until count)
            Log.d(TAG, "onBackPressed: stack item: ${supportFragmentManager.getBackStackEntryAt(entry)}")

        if (count == 0) {
            super.onBackPressed()
        } else {
//            if (myFragment is FragmentGalleryNew) {
//                supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
//            } else {
            supportFragmentManager.popBackStack()
//            }
        }
    }

    override fun onTaskCompleted(success: Boolean) {
        if (success) {
            val myFragment = supportFragmentManager.findFragmentById(R.id.mainFragment)
            if (myFragment is FragmentGalleryNew) {
                myFragment.onLoadingCompleted(success)
            }
        }
    }

    interface MyOnTouchListener {
        fun onTouch(ev: MotionEvent?)
    }

    companion object {
        const val VIDEO_MODE_TAG = "videoMode"
        const val GALLERY_FRAGMENT_TAG = "gallery_frag"
        const val PLAY_VIDEO_TAG = "play_frag"
        const val EDIT_VIDEO_TAG = "edit_frag"

        fun hasPermissions(context: Context?, vararg permissions: String?): Boolean {
            if (context != null) {
                for (permission in permissions) {
                    if (ActivityCompat.checkSelfPermission(context, permission!!) != PackageManager.PERMISSION_GRANTED) {
                        return false
                    }
                }
            }
            return true
        }
    }

    /**
     * Adds the required snip with durations to the snip DB
     * Sets up snip hierarchy based on file names.
     *
     * @param snipFilePath String
     * @param snipDuration Int
     * @param totalDuration Int
     */
    fun addSnip(snipFilePath: String, snipDuration: Int, totalDuration: Int) {
        if (addedToSnip.contains(snipFilePath))  //  This is a work around till we figure out the cause of duplication
            return
        else
            addedToSnip.add(snipFilePath)

        val pSnip = Snip()
        pSnip.apply {
            start_time = 0.0
            end_time = 0.0
            is_virtual_version = 0
            parent_snip_id = if (parentSnip != null) {
                if (File(snipFilePath).nameWithoutExtension.contains(File(parentSnip!!.videoFilePath).nameWithoutExtension) &&
                        isFragmentVisible(VIDEO_MODE_TAG) ||    //  while in videoMode check with file names for parent
                        isFragmentVisible(EDIT_VIDEO_TAG)) {    //  while in editMode check is parentSnip was already set
                    parentSnip?.snip_id!!
                } else 0
            } else 0
            snip_duration = snipDuration.toDouble()
            total_video_duration = totalDuration
            vid_creation_date = System.currentTimeMillis()
            event_id = AppClass.getAppInstance().lastEventId
            has_virtual_versions = (if (AppClass.getAppInstance().snipDurations.size > 0) 1 else 0)
            videoFilePath = snipFilePath
        }
        AppClass.getAppInstance().isInsertionInProgress = true
        // So that the order of the videos don't change
        runBlocking {
            appRepository.insertSnip(this@AppMainActivity, pSnip)
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
//            snip.setSnip_id(AppClass.getAppInstance().getLastSnipId());
//            hdSnips!!.video_path_processed = snip.videoFilePath
//            hdSnips.setSnip_id(AppClass.getAppInstance().getLastSnipId());

            val hdSnips = Hd_snips()
            hdSnips.video_path_processed = snip.videoFilePath
            hdSnips.snip_id = snip.snip_id
            if (!File(snip.videoFilePath).name.contains("-") && !parentChanged) { //  files names with - are edited from original todo: This is a mess
                parentSnip = snip
            }

            if (isInList(showInGallery, snip.videoFilePath)) {
                appRepository.insertHd_snips(hdSnips)
                saveSnipToDB(parentSnip, hdSnips.video_path_processed)
                getVideoThumbnail(snip, File(hdSnips.video_path_processed))
                showInGallery.remove(File(snip.videoFilePath).nameWithoutExtension) // house keeping
            }
//            parentSnipId = AppClass.getAppInstance().lastSnipId + 1
        }
    }

    private fun saveSnipToDB(parentSnip: Snip?, filePath: String?) {
        val snipDurations = AppClass.getAppInstance().snipDurations
        if (snipDurations.size > 0) {
            val event = AppClass.getAppInstance().getLastCreatedEvent()
            for (endSecond in snipDurations) {
                val startSecond = (endSecond - 5).coerceAtLeast(0)
                val snip = Snip()

                snip.apply {
                    start_time = startSecond.toDouble()
                    end_time = endSecond.toDouble()
                    is_virtual_version = 1
                    has_virtual_versions = 0
                    parent_snip_id = if (parentSnip != null) {
                        if (File(filePath!!).nameWithoutExtension.contains(File(parentSnip.videoFilePath).nameWithoutExtension)) {
                            parentSnip.snip_id
                        } else 0
                    } else 0
                    snip_duration = endSecond - startSecond.toDouble()
                    vid_creation_date = System.currentTimeMillis()
                    event_id = event.event_id
                }
                CoroutineScope(IO).launch {
                    appRepository.insertSnip(this@AppMainActivity, snip)
                }
                snip.videoFilePath = filePath
            }
            AppClass.getAppInstance().clearSnipDurations()
        }
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
            val thumbsStorageDir = File("$dataDir/$VIDEO_DIRECTORY_NAME",
                    THUMBS_DIRECTORY_NAME)
            if (!thumbsStorageDir.exists()) {
                if (!thumbsStorageDir.mkdirs()) {
                    Log.d(TAG, "Oops! Failed create "
                            + VIDEO_DIRECTORY_NAME + " directory")
                    return
                }
            }
            val fullThumbPath: File
            fullThumbPath = File(thumbsStorageDir.path + File.separator
                    + "snip_" + snip!!.snip_id + ".png")
            Log.d(TAG, "saving video thumbnail at path: " + fullThumbPath + ", video path: " + videoFile.absolutePath)
            //Save the thumbnail in a PNG compressed format, and close everything. If something fails, return null
            val streamThumbnail = FileOutputStream(fullThumbPath)

            //Other method to get a thumbnail. The problem is that it doesn't allow to get at a specific time
            val thumb: Bitmap //= ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(),MediaStore.Images.Thumbnails.MINI_KIND);
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(videoFile.absolutePath)
                thumb = if (snip.is_virtual_version != 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(snip.start_time.toInt() * 1000000.toLong(),
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 100, 100)
                    } else {
                        retriever.getFrameAtTime(snip.start_time.toInt() * 1000000.toLong(),
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
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
            //            snip.setThumbnailPath(fullThumbPath.getAbsolutePath());
            //update Snip
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd");
//            String currentDateandTime = sdf.format(new Date());
//            EventData eventData = new EventData();
//            eventData.setEvent_id(AppClass.getAppInsatnce().getLastEventId());
//            eventData.setEvent_title(currentDateandTime);
//            eventData.addEventSnip(snip);
//            AppClass.getAppInsatnce().saveAllEventSnips(snip);
//            if(isLast){
//                AppClass.getAppInsatnce().setInsertionInProgress(false);
//                thumbnailProcesingCompleted.onTaskCompleted(true);
//            }
            Log.d(TAG, "thumbnail saved successfully")
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "File Not Found Exception : check directory path")
            e.printStackTrace()
        } catch (e: IOException) {
            Log.d(TAG, "IOException while closing the stream")
            e.printStackTrace()
        }
    }

    /**
     * Checks if the required item is available in the list
     *
     * @param listOfPaths ArrayList<String>
     * @param filePath String?
     * @return Boolean
     */
    private fun isInList(listOfPaths: ArrayList<String>, filePath: String?): Boolean {
        var isInList = false
        listOfPaths.forEach {
            if (File(it).nameWithoutExtension == File(filePath!!).nameWithoutExtension)
                isInList = true
        }
        return isInList
    }

    /**
     * Video was successfully trimmed and is available at processedVideoPath
     *
     * @param processedVideoPath String
     */
    private fun videoTrimCompleted(processedVideoPath: String) {
        Log.d(TAG, "$processedVideoPath Completed")
        CoroutineScope(IO).launch {
            val duration = getMetadataDurations(arrayListOf(processedVideoPath))[0]
            addSnip(processedVideoPath, duration, duration)
        }
        if (!swipeProcessed) {
            videoModeFragment.processPendingSwipes()
        }
    }

    /**
     * Video was successfully split and is available at processedVideoPath
     * Processed video is added to snip
     *
     * @param processedVideoPath String
     */
    private fun videoSplitCompleted(processedVideoPath: String) {
        Log.d(TAG, "$processedVideoPath Completed")
        CoroutineScope(IO).launch {
            val pathList = arrayListOf("$processedVideoPath-0.mp4", "$processedVideoPath-1.mp4")
            getMetadataDurations(pathList).forEachIndexed { index, dur ->
                addSnip("${File(processedVideoPath).parent}/${File(processedVideoPath).nameWithoutExtension}-${index}.mp4", dur, dur)
            }
        }
        if (!swipeProcessed) {
            videoModeFragment.processPendingSwipes()
        }
    }

    /**
     * Video concatenation was successfully done and is available at processedVideoPath
     * Processed video is added to snip
     *
     * @param processedVideoPath
     */
    fun videoConcatCompleted(processedVideoPath: String) {
        val duration = getMetadataDurations(arrayListOf(processedVideoPath))[0]
        addSnip(processedVideoPath, duration, duration)     //  merged file is saved to DB

        val swipeClipDuration = videoModeFragment.swipeValue / 1000
        if (VideoMode.recordClips) {  //  concat was triggered when automatic capture was ongoing

            val intentService = Intent(this, VideoService::class.java)
            val split2File = "${File(processedVideoPath).parent}/${File(processedVideoPath).nameWithoutExtension}-1.mp4"
            val task = arrayListOf(VideoOpItem(
                    operation = IVideoOpListener.VideoOp.TRIMMED,
                    clip1 = processedVideoPath,
                    clip2 = "",
                    startTime = (duration - swipeClipDuration).toInt(),
                    endTime = duration,
                    outputPath = split2File))
            intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
            VideoService.enqueueWork(this, intentService)

        } else {    // concat was triggered when user recording was completed
            /*
             hdSnips!!.video_path_unprocessed = processedVideoPath
           hdSnips!!.hd_snip_id = AppClass.getAppInstance().lastHDSnipId.toInt()

           val splitTime = if (swipedFileNames.contains(File(processedVideoPath).nameWithoutExtension)) {
               (totalDuration[0] - swipeClipDuration).toInt()
           } else {
               totalDuration[0] - userRecordDuration
           }

           CoroutineScope(IO).launch {
               appRepository!!.updateHDSnip(hdSnips!!)
           }

           val intentService = Intent(requireContext(), VideoService::class.java)  //  todo: changed now
           val task = arrayListOf(VideoOpItem(
                   operation = VideoOp.TRIMMED,
                   clip1 = processedVideoPath,
                   clip2 = "",
                   startTime = splitTime,
                   endTime = totalDuration[0],
                   outputPath = File(processedVideoPath).parent!!))
           intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
           startForegroundService(requireContext(), intentService)
           */
        }
    }

    /**
     * Video speed change was successfully done and is available at processedVideoPath
     * Processed video is added to snip
     *
     * @param processedVideoPath
     */
    private fun videoSpeedChangeCompleted(processedVideoPath: String) {
        Log.d(TAG, "videoSpeedChangeCompleted: Video Saved at $processedVideoPath")
        Toast.makeText(this, "Video Saved at $processedVideoPath", Toast.LENGTH_SHORT).show()
        val duration = getMetadataDurations(arrayListOf(processedVideoPath))[0]
        if(doReplace){
            /*
            * todo: find the original snip, update the durations, delete the old video, rename the new video to old video
            */
            CoroutineScope(IO).launch{
                if(fileToReplace.isNotNullOrEmpty() && replacedWith.equals(processedVideoPath)) {
                    parentSnip = appRepository.getSnipByVideoPath(fileToReplace!!)
                    parentSnip?.snip_duration = duration.toDouble()
                    parentSnip?.total_video_duration = duration
                    appRepository.updateSnip(parentSnip!!)

                    File(parentSnip?.videoFilePath!!).delete()
                    File(processedVideoPath).renameTo(File(parentSnip?.videoFilePath!!))

                    fileToReplace = null
                    replacedWith = null
                }
            }
        }else{
            //  IReplaceRequired.parent must be called before this point
            addSnip(processedVideoPath, duration, duration)
        }
    }

    /**
     * Receives events from VideoService
     * */
    private val videoOperationReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {

            val operation = intent.getStringExtra("operation")
            val showProgress = intent.getIntExtra("progress", -1)
            val processedVideoPath = intent.getStringExtra("processedVideoPath")

            if (isFragmentVisible(VIDEO_MODE_TAG))
                if (showProgress == VideoService.STATUS_SHOW_PROGRESS) {
                    videoModeFragment.videoProcessing(true)
                } else {
                    videoModeFragment.videoProcessing(false)
                }
            if(isFragmentVisible(EDIT_VIDEO_TAG))
                if(showProgress == VideoService.STATUS_SHOW_PROGRESS){
//                    (supportFragmentManager.findFragmentByTag(EDIT_VIDEO_TAG) as VideoEditingFragment).showProgress()
                } else{
//                    (supportFragmentManager.findFragmentByTag(EDIT_VIDEO_TAG) as VideoEditingFragment).hideProgress()
                }

            when (intent.getIntExtra("status", VideoService.STATUS_NO_VALUE)) {
                VideoService.STATUS_OP_SUCCESS -> {
                    when (operation) {
                        IVideoOpListener.VideoOp.CONCAT.name -> {
                            if (processedVideoPath!!.isNotBlank())
                                videoConcatCompleted(processedVideoPath)
                        }
                        IVideoOpListener.VideoOp.TRIMMED.name -> {
                            if (processedVideoPath!!.isNotBlank())
                                videoTrimCompleted(processedVideoPath)
                        }
                        IVideoOpListener.VideoOp.SPLIT.name -> {
                            if (processedVideoPath!!.isNotBlank())
                                videoSplitCompleted(processedVideoPath)
                        }
                        IVideoOpListener.VideoOp.SPEED.name -> {
                            if (processedVideoPath!!.isNotBlank())
                                videoSpeedChangeCompleted(processedVideoPath)
                        }
                        else -> {
                        }
                    }
                }
                VideoService.STATUS_OP_FAILED -> {
                    Log.e(TAG, "onReceive: $operation failed")
                }
                else -> {
                }
            }
        }
    }

    /**
     *  Takes in a list of media files and returns a list of durations
     *
     *  @param List<String> filePathList
     *  @return List<Int> durations
     */
    fun getMetadataDurations(filePathList: List<String>): List<Int> {
        val durationList = arrayListOf<Int>()
        val retriever = MediaMetadataRetriever()
        var duration: Int
        filePathList.forEach {
            try {
                retriever.setDataSource(it)
                duration = TimeUnit.MILLISECONDS.toSeconds(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()).toInt()
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

    private fun isFragmentVisible(fragmentTag: String): Boolean {
        val frag = supportFragmentManager.findFragmentByTag(fragmentTag)
        return frag?.isVisible ?: false
    }

    override fun replace(oldFilePath: String, newFilePath: String) {
        fileToReplace = oldFilePath
        replacedWith = newFilePath
        doReplace = true
    }

    override fun parent(parentSnipId: Int) {
        CoroutineScope(IO).launch {
            parentSnip = appRepository.getSnipById(parentSnipId)
            parentChanged = true
        }
    }
}