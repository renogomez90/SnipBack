package com.hipoint.snipback.fragment

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.VectorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.crystal.crystalrangeseekbar.widgets.CrystalRangeSeekbar
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.exozet.android.core.ui.custom.SwipeDistanceView
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.hipoint.snipback.R
import com.hipoint.snipback.adapter.TimelinePreviewAdapter
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.videoControl.SpeedDetails
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kibotu.fastexoplayerseeker.SeekPositionEmitter
import net.kibotu.fastexoplayerseeker.seekWhenReady
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class VideoEditingFragment : Fragment() {
    private val TAG = VideoEditingFragment::class.java.simpleName

    //    UI
    private lateinit var rootView: View
    private lateinit var back: ImageView         //  for leaving the edit fragment
    private lateinit var back1: ImageView         //  for leaving the ongoing edit
    private lateinit var save: ImageView         //  saving the edited file
    private lateinit var accept: ImageView         //  saving the edited file
    private lateinit var reject: ImageView         //  closing the ongoing edit
    private lateinit var playCon1: LinearLayout
    private lateinit var playCon2: LinearLayout
    private lateinit var speedIndicator: TextView
    private lateinit var extentTextBtn: TextView          //  extend or trim the video
    private lateinit var cutTextBtn: TextView
    private lateinit var highlightTextBtn: TextView
    private lateinit var slowTextBtn: TextView
    private lateinit var speedTextBtn: TextView
    private lateinit var end: TextView
    private lateinit var start: TextView
    private lateinit var playBtn: ImageButton       //  playback the video
    private lateinit var pauseBtn: ImageButton       //  stop video playback
    private lateinit var toStartBtn: ImageButton       //  seek back to start of video
    private lateinit var playCon: ConstraintLayout
    private lateinit var previewTileList: RecyclerView
    private lateinit var seekBar: DefaultTimeBar
    private lateinit var timebarHolder: FrameLayout
    private lateinit var colourOverlay: LinearLayout
    private lateinit var previewBarProgress: ProgressBar
    private lateinit var swipeDetector: SwipeDistanceView //  detects swiping actions for scrolling with preview

    //    Exoplayer
    private lateinit var playerView: PlayerView
    private lateinit var player: SimpleExoPlayer
    private lateinit var defaultBandwidthMeter: DefaultBandwidthMeter
    private lateinit var dataSourceFactory: DataSource.Factory
    private lateinit var mediaSource: MediaSource

    //    Snip
    private var snip: Snip? = null

    //  preview tile adapter
    private var timelinePreviewAdapter: TimelinePreviewAdapter? = null

    //  Seek handling
    private var subscriptions = CompositeDisposable()
    private var paused = false

    //  speed change
    var isSpeedChanged = false
    var isEditOnGoing = false
    private var currentSpeed = 3
    private var startingTimestamps = 0L
    private var endingTimestamps = 0L
    private var speedDuration = Pair<Long, Long>(0, 0)
    private var speedDetails = arrayListOf<SpeedDetails>()
    private var tmpSpeedDetails: SpeedDetails? = null
    private var rangeSeekbar: CrystalRangeSeekbar? = null

    private val progressTracker: ProgressTracker by lazy { ProgressTracker(player) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.video_editing_fragment_main2, container, false)
        snip = requireArguments().getParcelable("snip")

        bindViews()
        bindListeners()
        setupPlayer()

        return rootView
    }

    override fun onDestroy() {
        super.onDestroy()
        player.apply {
            playWhenReady = false
            stop(true)
            setVideoSurface(null)
            release()
        }
    }

    /**
     * Binds views to layout references
     */
    private fun bindViews() {
        playerView = rootView.findViewById(R.id.player_view)
        playCon = rootView.findViewById(R.id.play_con)
        playCon1 = rootView.findViewById(R.id.play_con1)
        playCon2 = rootView.findViewById(R.id.play_con2)
        speedIndicator = rootView.findViewById(R.id.speed_indicator)
        extentTextBtn = rootView.findViewById(R.id.extent_text)
        cutTextBtn = rootView.findViewById(R.id.cut_text_btn)
        highlightTextBtn = rootView.findViewById(R.id.highlight_text_btn)
        slowTextBtn = rootView.findViewById(R.id.slow_text_btn)
        speedTextBtn = rootView.findViewById(R.id.speedup_text_btn)
        save = rootView.findViewById(R.id.save)
        accept = rootView.findViewById(R.id.accept)
        end = rootView.findViewById(R.id.end)
        start = rootView.findViewById(R.id.start)
        reject = rootView.findViewById(R.id.reject)
        playBtn = rootView.findViewById(R.id.exo_play)
        pauseBtn = rootView.findViewById(R.id.exo_pause)
        toStartBtn = rootView.findViewById(R.id.toStartBtn)
        back = rootView.findViewById(R.id.back)
        back1 = rootView.findViewById(R.id.back1)
        seekBar = rootView.findViewById(R.id.exo_progress)
        timebarHolder = rootView.findViewById(R.id.timebar_holder)
        colourOverlay = rootView.findViewById(R.id.colour_overlay)
        previewTileList = rootView.findViewById(R.id.previewFrameList)
        previewBarProgress = rootView.findViewById(R.id.previewBarProgress)
        swipeDetector = rootView.findViewById(R.id.edit_swipe_detector)

        setupIcons()
    }

    /**
     * Sets up UI icons
     */
    private fun setupIcons() {
        val density = resources.displayMetrics.density
        val bound1 = Rect(0, 0, (20 * density).roundToInt(), (20 * density).roundToInt())

        val extendDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_extend)
        extendDwg?.bounds = bound1
        extentTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, extendDwg, null, null)

        val cutDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_cutout)
        cutDwg?.bounds = bound1
        cutTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, cutDwg, null, null)

        val highlightDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_highlight)
        highlightDwg?.bounds = bound1
        highlightTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, highlightDwg, null, null)

        val slowDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_slow)
        slowDwg?.bounds = bound1
        slowTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, slowDwg, null, null)

        val speedDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_speed)
        speedDwg?.bounds = bound1
        speedTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, speedDwg, null, null)

    }

    /**
     * Binds listeners for view references
     */
    private fun bindListeners() {

        end.setOnClickListener {
            start.setBackgroundResource(R.drawable.end_curve)
            end.setBackgroundResource(R.drawable.end_curve_red)
            val currentPosition = player.currentPosition
            if (startingTimestamps != currentPosition) {
                startingTimestamps = currentPosition  //take the starting point when end is pressed
//                playerView.setExtraAdGroupMarkers(longArrayOf(currentPosition), booleanArrayOf(false))
                val startValue = (startingTimestamps * 100 / player.duration).toFloat()

                speedDuration = Pair(startingTimestamps, startingTimestamps)    //  we only have the starting position now
                tmpSpeedDetails = SpeedDetails(false, currentSpeed, speedDuration)
                speedDetails.add(tmpSpeedDetails!!)

                setupRangeMarker(startValue, 0F)    //  initial value for marker
                timebarHolder.addView(rangeSeekbar)
            }
        }

        start.setOnClickListener {
            start.setBackgroundResource(R.drawable.start_curve)
            end.setBackgroundResource(R.drawable.end_curve)
        }

        extentTextBtn.setOnClickListener {
//            extent.setImageResource(R.drawable.ic_extent_red)
            val dwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_extent_red)
            dwg?.bounds = Rect(0, 0, 20, 20)
            extentTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, dwg, null, null)
            extentTextBtn.setTextColor(resources.getColor(R.color.colorPrimaryDimRed))
            playCon1.visibility = View.VISIBLE
            playCon2.visibility = View.GONE
        }

        back.setOnClickListener { showDialogConfirmation() }

        back1.setOnClickListener {
            speedIndicator.visibility = View.GONE
            playCon1.visibility = View.GONE
            playCon2.visibility = View.VISIBLE
        }

        save.setOnClickListener { showDialogSave() }

        accept.setOnClickListener {
            val currentPosition = player.currentPosition
            if (endingTimestamps != currentPosition)
                endingTimestamps = currentPosition
            //  durations are correct and changes can be accepted
//            playerView.setExtraAdGroupMarkers(longArrayOf(startingTimestamps, endingTimestamps), booleanArrayOf(false, false))

            speedDetails.remove(tmpSpeedDetails)
            progressTracker.removeSpeedDetails(tmpSpeedDetails!!)

            speedDuration = Pair(startingTimestamps, endingTimestamps)
            speedDetails.add(SpeedDetails(false, currentSpeed, speedDuration))

            progressTracker.setChangeAccepted(true)
            progressTracker.setSpeed(currentSpeed.toFloat())
            progressTracker.addSpeedDetails(speedDetails)
            progressTracker.run()
            isEditOnGoing = false
            isSpeedChanged = true

            speedIndicator.visibility = View.GONE
            playCon1.visibility = View.GONE
            playCon2.visibility = View.VISIBLE

            val startValue = (startingTimestamps * 100 / player.duration).toFloat()
            val endValue = ((endingTimestamps - startingTimestamps) * 100 / player.duration).toFloat()

            setupRangeMarker(startValue, endValue)
            // todo: show enclosing markers
        }

        reject.setOnClickListener {
            /*showDialogdelete()*/
            progressTracker.setChangeAccepted(false)
            currentSpeed = 1
            progressTracker.setSpeed(currentSpeed.toFloat())
            if (tmpSpeedDetails != null)
                progressTracker.removeSpeedDetails(tmpSpeedDetails!!)
            startingTimestamps = 0L
            endingTimestamps = 0L
//            playerView.setExtraAdGroupMarkers(LongArray(0), BooleanArray(0))    //  hopefully will clear the admarkers
            player.setPlaybackParameters(PlaybackParameters(1F))
            isSpeedChanged = false
            isEditOnGoing = false

            speedIndicator.visibility = View.GONE
            playCon1.visibility = View.GONE
            playCon2.visibility = View.VISIBLE
        }

        playBtn.setOnClickListener {
            if (player.currentPosition >= player.duration)
                player.seekTo(0)
            player.playWhenReady = true
            paused = false
            Log.d(TAG, "Start Playback")
        }

        pauseBtn.setOnClickListener {
            if (player.isPlaying) {
                player.playWhenReady = false
                paused = true
                Log.d(TAG, "Stop Playback")
            }
        }

        toStartBtn.setOnClickListener {
            player.playWhenReady = false
            player.seekTo(0)
        }

        seekBar.setOnTouchListener { _, _ ->
            true
        }
        seekBar.isClickable = false

        slowTextBtn.setOnClickListener {
            speedIndicator.visibility = View.VISIBLE
            playCon1.visibility = View.VISIBLE
            playCon2.visibility = View.GONE
            isEditOnGoing = true
            player.playWhenReady = false
        }

        speedIndicator.setOnClickListener {
            speedIndicator.text = changeSpeedText()
            progressTracker.setSpeed(currentSpeed.toFloat())
        }
    }

    /**
     * Sets up the crystal range marker for displaying the selected ranges
     */
    private fun setupRangeMarker(startValue: Float, endValue: Float) {

        if (rangeSeekbar == null) {
            rangeSeekbar = CrystalRangeSeekbar(requireContext())

            val layoutParam = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            rangeSeekbar?.layoutParams = layoutParam
        }
        val colour =
                if (!speedDetails.isNullOrEmpty()) {
                    if (speedDetails[0].isFast)
                        resources.getColor(android.R.color.holo_blue_dark, context?.theme)
                    else
                        resources.getColor(android.R.color.holo_green_dark, context?.theme)
                } else
                    resources.getColor(android.R.color.holo_green_dark, context?.theme)

        val thumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.thumb, context?.theme) as VectorDrawable)
        val height = (35 * resources.displayMetrics.density + 0.5f).toInt()
        val padding = (8 * resources.displayMetrics.density + 0.5f).toInt()

        rangeSeekbar?.apply {
            minimumHeight = height
            elevation = 1F
            setPadding(padding, 0, padding, 0)
            setBarColor(resources.getColor(android.R.color.transparent, context?.theme))
            setBarHighlightColor(colour)
            setLeftThumbColor(colour)
            setRightThumbColor(colour)
            setLeftThumbBitmap(thumbImageDrawable)
            setRightThumbBitmap(thumbImageDrawable)
            setMinValue(0F)
            setMaxValue(100F)
            setMinStartValue(startValue)?.apply()

            if (endingTimestamps != 0L)
                setGap(endValue)
            else
                setMaxStartValue(startValue)?.apply()

            setOnTouchListener { view, motionEvent -> true }
        }
    }

    private fun changeSpeedText(): CharSequence? {
        return when (currentSpeed) {
            1 -> {
                currentSpeed = 3; "3X"
            }
            3 -> {
                currentSpeed = 4; "4X"
            }
            4 -> {
                currentSpeed = 5; "5X"
            }
            5 -> {
                currentSpeed = 10; "10X"
            }
            10 -> {
                currentSpeed = 15; "15X"
            }
            else -> {
                currentSpeed = 1; "1X"
            }
        }
    }

    /**
     * Setting up the player to play the require snip for editing
     */
    private fun setupPlayer() {

        CoroutineScope(Default).launch {
            getVideoPreviewFrames()
        }

        val videoUri = Uri.parse(snip!!.videoFilePath)

        defaultBandwidthMeter = DefaultBandwidthMeter.Builder(requireContext()).build()
        dataSourceFactory = DefaultDataSourceFactory(activity,
                Util.getUserAgent(requireActivity(), "mediaPlayerSample"), defaultBandwidthMeter)

        mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(videoUri)

        player = SimpleExoPlayer.Builder(requireContext()).build()
        playerView.player = player
        player.prepare(mediaSource)
        player.repeatMode = Player.REPEAT_MODE_OFF

        playerView.apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            controllerAutoShow = false
            controllerShowTimeoutMs = -1
            controllerHideOnTouch = false
            showController()
        }

        player.playWhenReady = true
        previewBarProgress.visibility = View.VISIBLE

        initSwipeControls()
    }

    protected fun showDialogConfirmation() {
        val dialog = Dialog(requireActivity())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.warningdialog_savevideodiscardchanges)
        dialog.show()
    }

    protected fun showDialogSave() {
        val dialog = Dialog(requireActivity())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.warningdialog_savevideo)
        dialog.show()
    }

    protected fun showDialogdelete() {
        val dialog = Dialog(requireActivity())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.warningdialog_deletevideo)
        dialog.show()
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

            player.playWhenReady = !paused
        }

        val emitter = SeekPositionEmitter()
        player.seekWhenReady(emitter)
                .subscribe({
                    Log.v(TAG, "seekTo=${it.first} isSeeking=${it.second}")
                }, { Log.e(TAG, "${it.message}") })
                .addTo(subscriptions)

        swipeDetector.onScroll { percentX, _ ->
            // left swipe is positive, right is negative
            val duration = player.duration
            val maxPercent = 0.75f
            val scaledPercent = percentX * maxPercent
            val percentOfDuration = scaledPercent * -1 * duration + startScrollingSeekPosition
            // shift in position domain and ensure circularity
            var newSeekPosition = percentOfDuration.roundToLong()
            /*((percentOfDuration + duration) % duration).roundToLong().absoluteValue*/

            if (isEditOnGoing) {
                if (newSeekPosition < startingTimestamps) {
                    newSeekPosition = startingTimestamps
                }

                rangeSeekbar?.setMaxStartValue((newSeekPosition * 100 / player.duration).toFloat())?.apply()
            }

            if (newSeekPosition < 0)
                newSeekPosition = 0
            else if (newSeekPosition > player.duration)
                newSeekPosition = player.duration

            emitter.seekFast(newSeekPosition)

        }
    }

    /**
     * Populates preview frames in the seekBar area from the video
     */
    private suspend fun getVideoPreviewFrames() {
        previewTileList.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)

        if (snip?.videoFilePath.isNotNullOrEmpty()) {
            val photoList = arrayListOf<Bitmap>()
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(snip?.videoFilePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
            for (i in 0..duration step duration / 9) {
                photoList.add(retriever.getFrameAtTime(i))
            }
            retriever.release()

            if (fragment != null && context != null)
                if (fragment!!.isVisible) {  //  in case the is not visible the UI cannot be updated
                    timelinePreviewAdapter = TimelinePreviewAdapter(requireContext(), photoList)
                    //  change context for updating the UI
                    withContext(Main) {
                        previewTileList.adapter = timelinePreviewAdapter
                        previewTileList.adapter?.notifyDataSetChanged()
                        previewBarProgress.visibility = View.GONE
                    }
                }
        }
    }

    companion object {
        var fragment: VideoEditingFragment? = null

        @JvmStatic
        fun newInstance(aSnip: Snip?): VideoEditingFragment {
            fragment = VideoEditingFragment()
            val bundle = Bundle()
            bundle.putParcelable("snip", aSnip)
            fragment!!.arguments = bundle
            return fragment!!
        }
    }

    inner class ProgressTracker(private val player: Player) : Runnable {

        private val handler: Handler = Handler()
        private var speedDetailList: ArrayList<SpeedDetails> = arrayListOf()

        private var currentSpeed: Float = 1F
        private var isChangeAccepted: Boolean = false

        override fun run() {
            if (isChangeAccepted) {
                val currentPosition = player.currentPosition
                if (speedDetailList.isNotEmpty() && currentPosition in speedDetailList[0].timeDuration!!.first..speedDetailList[0].timeDuration!!.second) {
                    if (speedDetailList[0].isFast) {
                        val overlayColour = resources.getColor(android.R.color.holo_blue_dark, requireContext().theme)
                        if (!colourOverlay.isShown) {
                            colourOverlay.visibility = View.VISIBLE
                            colourOverlay.setBackgroundColor(overlayColour)
                        }
                        player.setPlaybackParameters(PlaybackParameters(currentSpeed))
                    } else {
                        val overlayColour = resources.getColor(android.R.color.holo_green_dark, requireContext().theme)
                        if (!colourOverlay.isShown) {
                            colourOverlay.visibility = View.VISIBLE
                            colourOverlay.setBackgroundColor(overlayColour)
                        }
                        player.setPlaybackParameters(PlaybackParameters(1 / currentSpeed))
                    }
                } else {
                    if (player.playbackParameters != PlaybackParameters(1F))
                        player.setPlaybackParameters(PlaybackParameters(1F))
                    else {
                        colourOverlay.visibility = View.GONE
                    }
                }
            } else {
                if (player.playbackParameters != PlaybackParameters(1F))
                    player.setPlaybackParameters(PlaybackParameters(1F))
                else {
                    colourOverlay.visibility = View.GONE
                }
            }
            handler.postDelayed(this, 200 /* ms */)
        }

        init {
            handler.post(this)
        }

        fun setSpeed(speed: Float) {
            currentSpeed = speed
        }

        fun setChangeAccepted(isAccepted: Boolean) {
            isChangeAccepted = isAccepted
        }

        fun addSpeedDetails(speedDetails: ArrayList<SpeedDetails>) {
            speedDetailList.addAll(speedDetails)
            speedDetailList.sortWith(Comparator { s1, s2 ->
                (s2.timeDuration?.first!! - s1?.timeDuration!!.first).toInt()
            })
        }

        fun removeSpeedDetails(speedDetail: SpeedDetails): Boolean {
            return if (speedDetailList.contains(speedDetail)) {
                speedDetailList.remove(speedDetail)
            } else
                false
        }
    }

    fun getBitmap(vectorDrawable: VectorDrawable): Bitmap? {
        val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth,
                vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vectorDrawable.draw(canvas)
        return bitmap
    }

}