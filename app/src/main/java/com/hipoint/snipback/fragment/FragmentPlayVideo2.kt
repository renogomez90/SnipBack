package com.hipoint.snipback.fragment

import Jni.FFmpegCmd
import VideoHandle.OnEditorListener
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.*
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.exozet.android.core.extensions.onClick
import com.exozet.android.core.ui.custom.SwipeDistanceView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ClippingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.Utils.CommonUtils
import com.hipoint.snipback.Utils.TrimmerUtils
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.enums.CurrentOperation
import com.hipoint.snipback.listener.IVideoOpListener
import com.hipoint.snipback.room.entities.Event
import com.hipoint.snipback.room.entities.Hd_snips
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.room.repository.AppRepository
import com.hipoint.snipback.room.repository.AppViewModel
import com.hipoint.snipback.service.VideoService
import com.hipoint.snipback.videoControl.VideoOpItem
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_main2.*
import kotlinx.android.synthetic.main.exo_controls.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import net.kibotu.fastexoplayerseeker.SeekPositionEmitter
import net.kibotu.fastexoplayerseeker.seekWhenReady
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.roundToLong


class FragmentPlayVideo2 : Fragment(), AppRepository.HDSnipResult {
    private val TAG = FragmentPlayVideo2::class.java.simpleName

    private val VIDEO_DIRECTORY_NAME = "Snipback"

    private val retries = 3
    private var tries = 0
    private var seekToPoint: Long = 0
    private var whenReady: Boolean = false

    private var subscriptions: CompositeDisposable? = null

    private lateinit var mediaSource: MediaSource
    private lateinit var player: SimpleExoPlayer
    private lateinit var dataSourceFactory: DataSource.Factory
    private lateinit var defaultBandwidthMeter: DefaultBandwidthMeter
    private lateinit var appRepository: AppRepository
    private lateinit var appViewModel: AppViewModel
    private lateinit var playerView: PlayerView
    private lateinit var playBtn: ImageButton
    private lateinit var pauseBtn: ImageButton
    private lateinit var progressDurationHolder: LinearLayout
    private lateinit var playPauseHolder: FrameLayout
    private lateinit var quickEditBtn: ConstraintLayout
    private lateinit var quickEditTimeTxt: TextView
    private lateinit var seekBar: DefaultTimeBar
    private lateinit var rootView: View
    private lateinit var editBtn: TextView
    private lateinit var tag: TextView
    private lateinit var shutter: TextView
    private lateinit var share: TextView
    private lateinit var delete: TextView
    private lateinit var backArrow: RelativeLayout
    private lateinit var buttonCamera: RelativeLayout
    private lateinit var tvConvertToReal: ImageButton
    private lateinit var swipeDetector: SwipeDistanceView
    private lateinit var bottomMenu: LinearLayout

    // new
    private var event: Event? = null

    // new added
    private var snip: Snip? = null
    private var bufferHDSnip: Hd_snips? = null

    private var thumbnailExtractionStarted = false
    private var isInEditMode = false
    private var bufferDuration = -1L
    private var videoDuration = -1L
    private var maxDuration = 0L

