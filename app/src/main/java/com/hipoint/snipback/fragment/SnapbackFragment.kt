package com.hipoint.snipback.fragment

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SnapbackFragment: Fragment() {
    private val TAG     = SnapbackFragment::class.java.simpleName
    private val retries = 3
    private var tries   = 0

    private lateinit var videoPath : String
    private lateinit var playerView: PlayerView
    private lateinit var rootView  : View
    private lateinit var player    : SimpleExoPlayer
    private lateinit var backBtn   : RelativeLayout
    private lateinit var captureBtn: RelativeLayout

    companion object{
        var fragment: SnapbackFragment? = null

        fun newInstance(videoPath: String): SnapbackFragment {
            val bundle = Bundle()
            bundle.putString("videoPath", videoPath)

            if(fragment == null){
                fragment = SnapbackFragment()
            }

            fragment!!.arguments = bundle
            return fragment!!
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.fragment_snapback, container, false)
        bindViews()
        bindListeners()

        videoPath = requireArguments().getString("videoPath", "")

        if(videoPath.isEmpty())
            Toast.makeText(requireContext(), "Video Unavailable", Toast.LENGTH_SHORT).show()

        return rootView
    }

    override fun onResume() {
        super.onResume()
        setupPlayer()
    }

    override fun onPause() {
        player.release()
        super.onPause()
    }

    /**
     * sets up the video player like it is new
     */
    private fun setupPlayer() {
        player = SimpleExoPlayer.Builder(requireContext()).build()
        playerView.player = player
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL

        player.apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoPath)))
            prepare()
            repeatMode = Player.REPEAT_MODE_OFF
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            playWhenReady = true
        }

        playerView.controllerShowTimeoutMs = 2000
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

        player.addListener(object : Player.EventListener {
            override fun onPlayerError(error: ExoPlaybackException) {
                Log.e(TAG, "onPlayerError: ${error.message}")
                error.printStackTrace()
                tries++
                if (videoPath.isNotBlank() && tries < retries) {  //  retry in case of errors
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(500)
                        val frag = requireActivity().supportFragmentManager
                            .findFragmentByTag(AppMainActivity.PLAY_SNAPBACK_TAG)
                        requireActivity().supportFragmentManager.beginTransaction()
                            .detach(frag!!)
                            .attach(frag)
                            .commit()
                    }
                }
            }
        })

    }

    private fun bindListeners() {
        captureBtn.setOnClickListener {

        }

        backBtn.setOnClickListener {
            //  todo: show exit and save confirmation
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun bindViews() {
        player     = rootView.findViewById(R.id.snapback_player_view)
        backBtn    = rootView.findViewById(R.id.back_arrow)
        captureBtn = rootView.findViewById(R.id.button_capture)
    }
}