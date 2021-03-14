package com.hipoint.snipback.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.*
import android.graphics.drawable.VectorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.exozet.android.core.ui.custom.SwipeDistanceView
import com.exozet.android.core.utils.MathExtensions
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ClippingMediaSource
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.RangeSeekbarCustom
import com.hipoint.snipback.Utils.SnipbackTimeBar
import com.hipoint.snipback.adapter.TimelinePreviewAdapter
import com.hipoint.snipback.dialog.ProcessingDialog
import com.hipoint.snipback.enums.CurrentOperation
import com.hipoint.snipback.enums.EditSeekControl
import com.hipoint.snipback.listener.IVideoOpListener
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.service.VideoService
import com.hipoint.snipback.videoControl.VideoOpItem
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.coroutines.*
import net.kibotu.fastexoplayerseeker.SeekPositionEmitter
import net.kibotu.fastexoplayerseeker.seekWhenReady
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.roundToLong


class FragmentSlowMo : Fragment()  {
    private val TAG = FragmentSlowMo::class.java.simpleName
    private val PROCESSING_DIALOG = "dialog_processing"

    private lateinit var rootView          : View
    private lateinit var player            : SimpleExoPlayer
    private lateinit var playerView        : PlayerView
    private lateinit var start             : TextView
    private lateinit var end               : TextView
    private lateinit var editBackBtn       : ImageView
    private lateinit var acceptBtn         : ImageView
    private lateinit var rejectBtn         : ImageView
    private lateinit var acceptRejectHolder: LinearLayout
    private lateinit var previewBarProgress: ProgressBar
    private lateinit var previewTileList   : RecyclerView
    private lateinit var swipeDetector     : SwipeDistanceView
    private lateinit var timebarHolder     : FrameLayout
    private lateinit var seekbar           : SnipbackTimeBar

    //  preview thumbnail
    private var previewThumbs: File?   = null
    //  dialogs
    private var processingDialog: ProcessingDialog? = null
    //  adapters
    private var timelinePreviewAdapter: TimelinePreviewAdapter? = null
    // file paths
    private var bufferHdSnipId: Int     = 0
    private var videoSnipId   : Int     = 0
    // fast seeking
    private var subscriptions: CompositeDisposable? = null
    //  retires on failure
    private val retries = 3
    private var tries   = 0

    private var bufferDuration: Long = -1L
    private var videoDuration : Long = -1L
    private var maxDuration   : Long = -1L

    private val trimSegment  : RangeSeekbarCustom by lazy { RangeSeekbarCustom(requireContext()) }
    private val bufferOverlay: RangeSeekbarCustom by lazy { RangeSeekbarCustom(requireContext()) }

