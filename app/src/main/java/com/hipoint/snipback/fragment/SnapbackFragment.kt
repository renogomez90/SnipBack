package com.hipoint.snipback.fragment

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.exozet.android.core.ui.custom.SwipeDistanceView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.dialog.SnapbackProcessingDialog
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kibotu.fastexoplayerseeker.SeekPositionEmitter
import net.kibotu.fastexoplayerseeker.seekWhenReady
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

class SnapbackFragment: Fragment() {
    private val TAG     = SnapbackFragment::class.java.simpleName

    private val PROCESSING_SNAPBACK_DIALOG = "com.hipoint.snipback.SNAPBACK_VIDEO_PROCESSING"
    private val EXTERNAL_DIR_NAME          = "Snipback"

    private val retries = 3
    private var tries   = 0

    private var progressDialog: SnapbackProcessingDialog? = null
    private var subscriptions : CompositeDisposable?      = null
    private var animBlink     : Animation?                = null

    private lateinit var playerView   : PlayerView
    private lateinit var rootView     : View
    private lateinit var blinkEffect  : View
    private lateinit var player       : SimpleExoPlayer
    private lateinit var backBtn      : RelativeLayout
    private lateinit var captureBtn   : RelativeLayout
    private lateinit var playerHolder : ConstraintLayout
    private lateinit var swipeDetector: SwipeDistanceView

