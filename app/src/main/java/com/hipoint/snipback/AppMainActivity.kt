package com.hipoint.snipback

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.transition.Slide
import com.hipoint.snipback.Utils.CommonUtils
import com.hipoint.snipback.Utils.isPathInList
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.application.AppClass.showInGallery
import com.hipoint.snipback.fragment.*
import com.hipoint.snipback.listener.IReplaceRequired
import com.hipoint.snipback.room.entities.Event
import com.hipoint.snipback.room.entities.Hd_snips
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.room.repository.AppRepository
import com.hipoint.snipback.room.repository.AppViewModel
import com.hipoint.snipback.service.CleanupService
import com.hipoint.snipback.service.VideoService
import kotlinx.android.synthetic.main.fragment_settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.floor


class AppMainActivity : AppCompatActivity(), VideoMode.OnTaskCompleted,
    AppRepository.OnTaskCompleted, IReplaceRequired {
    var PERMISSION_ALL = 1
    var PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.INTERNET
    )

    //    private static String VIDEO_DIRECTORY_NAME = "SnipBackVirtual";
    //    private static String THUMBS_DIRECTORY_NAME = "Thumbs";
    private val TAG = AppMainActivity::class.java.simpleName

    private val VIDEO_DIRECTORY_NAME  = "SnipBackVirtual"
    private val THUMBS_DIRECTORY_NAME = "Thumbs"

    private var onTouchListeners: MutableList<MyOnTouchListener>? = null
    private var appViewModel    : AppViewModel?                   = null
    private var addedToSnip     : ArrayList<String>               = arrayListOf()

    private lateinit var galleryLoader: ProgressBar

    private val appRepository by lazy { AppRepository(AppClass.getAppInstance()) }

    /**
     * Registers a listener for receiving service broadcast for video operation status
     */
    override fun onResume() {
        super.onResume()
        isPausing = false
//        registerReceiver(videoOperationReceiver, IntentFilter(VideoService.ACTION))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        window.decorView.systemUiVisibility = flags
        val decorView = window.decorView
        decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                decorView.systemUiVisibility = flags
            }
        }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

        setContentView(R.layout.appmain_activity)

        galleryLoader = findViewById(R.id.galleryLoader)

        if (onTouchListeners == null) {
            onTouchListeners = ArrayList()
        }
        appViewModel = ViewModelProvider(this).get(AppViewModel::class.java)

//        RegisterFragment videoMode = new RegisterFragment();
//        loadFragment(videoMode);
        if24HoursCompleted()

