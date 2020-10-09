package com.hipoint.snipback.fragment

import android.app.Dialog
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.hipoint.snipback.R
import com.hipoint.snipback.adapter.TimelinePreviewAdapter
import com.hipoint.snipback.room.entities.Snip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class VideoEditingFragment : Fragment() {
    private val TAG = VideoEditingFragment::class.java.simpleName

    //    UI
    private lateinit var rootView: View
    private lateinit var back: ImageView
    private lateinit var back1: ImageView
    private lateinit var save: ImageView
    private lateinit var close: ImageView
    private lateinit var layout_extent: RelativeLayout
    private lateinit var play_con1: LinearLayout
    private lateinit var play_con2: LinearLayout
    private lateinit var extent: ImageButton
    private lateinit var extent_text: TextView
    private lateinit var end: TextView
    private lateinit var start: TextView
    private lateinit var playBtn: ImageButton
    private lateinit var pauseBtn: ImageButton
    private lateinit var play_con: ConstraintLayout
    private lateinit var previewTileList: RecyclerView

    //    Exoplayer
    private lateinit var playerView: PlayerView
    private lateinit var player: SimpleExoPlayer
    private lateinit var defaultBandwidthMeter: DefaultBandwidthMeter
    private lateinit var dataSourceFactory: DataSource.Factory
    private lateinit var mediaSource: MediaSource

    //    Snip
    private var snip: Snip? = null
    //  preview tile adapter
    private var timelinePreviewAdapter : TimelinePreviewAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.video_editing_fragment_main2, container, false)
        bindViews()
        bindListeners()
        setupPlayer()

        return rootView
    }

    /**
     * Binds views to layout references
     */
    private fun bindViews() {
        layout_extent   = rootView.findViewById(R.id.layout_extent)
        playerView      = rootView.findViewById(R.id.player_view)
        play_con        = rootView.findViewById(R.id.play_con)
        play_con1       = rootView.findViewById(R.id.play_con1)
        play_con2       = rootView.findViewById(R.id.play_con2)
        extent          = rootView.findViewById(R.id.extent)
        extent_text     = rootView.findViewById(R.id.extent_text)
        save            = rootView.findViewById(R.id.save)
        end             = rootView.findViewById(R.id.end)
        start           = rootView.findViewById(R.id.start)
        close           = rootView.findViewById(R.id.close)
        playBtn         = rootView.findViewById(R.id.exo_play)
        pauseBtn        = rootView.findViewById(R.id.exo_pause)
        back            = rootView.findViewById(R.id.back)
        back1           = rootView.findViewById(R.id.back1)
        previewTileList = rootView.findViewById(R.id.previewFrameList)
    }

    /**
     * Binds listeners for view references
     */
    private fun bindListeners() {
        end.setOnClickListener {
            start.setBackgroundResource(R.drawable.end_curve)
            end.setBackgroundResource(R.drawable.end_curve_red)
        }
        start.setOnClickListener {
            start.setBackgroundResource(R.drawable.start_curve)
            end.setBackgroundResource(R.drawable.end_curve)
        }
        extent.setOnClickListener {
            extent.setImageResource(R.drawable.ic_extent_red)
            extent_text.setTextColor(resources.getColor(R.color.colorPrimaryDimRed))
            play_con1.visibility = View.VISIBLE
            play_con2.visibility = View.GONE
        }
        back.setOnClickListener { showDialogConformation() }
        back1.setOnClickListener { showDialogConformation() }
        save.setOnClickListener { showDialogSave() }
        close.setOnClickListener { showDialogdelete() }
        playBtn.setOnClickListener {
            if (player.isPlaying) {
                player.playWhenReady = false
                Log.d(TAG, "Stop Playback")
            } else {
                player.playWhenReady = true
                Log.d(TAG, "Start Playback")
            }
        }
    }

    /**
     * Setting up the player to play the require snip for editing
     */
    private fun setupPlayer() {

        snip = requireArguments().getParcelable("snip")
        defaultBandwidthMeter = DefaultBandwidthMeter.Builder(requireContext()).build()
        dataSourceFactory = DefaultDataSourceFactory(requireContext(),
                Util.getUserAgent(requireActivity(), "mediaPlayerSample"), defaultBandwidthMeter)
        mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(snip!!.videoFilePath))
        player = SimpleExoPlayer.Builder(requireContext()).build()
        playerView.player = player
        playerView.apply{
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            controllerAutoShow = false
            controllerShowTimeoutMs = -1
            controllerHideOnTouch = false
            showController()
        }
        player.prepare(mediaSource)
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.playWhenReady = true

        CoroutineScope(Main).launch{
            getVideoPreviewFrames()
        }
    }

    protected fun showDialogConformation() {
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


    private fun getVideoPreviewFrames(){
        previewTileList.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)

        if(snip?.videoFilePath.isNotNullOrEmpty()){
            val photoList = arrayListOf<Bitmap>()
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(snip?.videoFilePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
            for(i in 0..duration step duration/9){
                photoList.add(retriever.getFrameAtTime(i))
            }
            retriever.release()
            timelinePreviewAdapter = TimelinePreviewAdapter(requireContext(), photoList)
            previewTileList.adapter = timelinePreviewAdapter
            previewTileList.adapter?.notifyDataSetChanged()
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(aSnip: Snip?): VideoEditingFragment {
            val fragment = VideoEditingFragment()
            val bundle = Bundle()
            bundle.putParcelable("snip", aSnip)
            fragment.arguments = bundle
            return fragment
        }
    }
}