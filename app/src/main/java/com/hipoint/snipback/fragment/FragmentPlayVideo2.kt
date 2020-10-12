package com.hipoint.snipback.fragment

import Jni.FFmpegCmd
import VideoHandle.OnEditorListener
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.exozet.android.core.extensions.onClick
import com.exozet.android.core.ui.custom.SwipeDistanceView
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
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
import com.hipoint.snipback.room.entities.Event
import com.hipoint.snipback.room.entities.Hd_snips
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.room.repository.AppRepository
import com.hipoint.snipback.room.repository.AppViewModel
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import net.kibotu.fastexoplayerseeker.SeekPositionEmitter
import net.kibotu.fastexoplayerseeker.seekWhenReady
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

class FragmentPlayVideo2 : Fragment() {
    private val TAG = FragmentPlayVideo2::class.java.simpleName

    private var currentPosi = 0L
    private var subscriptions = CompositeDisposable()

    private lateinit var mediaSource          : MediaSource
    private lateinit var player               : SimpleExoPlayer
    private lateinit var dataSourceFactory    : DataSource.Factory
    private lateinit var defaultBandwidthMeter: DefaultBandwidthMeter
    private lateinit var appRepository        : AppRepository
    private lateinit var appViewModel         : AppViewModel
    private lateinit var playerView           : PlayerView
    private lateinit var playBtn              : ImageButton
    private lateinit var pauseBtn             : ImageButton
    private lateinit var editBtn              : ImageButton
    private lateinit var seekBar              : DefaultTimeBar
    private lateinit var rootView             : View
    private lateinit var tag                  : ImageView
    private lateinit var backArrow            : RelativeLayout
    private lateinit var buttonCamera         : RelativeLayout
    private lateinit var tvConvertToReal      : ImageButton
    private lateinit var swipeDetector        : SwipeDistanceView

    // new
    /*
    private val seekdistance = 0f
    var initialX = 0f
    var initialY = 0f
    var currentX = 0f
    var currentY = 0f
    var condition2 = 0f
    */
    private var event: Event? = null

    // new added
    private var snip: Snip? = null
    private var paused = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.layout_play_video, container, false)
        appRepository = AppRepository(requireActivity().applicationContext)
        appViewModel = ViewModelProvider(this).get(AppViewModel::class.java)
        snip = requireArguments().getParcelable("snip")

        appViewModel.getEventByIdLiveData(snip!!.event_id).observe(viewLifecycleOwner, Observer { snipevent: Event? -> event = snipevent })

        bindViews()
        initSetup()
        bindListeners()
//        uri = Uri.parse(getArguments().getString("uri"));

        /*
        simpleExoPlayerView.setOnTouchListener(object : OnSwipeTouchListener(activity) {
            override fun onSwipeTop() {
//                Toast.makeText(requireActivity(), "top", Toast.LENGTH_SHORT).show();
            }

            override fun onSwipeRight(diffX: Float) {
                if (player.currentPosition < player.duration) {
                    player.seekTo(player.currentPosition + diffX.toLong())
                    simpleExoPlayerView.showController()
                } else if (player.currentPosition == player.duration) {
                    player.seekTo(0)
                    simpleExoPlayerView.showController()
                } else {
                    player.seekTo(0)
                    simpleExoPlayerView.showController()
                }
            }

            override fun onSwipeLeft(diffX: Float) {
                if (player.currentPosition == 0L) {
                } else {
                    player.seekTo(player.currentPosition - diffX.toLong())
                    simpleExoPlayerView.showController()
                }

                // changeSeek(diffX,diffY,distanceCovered,"X");
            }

            override fun onSwipeBottom() {
                Toast.makeText(activity, "bottom", Toast.LENGTH_SHORT).show()
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                simpleExoPlayerView.showController()
                return super.onTouch(v, event)
            }
        })
        */


