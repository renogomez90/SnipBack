package com.hipoint.snipback

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.hipoint.snipback.Utils.CommonUtils
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.dialog.ProcessingDialog
import com.hipoint.snipback.fragment.FragmentGalleryNew
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
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

class AppMainActivity : AppCompatActivity(), VideoMode.OnTaskCompleted, AppRepository.OnTaskCompleted {
    var PERMISSION_ALL = 1
    var PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET)

    //    private static String VIDEO_DIRECTORY_NAME = "SnipBackVirtual";
    //    private static String THUMBS_DIRECTORY_NAME = "Thumbs";
    private val VIDEO_DIRECTORY_NAME = "SnipBackVirtual"
    private val THUMBS_DIRECTORY_NAME = "Thumbs"
    private val TAG = AppMainActivity.javaClass.simpleName
    private var onTouchListeners: MutableList<MyOnTouchListener>? = null
    private var appViewModel: AppViewModel? = null
    private var parentSnip: Snip? = null
    private var addedToSnip: ArrayList<String> = arrayListOf()

    var swipeProcessed: Boolean = false
    var showInGallery: ArrayList<String> = arrayListOf()    //  names of files that need to be displayed in the gallery
    private var videoModeFragment: VideoMode? = null

    private val appRepository by lazy {AppRepository(AppClass.getAppInstance())}

    //    private ArrayList<String> thumbs = new ArrayList<>();

    override fun onResume() {
        super.onResume()
        registerReceiver(videoOperationReceiver, IntentFilter(VideoService.ACTION))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.appmain_activity)
        if (onTouchListeners == null) {
            onTouchListeners = ArrayList()
        }
        appViewModel = ViewModelProviders.of(this).get(AppViewModel::class.java)

//        RegisterFragment videoMode = new RegisterFragment();
//        loadFragment(videoMode);
        if24HoursCompleted()

//        appViewModel.loadGalleryDataFromDB(this);
        if (videoModeFragment == null) {
            videoModeFragment = VideoMode.newInstance()
        }

        loadFragment(videoModeFragment, false)
        if (!hasPermissions(this, *PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA),
                    50)
        }
    }

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
        ft.replace(R.id.mainFragment, fragment!!)
        if (addtoBackStack || fragment is FragmentGalleryNew) {
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
        if (count == 0) {
            super.onBackPressed()
        } else {
            if (myFragment is FragmentGalleryNew) {
                supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            } else {
                supportFragmentManager.popBackStack()
            }
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

    fun addSnip(snipFilePath: String, snipDuration: Int, totalDuration: Int) {
        if(addedToSnip.contains(snipFilePath))  //  This is a work around till we figure out the cause of duplication
            return
        else
            addedToSnip.add(snipFilePath)

        val pSnip = Snip()
        pSnip.apply {
            start_time = 0.0
            end_time = 0.0
            is_virtual_version = 0
            parent_snip_id = if (parentSnip != null) {
                if (File(snipFilePath).nameWithoutExtension.contains(File(parentSnip!!.videoFilePath).nameWithoutExtension)) {
                    parentSnip!!.snip_id
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

    override suspend fun onTaskCompleted(snip: Snip?) {
        if (snip?.is_virtual_version == 0) {
//            snip.setSnip_id(AppClass.getAppInstance().getLastSnipId());
//            hdSnips!!.video_path_processed = snip.videoFilePath
//            hdSnips.setSnip_id(AppClass.getAppInstance().getLastSnipId());

            val hdSnips = Hd_snips()
            hdSnips.video_path_processed = snip.videoFilePath
            hdSnips.snip_id = snip.snip_id
            if (!File(snip.videoFilePath).name.contains("-")) { //  files names with - are edited from original
                parentSnip = snip
            }

            if (isInList(showInGallery, snip.videoFilePath)) {
                appRepository.insertHd_snips(hdSnips)
//                saveSnipToDB(parentSnip, hdSnips!!.video_path_processed)
                getVideoThumbnail(snip, File(hdSnips.video_path_processed))
                showInGallery.remove(File(snip.videoFilePath).nameWithoutExtension) // house keeping
            }
//            parentSnipId = AppClass.getAppInstance().lastSnipId + 1
        }
    }

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

    private fun videoTrimCompleted(processedVideoPath: String) {
        Log.d(TAG, "$processedVideoPath Completed")
        CoroutineScope(IO).launch {
            val duration = getMetadataDurations(arrayListOf(processedVideoPath))[0]
            addSnip(processedVideoPath, duration, duration)
        }
        if (!swipeProcessed) {
            videoModeFragment?.processPendingSwipes()
        }
    }

    private fun videoSplitCompleted(processedVideoPath: String) {
        Log.d(TAG, "$processedVideoPath Completed")
        CoroutineScope(IO).launch {
            val pathList = arrayListOf("$processedVideoPath-0.mp4", "$processedVideoPath-1.mp4")
            getMetadataDurations(pathList).forEachIndexed { index, dur ->
                addSnip("${File(processedVideoPath).parent}/${File(processedVideoPath).nameWithoutExtension}-${index}.mp4", dur, dur)
            }
        }
        if (!swipeProcessed) {
            videoModeFragment?.processPendingSwipes()
        }
    }

    /**
     * Concatenation is done
     * @param processedVideoPath
     */
    fun videoConcatCompleted(processedVideoPath: String) {
        val duration = getMetadataDurations(arrayListOf(processedVideoPath))[0]
        addSnip(processedVideoPath, duration, duration)     //  merged file is saved to DB

        val swipeClipDuration = videoModeFragment?.swipeValue!! / 1000
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
     * Receives events from VideoService
     * */
    private val videoOperationReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {

            val operation = intent.getStringExtra("operation")
            val showProgress = intent.getIntExtra("progress", -1)
            val processedVideoPath = intent.getStringExtra("processedVideoPath")

            /*if (showProgress == VideoService.STATUS_SHOW_PROGRESS && VideoMode.stopPressed) {
                if (processingDialog == null) processingDialog = ProcessingDialog(context)
                processingDialog?.show()
            } else {
                processingDialog?.dismiss()
            }*/

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
     *  @param List<File> filePathList
     *  @return List<Int> durations
     */
    private fun getMetadataDurations(filePathList: List<String>): List<Int> {
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
}