//        appViewModel.loadGalleryDataFromDB(this);

        if(supportFragmentManager.findFragmentByTag(VIDEO_MODE_TAG) == null) {
            loadFragment(videoModeFragment, true)
        }
        /*
        if (!hasPermissions(this, *PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 50)
        }
        */

        parentChanged = false
        virtualToReal = false

    }

    /**
     * Unregister broadcast receiver
     */
    override fun onPause() {
        isPausing = true
//        unregisterReceiver(videoOperationReceiver)
        super.onPause()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hideSystemUI(window)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideSystemUI(window)

    }

    private fun hideSystemUI(window: Window) {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }
    public fun hideOrShowProgress(visible: Boolean){
        if (visible){
            galleryLoader.visibility=View.GONE
        } else{
            galleryLoader.visibility=View.VISIBLE
        }
    }

    override fun onDestroy() {
        //  so that clutter is removed
        if(!isFragmentVisible(SNAPBACK_VIDEO_TAG) && !isFragmentVisible(SLOW_MO_TAG)) {    //  don't trigger video file clean up since it may be in use in snapback fragment
            val cleanupIntent = Intent(this, CleanupService::class.java)
            startService(cleanupIntent)
            Log.d(TAG, "onDestroy: starting clean up service")
        }
        super.onDestroy()
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
    fun loadFragment(fragment: Fragment, addToBackStack: Boolean) {
        val ft = supportFragmentManager.beginTransaction()

        val tag = when (fragment) {
            is VideoMode -> VIDEO_MODE_TAG
            is FragmentGalleryNew -> GALLERY_FRAGMENT_TAG
            is FragmentPlayVideo2 -> PLAY_VIDEO_TAG
            is VideoEditingFragment -> EDIT_VIDEO_TAG
            is QuickEditFragment -> QUICK_EDIT_TAG
            is SnapbackFragment -> SNAPBACK_VIDEO_TAG
            is FragmentSlowMo -> SLOW_MO_TAG
            else -> ""
        }

        /*ft.setCustomAnimations(
            R.anim.slide_in_left_to_right,
            R.anim.slide_out_left_to_right,
            R.anim.slide_in_right_to_left,
            R.anim.slide_out_right_to_left)*/
        if(tag == GALLERY_FRAGMENT_TAG){
            fragment.apply {
                enterTransition = Slide(Gravity.END)
                exitTransition = Slide(Gravity.START)
            }
        }else {
            ft.setCustomAnimations(
                R.anim.slide_in_left_to_right,
                R.anim.slide_out_left_to_right,
                R.anim.slide_in_right_to_left,
                R.anim.slide_out_right_to_left)
        }

        //  don't add is already present
        val count = supportFragmentManager.backStackEntryCount
        Log.d(TAG, "onBackPressed: stack count $count")
        var isAlreadyPresent = false
        for (entry in 0 until count) {
            if (supportFragmentManager.getBackStackEntryAt(entry).name == tag) {
                isAlreadyPresent = true
                supportFragmentManager.popBackStack(tag, 0)
                break
            }
        }

        if (!isAlreadyPresent) {
            ft.replace(R.id.mainFragment, fragment, tag)

            if (addToBackStack) {
                ft.addToBackStack(tag)
            }
            ft.commitAllowingStateLoss()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        for (listener in onTouchListeners!!) listener.onTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onBackPressed() {
        val myFragment = supportFragmentManager.findFragmentById(R.id.mainFragment)
        val count = supportFragmentManager.backStackEntryCount
        Log.d(TAG, "onBackPressed: stack count $count")
        for (entry in 0 until count)
            Log.d(
                TAG,
                "onBackPressed: stack item: ${supportFragmentManager.getBackStackEntryAt(entry)}"
            )

        if (videoModeFragment.isVisible || count == 1) {    //  we are at the first fragment when back was pressed. we can exit
            finish()
        }

        if (count == 0) {
            super.onBackPressed()
        } else {
            val playVideoFragment =
                supportFragmentManager.findFragmentByTag(PLAY_VIDEO_TAG) as? FragmentPlayVideo2
            val slowMoFragment =
                supportFragmentManager.findFragmentByTag(SLOW_MO_TAG) as? FragmentSlowMo
            val editFrag =
                supportFragmentManager.findFragmentByTag(EDIT_VIDEO_TAG) as? VideoEditingFragment
            val snapbackFragment =
                supportFragmentManager.findFragmentByTag(SNAPBACK_VIDEO_TAG) as? SnapbackFragment
            val gallery =
                supportFragmentManager.findFragmentByTag(GALLERY_FRAGMENT_TAG) as? FragmentGalleryNew

            if(gallery != null && gallery.isVisible){
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                supportFragmentManager.popBackStack()
            }else if (editFrag != null && editFrag.isVisible) {
                editFrag.confirmExitOnBackPressed()
            } else if (snapbackFragment != null && snapbackFragment.isVisible) {
                snapbackFragment.showSaveDialog()
            } else if (slowMoFragment != null && slowMoFragment.isVisible){
                slowMoFragment.showSaveDialog()
            } else if (playVideoFragment != null && playVideoFragment.isVisible && slowMoFragment != null){
                supportFragmentManager.popBackStack(SLOW_MO_TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
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
        var isPausing = true

        //  edit file replacement
        internal var fileToReplace: String? = null
        internal var replacedWith : String? = null
        internal var parentSnip   : Snip?   = null
        internal var doReplace    : Boolean = false
        internal var parentChanged: Boolean = false
        internal var virtualToReal: Boolean = false //  set this to true before trimming, so that additional operations may be applied on the new video eg. speed change

        const val SLOW_MO_TAG          = "slow_mo_frag"
        const val VIDEO_MODE_TAG       = "videoMode"
        const val GALLERY_FRAGMENT_TAG = "gallery_frag"
        const val PLAY_VIDEO_TAG       = "play_frag"
        const val PLAY_SNAPBACK_TAG    = "play_snapback_frag"
        const val EDIT_VIDEO_TAG       = "edit_frag"
        const val QUICK_EDIT_TAG       = "quick_edit_frag"
        const val SNAPBACK_VIDEO_TAG   = "snapback_frag"

        private val videoModeFragment: VideoMode by lazy { VideoMode.newInstance() }

        fun hasPermissions(context: Context?, vararg permissions: String?): Boolean {
            if (context != null) {
                for (permission in permissions) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            permission!!
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return false
                    }
                }
            }
            return true
        }
    }


    /**
     * To be set before converting a virtual snip to a real snip
     *
     * @param isConverting Boolean
     */
    fun setVirtualToReal(isConverting: Boolean) {
        virtualToReal = isConverting
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
        Log.d(TAG, "addSnip: adding snip $snipFilePath")
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
                    isFragmentVisible(EDIT_VIDEO_TAG) ||
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
            if (!File(snip.videoFilePath).name.contains("-") && !parentChanged ||
                VideoMode.getSwipedRecording().originalFilePath!!.contains(snip.videoFilePath)
            ) { //  files names with - are edited from original todo: This is a mess
                parentSnip = snip
            }

            if (showInGallery.isPathInList(snip.videoFilePath)) {
                appRepository.insertHd_snips(hdSnips)
                saveSnipToDB(parentSnip, hdSnips.video_path_processed)
                getVideoThumbnail(snip, File(hdSnips.video_path_processed))
                showInGallery.remove(File(snip.videoFilePath).nameWithoutExtension) // house keeping
            }

            if (parentChanged)   //  resetting the parent changed flag if it was set, since at this point it must have been consumed
                parentChanged = false

            //  restart the video playback fragment with the modified video, if we have just arrived here from saving the edit
            val editFrag =
                supportFragmentManager.findFragmentByTag(EDIT_VIDEO_TAG) as? VideoEditingFragment

            if (editFrag != null &&
                editFrag.isVisible &&
                VideoEditingFragment.saveAction != VideoEditingFragment.SaveActionType.CANCEL
            )
                dismissEditFragmentProcessingDialog(snip.videoFilePath)

//            parentSnipId = AppClass.getAppInstance().lastSnipId + 1

            //  adding the buffer video into the DB
            checkIfBufferAvailableForSnip(snip)
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

    private fun saveSnipToDB(parentSnip: Snip?, filePath: String?) {
        val snipDurations = AppClass.getAppInstance().snipDurations
        if (snipDurations.size > 0) {
            CoroutineScope(IO).launch {
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
                    appRepository.insertSnip(this@AppMainActivity, snip)
                    snip.videoFilePath = filePath
                }
                AppClass.getAppInstance().clearSnipDurations()
            }
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
            val thumbsStorageDir = File(
                "$dataDir/$VIDEO_DIRECTORY_NAME",
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
            val thumb: Bitmap //= ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(),MediaStore.Images.Thumbnails.MINI_KIND);
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
     * Dismisses the progress dialog in edit fragment if available,
     * starts the FragmentPlayVideo2 with the edited video for playback
     *
     * @param processedVideoPath String - path of edited video to be played
     */
    private fun dismissEditFragmentProcessingDialog(processedVideoPath: String?) {
        //  If EditVideoFragment is available dismiss the processing dialog
        val fm = supportFragmentManager
        val editFrag = fm.findFragmentByTag(EDIT_VIDEO_TAG) as? VideoEditingFragment

        if (editFrag != null) {
            editFrag.hideProgress()

            val playbackFragment = fm.findFragmentByTag(PLAY_VIDEO_TAG) as? FragmentPlayVideo2
            if (playbackFragment != null) {
                var index = fm.backStackEntryCount - 1

                while (fm.getBackStackEntryAt(index).name != PLAY_VIDEO_TAG) {
                    fm.popBackStack()
                    index--
                }
            }
//            fm.popBackStack()             //  now we are at the gallery and can play the modified video
            if (processedVideoPath != null) {
                CoroutineScope(IO).launch {
                    var snip: Snip? = null
                    var tries = 0
                    while (snip == null && tries < 5) {
                        delay(50)
                        snip = appRepository.getSnipByVideoPath(processedVideoPath)
                        tries++
                    }
                    withContext(Main) {
                        if (snip != null) {
                            playbackFragment?.updatePlaybackFile(snip)
                        }
                    }
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
                duration =
                    floor(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toFloat() / 1000).toInt()

                durationList.add(duration)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                Log.e(TAG, "getMetadataDurations: $it file error; setting duration to -1")
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

            while (parentSnip?.parent_snip_id != 0) { //  to find the top level parent
                parentSnip = appRepository.getSnipById(parentSnip?.parent_snip_id!!)
            }
            parentChanged = true
        }
    }
}