    private val previewTileReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                previewThumbs = File(intent.getStringExtra("preview_path")!!)
                showThumbnailsIfAvailable(previewThumbs!!)
            }
        }
    }

    private val progressDismissReceiver: BroadcastReceiver = object : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {

                bufferPath = intent.getStringExtra("bufferPath")
                videoPath = intent.getStringExtra("processedVideoPath")

                hideProgress()
                setupPlayer()
            }
        }
    }

    private fun showProgress(){
        if(processingDialog == null)
            processingDialog = ProcessingDialog()
        processingDialog!!.isCancelable = true
        processingDialog!!.show(requireActivity().supportFragmentManager, PROCESSING_DIALOG)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun hideProgress(){
        processingDialog?.dismiss()
    }

    private fun showThumbnailsIfAvailable(previewThumbs: File) {
        if (previewThumbs.exists()) {
            val thumbList = previewThumbs.listFiles()
            val imageList = arrayListOf<Bitmap>()
            thumbList?.forEach {
                imageList.add(BitmapFactory.decodeFile(it.absolutePath))
            }
            timelinePreviewAdapter = TimelinePreviewAdapter(requireContext(), imageList)
            previewTileList.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            //  change context for updating the UI
            timelinePreviewAdapter!!.setHasStableIds(true)
            previewTileList.adapter = timelinePreviewAdapter
            previewTileList.adapter?.notifyDataSetChanged()
            previewTileList.scrollToPosition(timelinePreviewAdapter!!.itemCount)
            previewBarProgress.visibility = View.GONE
        } else {
            getVideoPreviewFrames()
        }
    }
    /**
     * To dynamically change the seek parameters so that seek appears to be more responsive
     */
    private val gestureDetector by lazy {
        GestureDetector(requireContext(), object : GestureDetector.OnGestureListener {
            override fun onDown(e: MotionEvent?): Boolean = false

            override fun onShowPress(e: MotionEvent?) = Unit

            override fun onSingleTapUp(e: MotionEvent?): Boolean = false

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

    /**
     * Populates preview frames in the seekBar area from the video
     */
    private fun getVideoPreviewFrames() {
        val intentService = Intent(requireContext(), VideoService::class.java)
        val task = arrayListOf(VideoOpItem(
            operation = IVideoOpListener.VideoOp.FRAMES,
            clips = arrayListOf(videoPath!!),
            outputPath = File(videoPath!!).parent!!,
            comingFrom = CurrentOperation.VIDEO_EDITING))
        intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
        VideoService.enqueueWork(requireContext(), intentService)
    }

    companion object {
        private var fragment: FragmentSlowMo? = null

        private var bufferPath    : String? = null
        private var videoPath     : String? = null

        @JvmStatic
        fun newInstance(buffer: String?, video: String?): FragmentSlowMo {
            if (fragment == null) {
                fragment = FragmentSlowMo()
            }

            val bundle = Bundle()
            bundle.putString("bufferPath", buffer)
            bundle.putString("videoPath", video)
            fragment!!.arguments = bundle

            return fragment!!
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {

        }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
    ): View? {
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        rootView = inflater.inflate(R.layout.fragment_slo_mo, container, false)

        bindViews()
        bindListeners()

        return rootView
    }

    override fun onResume() {
        super.onResume()
        requireActivity().registerReceiver(previewTileReceiver, IntentFilter(VideoEditingFragment.PREVIEW_ACTION))
        requireActivity().registerReceiver(progressDismissReceiver, IntentFilter(VideoEditingFragment.DISMISS_ACTION))

        bufferPath = arguments?.getString("bufferPath")
        videoPath = arguments?.getString("videoPath")

        if(videoPath.isNullOrEmpty()) {
            showProgress()
        } else {
            setupPlayer()
        }
    }

    override fun onPause() {
        requireActivity().unregisterReceiver(previewTileReceiver)
        requireActivity().unregisterReceiver(progressDismissReceiver)
        super.onPause()
    }

    private fun bindViews() {
        playerView         = rootView.findViewById(R.id.player_view)
        start              = rootView.findViewById(R.id.start)
        end                = rootView.findViewById(R.id.end)
        editBackBtn        = rootView.findViewById(R.id.back_arrow)
        acceptBtn          = rootView.findViewById(R.id.accept)
        rejectBtn          = rootView.findViewById(R.id.reject)
        acceptRejectHolder = rootView.findViewById(R.id.accept_reject_holder)
        swipeDetector      = rootView.findViewById(R.id.swipe_detector)
        timebarHolder      = rootView.findViewById(R.id.timebar_holder)
        previewTileList    = rootView.findViewById(R.id.previewFrameList)
        previewBarProgress = rootView.findViewById(R.id.previewBarProgress)
        seekbar            = rootView.findViewById(R.id.exo_progress)
    }

    private fun setupPlayer(){
        player = SimpleExoPlayer.Builder(requireContext()).build()
        playerView.player = player
        setupMediaSource()
        playerView.setShowMultiWindowTimeBar(true)
        maxDuration = bufferDuration + videoDuration
        seekbar.hideScrubber()

        player.apply {
            repeatMode = Player.REPEAT_MODE_OFF
            setSeekParameters(SeekParameters.EXACT)
            playWhenReady = false
        }

        playerView.apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            controllerShowTimeoutMs = 2000
            setBackgroundColor(Color.BLACK)
            setShutterBackgroundColor(Color.TRANSPARENT)    // removes the black screen when seeking or switching media
            showController()
        }

        player.addListener(object : Player.EventListener {
            override fun onPlayerError(error: ExoPlaybackException) {
                Log.e(TAG, "onPlayerError: ${error.message}")
                error.printStackTrace()
                tries++
                if (videoPath.isNotNullOrEmpty() && tries < retries) {  //  retry in case of errors
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(500)
                        val frag = requireActivity().supportFragmentManager.findFragmentByTag(
                            AppMainActivity.PLAY_VIDEO_TAG)
                        requireActivity().supportFragmentManager.beginTransaction()
                            .detach(frag!!)
                            .attach(frag)
                            .commit()
                    }
                }
            }
        })
        initSwipeControls()
        previewThumbs = File("${ File(videoPath).parent}/previewThumbs")
        if (previewThumbs != null) {
            showThumbnailsIfAvailable(previewThumbs!!)
        }
        prepareForEdit()
    }

    private fun prepareForEdit() {

    }

    private fun bindListeners() {
        start.setOnClickListener {

        }

        end.setOnClickListener {

        }

        editBackBtn.setOnClickListener {

        }
        acceptBtn.setOnClickListener {

        }

        rejectBtn.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }

        editBackBtn.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }

        rootView.isFocusableInTouchMode = true
        rootView.requestFocus()
        rootView.setOnKeyListener(object : View.OnKeyListener {
            override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    requireActivity().supportFragmentManager.popBackStack()
                    return true
                }
                return false
            }
        })
    }

    private fun setupMediaSource() {
        val retriever = MediaMetadataRetriever()
        val clips = arrayListOf<MediaSource>()

        if(bufferPath.isNotNullOrEmpty()) {
            retriever.setDataSource(bufferPath)
            bufferDuration =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()

            val bufferSource =
                ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext()))
                    .createMediaSource(MediaItem.fromUri(Uri.parse(bufferPath)))

            val clip1 = ClippingMediaSource(
                bufferSource,
                0,
                TimeUnit.MILLISECONDS.toMicros(bufferDuration)
            )

            clips.add(clip1)
        }

        if(videoPath.isNullOrEmpty())
            return

        retriever.setDataSource(videoPath)
        videoDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        retriever.release()

        Log.d(TAG, "setupMediaSource: setting up videos: \n$bufferPath and \n$videoPath")

        val videoSource =
            ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext()))
                .createMediaSource(MediaItem.fromUri(Uri.parse(videoPath)))

        //  clippingMediaSource used as workaround for timeline scrubbing

        val clippedVideoDuration = videoDuration/1000
        val clip2 = ClippingMediaSource(
            videoSource,
            0,
            TimeUnit.SECONDS.toMicros(clippedVideoDuration)
        )

        clips.add(clip2)
        val mediaSource = ConcatenatingMediaSource(true, *clips.toTypedArray())

        player.setMediaSource(mediaSource)
        player.prepare()
        showBufferOverlay()
    }

    private fun initSwipeControls() {
        var startScrollingSeekPosition = 0L

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

            Log.d("seekPos2","$newSeekPosition")
            emitter.seekFast(newSeekPosition)
        }
    }

    /**
     * places a dark overlay to identify the buffer region
     */
    private fun showBufferOverlay() {
        val colour = resources.getColor(R.color.blackOverlay, context?.theme)
        val height = (35 * resources.displayMetrics.density + 0.5f).toInt()
        val padding = (8 * resources.displayMetrics.density + 0.5f).toInt()

        val thumbDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb_transparent, context?.theme) as VectorDrawable,
            resources.getColor(android.R.color.transparent, context?.theme))

        val endValue = (bufferDuration * 100 / (bufferDuration + videoDuration)).toFloat()

        bufferOverlay.apply {
            minimumHeight = height
            elevation = 4F
            setPadding(padding, 0, padding, 0)
            setBarColor(resources.getColor(android.R.color.transparent, context?.theme))
            setBackgroundResource(R.drawable.range_background)
            setLeftThumbBitmap(thumbDrawable)
            setRightThumbBitmap(thumbDrawable)
            setBarHighlightColor(colour)
            setMinValue(0F)
            setMaxValue(100F)
            setMinStartValue(0F).apply()
            setMaxStartValue(endValue).apply()

            setOnTouchListener { _, _ -> true }
        }

        if(bufferOverlay.parent == null)
            timebarHolder.addView(bufferOverlay)
    }

    private fun extendRangeMarker(startValue: Float, endValue: Float) {
        val colour = resources.getColor(android.R.color.transparent, context?.theme)
        val height = (35 * resources.displayMetrics.density + 0.5f).toInt()
        val padding = (8 * resources.displayMetrics.density + 0.5f).toInt()
        val leftThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb, context?.theme) as VectorDrawable,
            resources.getColor(android.R.color.holo_green_dark, context?.theme))
        val rightThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb, context?.theme) as VectorDrawable,
            resources.getColor(android.R.color.holo_red_light, context?.theme))

        trimSegment.apply {
            minimumHeight = height
            elevation = 6F
            setPadding(padding, 0, padding, 0)
            setBarColor(resources.getColor(android.R.color.transparent, context?.theme))
            setBackgroundResource(R.drawable.range_background)
            setLeftThumbBitmap(leftThumbImageDrawable)
            setRightThumbBitmap(rightThumbImageDrawable)
            setBarHighlightColor(colour)
            setMinValue(0F)
            setMaxValue(100F)
            setMinStartValue(startValue).apply()
            setMaxStartValue(endValue).apply()

            setOnTouchListener { _, _ -> true }
        }

        if(trimSegment.parent == null)
            timebarHolder.addView(trimSegment)
    }

    /**
     *  Converts vector drawable to bitmap image
     *
     *  @param vectorDrawable
     *  @return Bitmap
     * */
    private fun getBitmap(vectorDrawable: VectorDrawable, colorResource: Int): Bitmap? {
        vectorDrawable.colorFilter = PorterDuffColorFilter(colorResource, PorterDuff.Mode.SRC_ATOP)
        val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vectorDrawable.draw(canvas)
        return bitmap
    }
}