/*
//        int w;
//        int h;
//
//        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
//        mediaMetadataRetriever.setDataSource( uri);
//        String height = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
//        String width = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
//        w = Integer.parseInt(width);
//        h = Integer.parseInt(height);
//
//        if (w > h) {
//            ( requireActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
//        } else {
//            ( requireActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
//        }

//        OrientationEventListener mOrientationListener = new OrientationEventListener(
//                requireActivity()) {
//            @Override
//            public void onOrientationChanged(int orientation) {
//                if(!requireActivity().isFinishing()) {
//                    if (orientation == 0 || orientation == 180) {
//                        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
//                    } else if (orientation == 90 || orientation == 270) {
//                        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
//                    }
//                }
//            }
//        };
//
//        if (mOrientationListener.canDetectOrientation()) {
//            mOrientationListener.enable();
//        }
        */
        return rootView
    }

    private fun initSetup() {
        defaultBandwidthMeter = DefaultBandwidthMeter.Builder(requireContext()).build()
        dataSourceFactory = DefaultDataSourceFactory(activity,
                Util.getUserAgent(requireActivity(), "mediaPlayerSample"), defaultBandwidthMeter)

        mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(snip!!.videoFilePath))

        player = SimpleExoPlayer.Builder(requireContext()).build()
        playerView.player = player
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL

        if (snip!!.is_virtual_version == 1) {   // Virtual versions only play part of the media
            val clippingMediaSource = ClippingMediaSource(mediaSource, TimeUnit.SECONDS.toMicros(snip!!.start_time.toLong()),  TimeUnit.SECONDS.toMicros(snip!!.end_time.toLong()))
            seekBar.setDuration(snip!!.snip_duration.toLong() * 1000)
            player.prepare(clippingMediaSource)
        } else {
            player.prepare(mediaSource)
        }

        player.repeatMode = Player.REPEAT_MODE_OFF
        player.playWhenReady = true
        playerView.controllerShowTimeoutMs = 2000
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    private fun bindViews() {
        buttonCamera    = rootView.findViewById(R.id.button_camera)
        backArrow       = rootView.findViewById(R.id.back_arrow)
        tvConvertToReal = rootView.findViewById(R.id.tvConvertToReal)
        playerView      = rootView.findViewById(R.id.player_view)
        editBtn         = rootView.findViewById(R.id.edit)
        tag             = rootView.findViewById(R.id.tag)
        swipeDetector   = rootView.findViewById(R.id.swipe_detector)
        seekBar         = rootView.findViewById(R.id.exo_progress)
        playBtn         = rootView.findViewById(R.id.exo_play)
        pauseBtn        = rootView.findViewById(R.id.exo_pause)
    }

    private fun bindListeners() {
        playBtn.onClick {
            if (player.currentPosition >= player.contentDuration) {
                player.seekTo(0)
            }
            player.playWhenReady = true
            paused = false
        }

        pauseBtn.onClick {
            player.playWhenReady = false
            paused = true
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
            val intent1 = Intent(activity, AppMainActivity::class.java)
            startActivity(intent1)
            requireActivity().finishAffinity()
        }

        tag.setOnClickListener {
            // ((AppMainActivity) requireActivity()).loadFragment(CreateTag.newInstance(), true);
        }

        editBtn.setOnClickListener {
            player.playWhenReady = false
            (activity as AppMainActivity?)!!.loadFragment(VideoEditingFragment.newInstance(snip), true)
        }

        rootView.isFocusableInTouchMode = true
        rootView.requestFocus()
        rootView.setOnKeyListener(View.OnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                player.release()
                requireActivity().onBackPressed()
                //                    ((AppMainActivity) requireActivity()).loadFragment(FragmentGalleryNew.newInstance(), true);
                return@OnKeyListener true
            }
            false
        })

        initSwipeControls()
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

            val duration = player.duration

            val maxPercent = 0.75f
            val scaledPercent = percentX * maxPercent
            val percentOfDuration = scaledPercent * -1 * duration + startScrollingSeekPosition
            // shift in position domain and ensure circularity
            val newSeekPosition = ((percentOfDuration + duration) % duration).roundToLong().absoluteValue

            emitter.seekFast(newSeekPosition)

        }
    }

    private val VIDEO_DIRECTORY_NAME = "Snipback"
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


        @JvmStatic
        fun newInstance(snip: Snip?): FragmentPlayVideo2 {
            //  we need to create new fragments for each video
            // otherwise the smooth scrolling is having issues for some reason
            val fragment = FragmentPlayVideo2()
            val bundle = Bundle()
            bundle.putParcelable("snip", snip)
            fragment.arguments = bundle
            return fragment
        }
    }

    override fun onPause() {
        player.playWhenReady = false
        super.onPause()
    }

    override fun onDestroy() {
        subscriptions.dispose()
        player.release()
        super.onDestroy()
    }
}