    /**
     * To dynamically change the seek parameters so that seek appears to be more responsive
     */
    private val gestureDetector by lazy {
        GestureDetector(requireContext(), object : GestureDetector.OnGestureListener {
            override fun onDown(e: MotionEvent?): Boolean = false

            override fun onShowPress(e: MotionEvent?) = Unit

            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                if (playerView.isControllerVisible)
                    playerView.hideController()
                else
                    playerView.showController()

                return false
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
                if (e1 != null && e2 != null) {
                    val speed = (distanceX / (e2.eventTime - e1.eventTime)).absoluteValue
                    if ((speed * 100) < 1.0F) {  // slow
                        if (player.seekParameters != SeekParameters.EXACT) {
                            player.setSeekParameters(SeekParameters.EXACT)
                        }
                    } else { //  fast
                        if (player.seekParameters != SeekParameters.CLOSEST_SYNC) {
                            player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                        }
                    }

                }
                return false
            }

            override fun onLongPress(e: MotionEvent?) = Unit

            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean =
                    false
        })
    }

    override fun onResume() {
        super.onResume()
        initSetup()
        bindListeners()
        Log.d(TAG, "onResume: started")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            seekToPoint = it.getLong("KEY_PLAYER_POSITION")
            whenReady = it.getBoolean("KEY_PLAYER_PLAY_WHEN_READY")
            Log.d("seekto and whenready", "seekPoint is $seekToPoint and whenReady is $whenReady")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        rootView = inflater.inflate(R.layout.layout_play_video, container, false)
        appRepository = AppRepository(requireActivity().applicationContext)
        appViewModel = ViewModelProvider(this).get(AppViewModel::class.java)
        snip = requireArguments().getParcelable("snip")
        appViewModel.getEventByIdLiveData(snip!!.event_id).observe(viewLifecycleOwner, Observer { snipevent: Event? -> event = snipevent })

        bindViews()
        (activity as AppMainActivity?)?.hideStatusBar()

        return rootView
    }

    /**
     * Called when the fragment is no longer in use.  This is called
     * after [.onStop] and before [.onDetach].
     */
    override fun onDestroy() {
        super.onDestroy()
        if (this::player.isInitialized) {
            player.apply {
                playWhenReady = false
            }
        }
        seekToPoint = 0
        (activity as AppMainActivity?)?.showStatusBar()

    }

    /**
     * restarts the player with the updated snips.
     * To be called after an edited file has been saved
     */
    fun updatePlaybackFile(updatedSnip: Snip) {
        snip = updatedSnip
        player.release()
        initSetup()
    }

    private fun initSetup() {
        player = SimpleExoPlayer.Builder(requireContext()).build()
        playerView.player = player
        setVideoSource()

        player.apply {
            repeatMode = Player.REPEAT_MODE_OFF
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            playWhenReady = whenReady
            player.seekTo(seekToPoint)
        }

        playerView.apply {

            val orientation = requireContext().resources.configuration.orientation
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(Color.BLACK)
            controllerShowTimeoutMs = 3000
            setShutterBackgroundColor(Color.TRANSPARENT)    // removes the black screen when seeking or switching media
        }

        player.addListener(object : Player.EventListener {
            override fun onPlayerError(error: ExoPlaybackException) {
                Log.e(TAG, "onPlayerError: ${error.message}")
                error.printStackTrace()
                tries++
                if (snip?.videoFilePath.isNotNullOrEmpty() && tries < retries) {  //  retry in case of errors
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(500)
                        val frag = requireActivity().supportFragmentManager.findFragmentByTag(AppMainActivity.PLAY_VIDEO_TAG)
                        requireActivity().supportFragmentManager.beginTransaction()
                                .detach(frag!!)
                                .attach(frag)
                                .commit()
                    }
                }
            }

            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val currentTimeline = player.currentTimeline
                    maxDuration = 0L
                    for (i in 0 until currentTimeline.windowCount) {
                        val window = Timeline.Window()
                        currentTimeline.getWindow(i, window)
                        maxDuration += window.durationMs
                    }
                    if (isInEditMode) {
                        seekBar.setDuration(maxDuration)
                    }
                }
                if (playbackState == Player.STATE_ENDED && player.currentPosition >= player.duration) {
                    player.playWhenReady = false
                    whenReady = false
                }
            }
        })

        maxDuration = player.duration
        checkBufferAvailable()

        if (!VideoService.isProcessing)  //  in case we are coming from video editing there is a chance for crash
            getVideoPreviewFrames()
    }


    /**
     * set up playback video source
     */
    private fun setVideoSource() {
        defaultBandwidthMeter = DefaultBandwidthMeter.Builder(requireContext()).build()
        dataSourceFactory = DefaultDataSourceFactory(requireContext(),
                Util.getUserAgent(requireActivity(), "mediaPlayerSample"), defaultBandwidthMeter)

        mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(Uri.parse(snip!!.videoFilePath))

        if (snip!!.is_virtual_version == 1) {   // Virtual versions only play part of the media
            val clippingMediaSource = ClippingMediaSource(mediaSource,
                    TimeUnit.SECONDS.toMicros(snip!!.start_time.toLong()),
                    TimeUnit.SECONDS.toMicros(snip!!.end_time.toLong()))
            seekBar.setDuration(snip!!.snip_duration.toLong() * 1000)
            player.setMediaSource(clippingMediaSource)
        } else {
            val clippingMediaSource = ClippingMediaSource(mediaSource,
                    0,
                    TimeUnit.SECONDS.toMicros(snip!!.total_video_duration.toLong()))
            seekBar.setDuration(snip!!.total_video_duration.toLong() * 1000)
            player.setMediaSource(clippingMediaSource)
//            player.setMediaItem(MediaItem.fromUri(Uri.parse(snip!!.videoFilePath)))
        }

        player.prepare()
    }

    private fun checkBufferAvailable() {
        val videoId = snip!!.snip_id
        CoroutineScope(IO).launch {
            appRepository.getHDSnipsBySnipID(this@FragmentPlayVideo2, videoId)
            //  result for this will be available at queryResult
        }
    }

    private fun bindViews() {
        buttonCamera = rootView.findViewById(R.id.button_camera)
        backArrow = rootView.findViewById(R.id.back_arrow)
        tvConvertToReal = rootView.findViewById(R.id.tvConvertToReal)
        playerView = rootView.findViewById(R.id.player_view)
        editBtn = rootView.findViewById(R.id.edit)
        tag = rootView.findViewById(R.id.tag)
        shutter = rootView.findViewById(R.id.shutter)
        share = rootView.findViewById(R.id._button_share)
        delete = rootView.findViewById(R.id.button_delete)
        swipeDetector = rootView.findViewById(R.id.swipe_detector)
        progressDurationHolder = rootView.findViewById(R.id.progress_duration_holder)
        playPauseHolder = rootView.findViewById(R.id.play_pause_holder)
        seekBar = rootView.findViewById(R.id.exo_progress)
        playBtn = rootView.findViewById(R.id.exo_play)
        pauseBtn = rootView.findViewById(R.id.exo_pause)
        quickEditBtn = rootView.findViewById(R.id.quickEdit_button)
        quickEditTimeTxt = rootView.findViewById(R.id.quick_edit_time)
        bottomMenu = rootView.findViewById(R.id.bottom_menu)

    }

    private fun bindListeners() {
        playBtn.onClick {
            if (player.currentPosition >= player.contentDuration) {
                player.seekTo(0)
            }
            player.playWhenReady = true
            whenReady = true
        }

        pauseBtn.onClick {
            player.playWhenReady = false
            whenReady = false
        }

        tvConvertToReal.setOnClickListener { validateVideo(snip) }
        if ((if (snip != null) snip!!.is_virtual_version else 0) == 1) {
            tvConvertToReal.visibility = View.VISIBLE
        } else {
            tvConvertToReal.visibility = View.GONE
        }

        backArrow.setOnClickListener {
            player.release()
            requireActivity().onBackPressed()
        }

        buttonCamera.setOnClickListener {
//            (AppMainActivity).loadFragment(VideoMode.newInstance(),true);
            player.release()
            popToVideoMode()
        }

        quickEditBtn.setOnClickListener {
            launchQuickEdit()
        }

        tag.setOnClickListener {
            // ((AppMainActivity) requireActivity()).loadFragment(CreateTag.newInstance(), true);
        }
        shutter.setOnClickListener {

        }
        share.setOnClickListener {

        }
        delete.setOnClickListener {

        }

        editBtn.setOnClickListener {
            player.playWhenReady = false
            (activity as AppMainActivity?)!!.loadFragment(VideoEditingFragment.newInstance(snip, thumbnailExtractionStarted), true)
            thumbnailExtractionStarted = false
        }

        rootView.isFocusableInTouchMode = true
        rootView.requestFocus()
        rootView.setOnKeyListener(object : View.OnKeyListener {
            override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK && isInEditMode) {
                    restoreOriginalMedia()
                    return true
                }
                return false
            }
        })

        seekBar.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_MOVE) {
                val totalX = seekBar.width
                val seekPercent = (event.x * 100) / totalX
                val newSeekPosition = player.duration * seekPercent / 100
                player.seekTo(newSeekPosition.toLong())
            }
            return@setOnTouchListener false
        }

        initSwipeControls()
    }

    override fun onSaveInstanceState(outState: Bundle) {

        outState.apply {
            if (this@FragmentPlayVideo2::player.isInitialized) {
                putLong("KEY_PLAYER_POSITION", player.contentPosition)
            }
            putBoolean("KEY_PLAYER_PLAY_WHEN_READY", whenReady)

        }
        super.onSaveInstanceState(outState)
    }

    /**
     * start quick edit
     */
    private fun launchQuickEdit() {
        val quickEditFragment = QuickEditFragment.newInstance(bufferHDSnip?.hd_snip_id
                ?: 0, snip!!.snip_id, bufferPath, snip!!.videoFilePath)
        (activity as AppMainActivity?)!!.loadFragment(quickEditFragment, true)
    }

    /**
     * restores the original video to be played
     */
    private fun restoreOriginalMedia() {
        player.release()
        initSetup()
        initSwipeControls() //  because the player instance has changed
    }

    private fun popToVideoMode() {
        val fm = requireActivity().supportFragmentManager

        for (i in fm.backStackEntryCount - 1 downTo 0) {
            if (!fm.getBackStackEntryAt(i).name.equals(AppMainActivity.VIDEO_MODE_TAG, true))
                fm.popBackStack()
        }
    }

    private fun initSwipeControls() {
        var startScrollingSeekPosition = 0L

        swipeDetector.setOnClickListener {
            if (playerView.isControllerVisible)
                playerView.hideController()
            else
                playerView.showController()
        }

        swipeDetector.onIsScrollingChanged {
            if (it)
                startScrollingSeekPosition = player.currentPosition

        }

        val emitter = SeekPositionEmitter()
        subscriptions = CompositeDisposable()

        player.seekWhenReady(emitter)
                .subscribe({
                    Log.v(TAG, "seekTo=${it.first} isSeeking=${it.second}")
                }, { Log.e(TAG, "${it.message}") })
                .addTo(subscriptions!!)

        swipeDetector.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }

        swipeDetector.onScroll { percentX, _ ->

            val maxPercent = 0.75f
            val scaledPercent = percentX * maxPercent
            val percentOfDuration = scaledPercent * -1 * maxDuration + startScrollingSeekPosition
            // shift in position domain and ensure circularity
            /*val newSeekPosition = ((percentOfDuration + duration) % duration).roundToLong().absoluteValue*/
            var newSeekPosition = percentOfDuration.roundToLong()

            if (newSeekPosition < 0) {
                newSeekPosition = 0
            } else if (newSeekPosition > maxDuration) {
                newSeekPosition = maxDuration
            }

            player.seekTo(newSeekPosition)
//            emitter.seekFast(newSeekPosition)
        }
    }

    private fun validateVideo(snip: Snip?) {
        val destinationPath = snip!!.videoFilePath
        val mediaStorageDir = File(Environment.getExternalStorageDirectory(),
                VIDEO_DIRECTORY_NAME)
        // Create storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return
            }
        }
        val mediaFile = File(mediaStorageDir.path + File.separator
                + "VID_" + System.currentTimeMillis() + ".mp4")
        val complexCommand = arrayOf("ffmpeg", "-i", destinationPath.toString(), "-ss", TrimmerUtils.formatCSeconds(snip.start_time.toLong()),
                "-to", TrimmerUtils.formatCSeconds(snip.end_time.toLong()), "-async", "1", mediaFile.toString())
        val hud = CommonUtils.showProgressDialog(activity)
        FFmpegCmd.exec(complexCommand, 0, object : OnEditorListener {
            override fun onSuccess() {
                snip.is_virtual_version = 0
                snip.videoFilePath = mediaFile.absolutePath
                AppClass.getAppInstance().setEventSnipsFromDb(event, snip)
                CoroutineScope(IO).launch { appRepository.updateSnip(snip) }
                val hdSnips = Hd_snips()
                hdSnips.video_path_processed = mediaFile.absolutePath
                hdSnips.snip_id = snip.snip_id

                CoroutineScope(IO).launch { appRepository.insertHd_snips(hdSnips) }
                AppClass.getAppInstance().isInsertionInProgress = true
                if (hud.isShowing) hud.dismiss()
                requireActivity().runOnUiThread { Toast.makeText(activity, "Video saved to gallery", Toast.LENGTH_SHORT).show() }

//                appViewModel.loadGalleryDataFromDB(ActivityPlayVideo.this);
            }

            override fun onFailure() {
                if (hud.isShowing) hud.dismiss()
                requireActivity().runOnUiThread { Toast.makeText(activity, "Failed to trim", Toast.LENGTH_SHORT).show() }
            }

            override fun onProgress(progress: Float) {}
        })
    }

    companion object {
        var fragment: FragmentPlayVideo2? = null

        private var currentPos = 0L
        private var bufferPath = ""
        private var bufferAvailable = false

        @JvmStatic
        fun newInstance(snip: Snip?): FragmentPlayVideo2 {
            //  we need to create new fragments for each video
            // otherwise the smooth scrolling is having issues for some reason
            if (fragment == null)
                fragment = FragmentPlayVideo2()

            val bundle = Bundle()
            bundle.putParcelable("snip", snip)
            fragment!!.arguments = bundle
            return fragment!!
        }
    }

    override fun onPause() {
        Log.d(TAG, "onPause: started")

        player.playWhenReady = false
        currentPos = player.currentPosition

        if (this::player.isInitialized) {
            player.apply {
                stop()
                setVideoSurface(null)
                release()
            }
        }

        subscriptions?.dispose()
        super.onPause()
    }

    /**
     * Populates preview frames in the seekBar area from the video
     */
    private fun getVideoPreviewFrames() {
        val intentService = Intent(requireContext(), VideoService::class.java)
        val task = arrayListOf(VideoOpItem(
                operation = IVideoOpListener.VideoOp.FRAMES,
                clips = arrayListOf(snip!!.videoFilePath),
                outputPath = File(snip!!.videoFilePath).parent!!,
                comingFrom = CurrentOperation.VIDEO_EDITING))
        intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
        VideoService.enqueueWork(requireContext(), intentService)
        thumbnailExtractionStarted = true
    }

    override suspend fun queryResult(hdSnips: List<Hd_snips>?) {
        hdSnips?.let {
            if (it.size < 2) {
                bufferAvailable = false
                withContext(Main) { quickEditBtn.visibility = View.INVISIBLE }
                return@let
            }

            val sorted = it.sortedBy { hdSnips -> hdSnips.video_path_processed.toLowerCase() }

            sorted.forEach { item -> Log.d(TAG, "queryResult: ${item.video_path_processed}") }

            if (sorted[0].video_path_processed == sorted[1].video_path_processed) {       //  there is no buffer
                bufferAvailable = false
                withContext(Main) { quickEditBtn.visibility = View.INVISIBLE }
                return@let
            } else {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(sorted[0].video_path_processed)
                    bufferDuration =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    .toLong()
                    retriever.setDataSource(snip!!.videoFilePath)
                    videoDuration =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    .toLong()
                    retriever.release()

                    if (bufferDuration > 500L && videoDuration > 0L) {  //  if the buffer is over 100 milli seconds then show the quick edit button
                        withContext(Main) {
                            quickEditBtn.visibility = View.VISIBLE
                            quickEditTimeTxt.text =
                                    "-${(bufferDuration.toFloat() / 1000).roundToInt()} s"
                        }
                        bufferAvailable = true
                        bufferPath = sorted[0].video_path_processed
                        bufferHDSnip = sorted[0]
//                        addToVideoPlayback(sorted[0].video_path_processed)
                    } else {
                        bufferAvailable = false
                        withContext(Main) { quickEditBtn.visibility = View.INVISIBLE }
                    }

                } catch (e: IllegalArgumentException) {
                    bufferAvailable = false
                    withContext(Main) { quickEditBtn.visibility = View.INVISIBLE }
                    e.printStackTrace()
                }
            }
        }
    }
}