    private val videoPathReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let{
                videoPath = it.getStringExtra(EXTRA_VIDEO_PATH) ?: ""
                hideProgressDialog()
                if(videoPath.isNotNullOrEmpty())
                    setupPlayer(videoPath!!)
            }
        }
    }

    private val mediaStorageDir by lazy { File(Environment.getExternalStorageDirectory(), EXTERNAL_DIR_NAME) }

    companion object{
        val SNAPBACK_PATH_ACTION = "com.hipoint.snipback.SNAPBACK_VIDEO_PATH"
        val EXTRA_VIDEO_PATH = "videoPath"
        
        var fragment: SnapbackFragment? = null
        var videoPath: String? = null
        
        fun newInstance(videoPath: String): SnapbackFragment {
            val bundle = Bundle()
            bundle.putString(EXTRA_VIDEO_PATH, videoPath)

            if(fragment == null){
                fragment = SnapbackFragment()
            }

            fragment!!.arguments = bundle
            return fragment!!
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.fragment_snapback, container, false)
        bindViews()
        bindListeners()

        return rootView
    }

    override fun onResume() {
        super.onResume()
        videoPath = arguments?.getString(EXTRA_VIDEO_PATH) ?: ""
        if(videoPath.isNotNullOrEmpty())
            setupPlayer(videoPath!!)
        else
            showProgressDialog()
        requireActivity().registerReceiver(videoPathReceiver, IntentFilter(SNAPBACK_PATH_ACTION))
    }

    override fun onPause() {
        if(this::player.isInitialized)
            player.release()

        requireActivity().unregisterReceiver(videoPathReceiver)
        super.onPause()
    }

    fun updatePath(newPath: String){
        SnapbackFragment.videoPath = newPath
    }

    /**
     * sets up the video player like it is new
     */
    private fun setupPlayer(videoPath: String) {
        player = SimpleExoPlayer.Builder(requireContext()).build()
        playerView.player = player
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL

        player.apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoPath)))
            prepare()
            repeatMode = Player.REPEAT_MODE_OFF
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            playWhenReady = false
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

        initSwipeControls()
    }

    private fun bindListeners() {
        captureBtn.setOnClickListener {
            if(hasStoragePermission()) {
                performCapture()
            }else {
                requestStoragePermission()
            }
        }

        backBtn.setOnClickListener {
            //  todo: show exit and save confirmation
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    /**
     * captures and saves the current frome
     */
    private fun performCapture() {
        if (!mediaStorageDir.exists()) {
            mediaStorageDir.mkdir()
        }

        CoroutineScope(Default).launch {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            var bitmap = retriever.getFrameAtTime(TimeUnit.MILLISECONDS.toMicros(player.currentPosition), MediaMetadataRetriever.OPTION_CLOSEST)

            if(bitmap == null)
                bitmap = retriever.getFrameAtTime(TimeUnit.MILLISECONDS.toMicros(player.currentPosition), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            saveImage(bitmap, requireContext())
        }

        blinkAnimation()
    }

    private fun requestStoragePermission() {
        Dexter.withContext(requireContext()).withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                    performCapture()
                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Toast.makeText(requireContext(), "cannot capture without storage permission", Toast.LENGTH_SHORT).show()
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?,
                ) { }
            }).withErrorListener { Toast.makeText(requireActivity().applicationContext, "Error occurred! ", Toast.LENGTH_SHORT).show() }
            .onSameThread()
            .check()
    }

    private fun hasStoragePermission(): Boolean =
        ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED


    private fun bindViews() {
        playerHolder  = rootView.findViewById(R.id.player_holder)
        blinkEffect   = rootView.findViewById(R.id.overlay)
        playerView    = rootView.findViewById(R.id.snapback_player_view)
        backBtn       = rootView.findViewById(R.id.back_arrow)
        captureBtn    = rootView.findViewById(R.id.button_capture)
        swipeDetector = rootView.findViewById(R.id.swipe_detector)

        animBlink = AnimationUtils.loadAnimation(requireContext(), R.anim.blink)
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
            val duration = player.duration

            val maxPercent = 0.75f
            val scaledPercent = percentX * maxPercent
            val percentOfDuration = scaledPercent * -1 * duration + startScrollingSeekPosition
            val newSeekPosition = percentOfDuration.roundToLong()

            emitter.seekFast(newSeekPosition)
        }
    }

    private fun saveImage(bitmap: Bitmap, context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = contentValues()
            values.put(MediaStore.Images.Media.RELATIVE_PATH, EXTERNAL_DIR_NAME)
            values.put(MediaStore.Images.Media.IS_PENDING, true)
            // RELATIVE_PATH and IS_PENDING are introduced in API 29.

            val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                saveImageToStream(bitmap, context.contentResolver.openOutputStream(uri))
                values.put(MediaStore.Images.Media.IS_PENDING, false)
                context.contentResolver.update(uri, values, null, null)
            }
        } else {

            if(!mediaStorageDir.exists()){
                mediaStorageDir.mkdir()
            }

            val fileName = System.currentTimeMillis().toString() + ".png"
            val file = File(mediaStorageDir, fileName)
            file.createNewFile()

            saveImageToStream(bitmap, FileOutputStream(file))
            if (file.absolutePath != null) {
                val values = contentValues()
                values.put(MediaStore.Images.Media.DATA, file.absolutePath)
                // .DATA is deprecated in API 29
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }
        }
    }

    private fun contentValues() : ContentValues {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        return values
    }

    private fun saveImageToStream(bitmap: Bitmap, outputStream: OutputStream?) {
        if (outputStream != null) {
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun showProgressDialog(){
        if (progressDialog == null){
            progressDialog = SnapbackProcessingDialog()
        }

        progressDialog!!.isCancelable = true
        progressDialog!!.show(requireActivity().supportFragmentManager, PROCESSING_SNAPBACK_DIALOG)
    }

    fun hideProgressDialog() = progressDialog?.dismiss()


    /**
     * Flashes the UI to indicate that an action has occurred
     * */
    private fun blinkAnimation() {
        playerHolder.startAnimation(animBlink)
        blinkEffect.visibility = View.VISIBLE
        blinkEffect.animate()
            .alpha(02f)
            .setDuration(100)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    blinkEffect.visibility = View.GONE
                    blinkEffect.clearAnimation()
                    playerHolder.clearAnimation()
                }
            })
    }
}