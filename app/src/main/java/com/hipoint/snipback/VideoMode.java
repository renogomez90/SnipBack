package com.hipoint.snipback;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.legacy.app.FragmentCompat;

import com.hipoint.snipback.Utils.AutoFitTextureView;
import com.hipoint.snipback.Utils.CountUpTimer;
import com.hipoint.snipback.Utils.VideoUtils;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.dialog.ProcessingDialog;
import com.hipoint.snipback.fragment.Feedback_fragment;
import com.hipoint.snipback.fragment.FragmentGalleryNew;
import com.hipoint.snipback.listener.IVideoOpListener;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.Hd_snips;
import com.hipoint.snipback.room.entities.Snip;
import com.hipoint.snipback.room.repository.AppRepository;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static android.view.View.VISIBLE;

public class VideoMode extends Fragment implements
        View.OnClickListener,
        View.OnTouchListener,
        ActivityCompat.OnRequestPermissionsResultCallback,
        AppRepository.OnTaskCompleted,
        IVideoOpListener {

    //Camera Orientation
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS    = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS    = new SparseIntArray();

    private static final String TAG                    = "Camera2VideoFragment";
    private static final String FRAGMENT_DIALOG        = "dialog";
    private static final int REQUEST_VIDEO_PERMISSIONS = 1;

    private static String VIDEO_DIRECTORY_NAME  = "SnipBackVirtual";
    private static String THUMBS_DIRECTORY_NAME = "Thumbs";
    private String VIDEO_DIRECTORY_NAME1        = "Snipback";
//    private GestureFilter detector;

    //clips
    private static boolean recordPressed      = false;                //  to know if the user has actively started recording
    private static boolean recordClips        = true;                 //  to check if short clips should be recorded
    private final int[] totalDuration         = {0};                  //  total combined duration of merged clip
    private Long clipDuration                 = 30 * 1000L;           //  Buffer duration
    private VideoUtils mergeUtils             = new VideoUtils(this);
    private ProcessingDialog processingDialog = null;
    private Long actualClipTime               = 0L;
    private Queue<File> clipQueue;


    //two finger pinch zoom
    public float finger_spacing = 0;
    public double zoom_level    = 1f;
    public Rect zoom;
    TextView zoomFactor;

    //zoom slider controls
    float mProgress;
    float minZoom;
    float maxZoom;
    private float zoomLevel;
    float currentProgress = 1;
    final float zoomStep  = 1;

    //left swipe
    private float x1, x2;
    static final int MIN_DISTANCE = 150;


    /**
     * Called when a touch event is dispatched to a view. This allows listeners to
     * get a chance to respond before the target view.
     *
     * @param v     The view the touch event has been dispatched to.
     * @param event The MotionEvent object containing full information about
     *              the event.
     * @return True if the listener has consumed the event, false otherwise.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                x1 = event.getX();
                break;
            case MotionEvent.ACTION_UP:
                x2 = event.getX();
                float deltaX = x2 - x1;
                if (Math.abs(deltaX) > MIN_DISTANCE) {
                    // Left to Right swipe action
                    if (x2 > x1) {
                        if (mIsRecordingVideo) {
                            saveSnipTimeToLocal();
                        }
                    }
                }
                break;
        }

        try {
            CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);

            String mCameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            float maxZoom = (characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM));

            Rect m = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            if (m == null) return false;

            int action = event.getAction();
            float current_finger_spacing;
//            setUpCaptureRequestBuilder(mPreviewBuilder);

            if (event.getPointerCount() == 2) {
                // Multi touch logic
                current_finger_spacing = getFingerSpacing(event);

                float delta = 0.03f; //control the zoom sensitivity

                if (finger_spacing != 0) {
                    if (current_finger_spacing > finger_spacing) {

                        if ((maxZoom - zoom_level) <= delta) {
                            delta = (float) (maxZoom - zoom_level);
                        }
                        zoom_level = zoom_level + delta;
//                        seekBar.setProgress((int)zoom_level);
                    } else if (current_finger_spacing < finger_spacing) {
                        if ((zoom_level - delta) < 1f) {
                            delta = (float) (zoom_level - 1f);
                        }
                        zoom_level = zoom_level - delta;
//                        if (zoom_level == 1){
//                            seekBar.setProgress(0);
//                        } else {
////                            seekBar.setProgress((int) zoom_level);
//                        }
                    }

                    float ratio = (float) ((float) 1 / zoom_level);
                    //This ratio is the ratio of cropped Rect to Camera's original(Maximum) Rect
                    //croppedWidth and croppedHeight are the pixels cropped away, not pixels after cropped

                    int croppedWidth = m.width() - Math.round((float) m.width() * ratio);
                    int croppedHeight = m.height() - Math.round((float) m.height() * ratio);

                    // zoom represents the zoomed visible area
                    zoom = new Rect(croppedWidth / 2, croppedHeight / 2,
                            m.width() - croppedWidth / 2, m.height() - croppedHeight / 2);
                    mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
                }
                finger_spacing = current_finger_spacing;
            } else {
                if (action == MotionEvent.ACTION_UP) {
                    //single touch logic
                }
            }

            try {
                mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null,
                        mBackgroundHandler);
            } catch (CameraAccessException | NullPointerException e) {
                e.printStackTrace();
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException("can not access camera.", e);
        }

        return true;


    }

    /**
     * Returns the calculated space between touch points
     *
     * @param event
     * @return float space between touched points
     */
    private float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    public interface OnTaskCompleted {
        void onTaskCompleted(boolean success);
    }

    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * Button to record video
     */
    private Button mButtonVideo;

    /**
     * A reference to the opened {@link android.hardware.camera2.CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * A reference to the current {@link android.hardware.camera2.CameraCaptureSession} for
     * preview.
     */
    private CameraCaptureSession mPreviewSession;

//    AppMainActivity.MyOnTouchListener onTouchListener;
//
//    private OnSwipeTouchListener touchListener=new OnSwipeTouchListener(getActivity()) {
//        public void impelinfragment() {
//            //do what you want:D
//            Log.i("swipe","swipe left");
//        }
//    };

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * The {@link android.util.Size} of video recording.
     */
    private Size mVideoSize;

    private CountUpTimer countUpTimer;

    /**
     * MediaRecorder
     */
    private MediaRecorder mMediaRecorder;

    /**
     * Whether the app is recording video now
     */
    private boolean mIsRecordingVideo;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
//            startPreview();
            //  If recordClips is true the app will automatically start recording
            //  and saving clips while displaying the preview
            //  else preview is shown without recording.
            if (recordClips) {
                startRecordingVideo();
            } else
                startPreview();

            mCameraOpenCloseLock.release();
            if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };
    private Hd_snips hdSnips;

    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private static Size chooseVideoSize(Size[] choices) {
//        for (Size size : choices) {
//            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
//                return size;
//            }
//        }
//        Log.e(TAG, "Couldn't find any suitable video size");
//        return choices[choices.length - 1];

        for (Size size : choices) {
            if (1920 == size.getWidth() && 1080 == size.getHeight()) {
                return size;
            }
        }
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private Integer mSensorOrientation;
    private String outputFilePath;
    private CaptureRequest.Builder mPreviewBuilder;
    private int timerSecond = 0;
    private AppRepository appRepository;
    private Animation animBlink;

    //Views
    private View rootView;
    private ImageButton gallery, settings, recordButton, recordStopButton, r_3_bookmark, r_2_shutter;
    private TextView tvTimer;
    private Chronometer mChronometer;
    private View blinkEffect;
    private RelativeLayout rlVideo, recStartLayout, bottomContainer, zoomControlLayout;
    private OnTaskCompleted thumbnailProcesingCompleted;
    private SeekBar seekBar;
    ImageButton zoomOut, zoomIn;

    public static VideoMode newInstance() {
        VideoMode fragment = new VideoMode();
        return fragment;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_videomode, container, false);
        (getActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        animBlink = AnimationUtils.loadAnimation(getContext(), R.anim.blink);
        rlVideo = rootView.findViewById(R.id.rl_video);
        gallery = rootView.findViewById(R.id.r_1);
        settings = rootView.findViewById(R.id.r_5);
        recordButton = rootView.findViewById(R.id.rec);
        mChronometer = rootView.findViewById(R.id.chronometer);
        recordButton.setOnClickListener(this);
        mTextureView = rootView.findViewById(R.id.texture);
        blinkEffect = rootView.findViewById(R.id.overlay);

        recStartLayout = rootView.findViewById(R.id.rec_start_container);
        bottomContainer = rootView.findViewById(R.id.bottom_cont);
        recordStopButton = rootView.findViewById(R.id.rec_stop);

        zoomControlLayout = rootView.findViewById(R.id.zoom_control_layout);
        seekBar = rootView.findViewById(R.id.zoom_controller);
        zoomOut = rootView.findViewById(R.id.zoom_out_btn);
        zoomIn = rootView.findViewById(R.id.zoom_in_btn);

        recordStopButton.setOnClickListener(this);
        r_3_bookmark = rootView.findViewById(R.id.r_3_bookmark);
        r_3_bookmark.setOnClickListener(this);
        r_2_shutter = rootView.findViewById(R.id.r_2_shutter);
        r_2_shutter.setOnClickListener(this);

        zoomFactor = rootView.findViewById(R.id.zoom_factor);
        zoomControlLayout.setOnClickListener(this);
        zoomIn.setOnClickListener(this);
        zoomOut.setOnClickListener(this);
        rlVideo.setOnTouchListener(this);
        rlVideo.setOnClickListener(this);

//        detector = new GestureFilter(getActivity(), this);

//        ((AppMainActivity)getActivity()).registerMyOnTouchListener(new AppMainActivity.MyOnTouchListener() {
//            @Override
//            public void onTouch(MotionEvent ev) {
//                touchListener.onTouch(ev);
//            }
//        });

//        rlVideo.setOnTouchListener((view, motionEvent) -> mTextureView.onTouch(view, motionEvent));
//        accessRoomDatabase();
        mTextureView.setOnClickListener(this);
        mTextureView.setOnTouchListener(this);
        gallery.setOnClickListener(v -> ((AppMainActivity) getActivity()).loadFragment(FragmentGalleryNew.newInstance(), true));
        settings.setOnClickListener(v -> showDialogSettingsMain());
        appRepository = AppRepository.getInstance();

        mChronometer.setOnChronometerTickListener(arg0 -> {
//                if (!resume) {
            long time = SystemClock.elapsedRealtime() - mChronometer.getBase();
            int h = (int) (time / 3600000);
            int m = (int) (time - h * 3600000) / 60000;
            int s = (int) (time - h * 3600000 - m * 60000) / 1000;
            String t = (h < 10 ? "0" + h : h) + ":" + (m < 10 ? "0" + m : m) + ":" + (s < 10 ? "0" + s : s);
            mChronometer.setText(t);

            long minutes = ((SystemClock.elapsedRealtime() - mChronometer.getBase()) / 1000) / 60;
            long seconds = ((SystemClock.elapsedRealtime() - mChronometer.getBase()) / 1000) % 60;
            int elapsedMillis = (int) (SystemClock.elapsedRealtime() - mChronometer.getBase());
            timerSecond = (int) TimeUnit.MILLISECONDS.toSeconds(elapsedMillis);
//                    elapsedTime = SystemClock.elapsedRealtime();
            Log.d(TAG, "onChronometerTick: " + minutes + " : " + seconds);
//                } else {
//                    long minutes = ((elapsedTime - cmTimer.getBase())/1000) / 60;
//                    long seconds = ((elapsedTime - cmTimer.getBase())/1000) % 60;
//                    elapsedTime = elapsedTime + 1000;
//                    Log.d(TAG, "onChronometerTick: " + minutes + " : " + seconds);
//                }
        });


        minZoom = getMinZoom();
        maxZoom = (float) getMaxZoom() - 1;


        seekBar.setMax(Math.round(maxZoom - minZoom));
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        setCurrentZoom(Math.round((minZoom + 1) + (mProgress * zoomStep)));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        setCurrentZoom(Math.round((minZoom + 1) + (float) progress * zoomStep));
                        if (fromUser) mProgress = progress;
                    }
                }
        );


        return rootView;
    }

    private float getMinZoom() {
        return 0f;
    }

    private float getCurrentZoom(float zoomLevel) {
        return zoomLevel;
    }

    public void setCurrentZoom(float zoomLevel) {
        Rect zoomRect = getZoomRect(zoomLevel);
        if (zoomRect != null) {
            try {
                //you can try to add the synchronized object here
                mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
                mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
            } catch (Exception e) {
                Log.e(TAG, "Error updating preview: ", e);
            }
            this.zoomLevel = (float) zoomLevel;
        }
    }

    private Rect getZoomRect(float zoomLevel) {
        try {
            CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);

            String mCameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            float maxZoom = (characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM));
            Rect activeRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            if ((zoomLevel <= maxZoom) && (zoomLevel >= 1)) {

                float ratio = (float) 1 / zoomLevel; //This ratio is the ratio of cropped Rect to Camera's original(Maximum) Rect
                //croppedWidth and croppedHeight are the pixels cropped away, not pixels after cropped
                int croppedWidth = activeRect.width() - Math.round((float) activeRect.width() * ratio);
                int croppedHeight = activeRect.height() - Math.round((float) activeRect.height() * ratio);
                //Finally, zoom represents the zoomed visible area
                return new Rect(croppedWidth / 2, croppedHeight / 2,
                        activeRect.width() - croppedWidth / 2, activeRect.height() - croppedHeight / 2);
//                mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
//                return  zoom;
            } else if (zoomLevel == 0) {
                return new Rect(0, 0, activeRect.width(), activeRect.height());
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error during camera init");
            return null;
        }
    }


    public float getMaxZoom() {
        try {
            CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
            String mCameraId = manager.getCameraIdList()[0];
            return (manager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM));
        } catch (Exception e) {
            Log.e(TAG, "Error during camera init");
            return -1;
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }


    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.rec: {
                bottomContainer.setVisibility(View.INVISIBLE);
                recStartLayout.setVisibility(VISIBLE);

                // Once the record button is pressed we no longer need to capture clips since the
                // the user has started actively recording.
                if (recordClips) recordClips = false;

                recordPressed = true;
                startRecordingVideo();
                while (clipQueue.size() > 2) {
                    // removed clips older than the last 3, so that we have something to fallback on
                    clipQueue.remove().delete();
                }
                break;
            }

            case R.id.rec_stop: {
                bottomContainer.setVisibility(VISIBLE);
                recStartLayout.setVisibility(View.INVISIBLE);
                stopRecordingVideo();
                // we can restart recoding clips if it is required at this point
                attemptClipConcat();
                break;
            }

            case R.id.r_3_bookmark: {
                saveSnipTimeToLocal();
                break;
            }
            case R.id.zoom_out_btn: {


                if (getCurrentZoom(zoomLevel) <= (maxZoom + 1)) {
                    if (mProgress > minZoom) {
                        mProgress--;
                        setCurrentZoom(Math.round(minZoom + (mProgress * zoomStep)));
                        seekBar.setProgress((int) getCurrentZoom(zoomLevel));
                    } else {
                        mProgress = 0;
                    }

                }

                break;
            }

            case R.id.zoom_in_btn: {

                if (getCurrentZoom(zoomLevel) <= maxZoom) {

                    if (mProgress < maxZoom) {
                        mProgress++;
                        setCurrentZoom(Math.round(minZoom + (mProgress * zoomStep)));
                        seekBar.setProgress((int) getCurrentZoom(zoomLevel));
                    } else {
                        mProgress = 3;

                    }

                }

                break;

            }


            case R.id.r_2_shutter: {
                rlVideo.startAnimation(animBlink);
                blinkEffect.setVisibility(VISIBLE);
                blinkEffect.animate()
                        .alpha(02f)
                        .setDuration(100)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                blinkEffect.setVisibility(View.GONE);
                                blinkEffect.clearAnimation();
                            }
                        });

//                File filevideopath = new File(outputFilePath);
//
//                try {
//
//                    File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
//                            VIDEO_DIRECTORY_NAME1);
//                    // Create storage directory if it does not exist
//                    if (!mediaStorageDir.exists()) {
//                        if (!mediaStorageDir.mkdirs()) {
//                            return;
//                        }
//                    }
//                    File mediaFile = new File(mediaStorageDir.getPath() + File.separator
//                            + "PhotoTHUM_" + System.currentTimeMillis() + ".png");
//
//
//                    if (!mediaStorageDir.exists()) {
//                        if (!mediaStorageDir.mkdirs()) {
//                            Log.d(TAG, "Oops! Failed create "
//                                    + VIDEO_DIRECTORY_NAME1 + " directory");
//                            return;
//                        }
//                    }
//
//
//                    Log.d(TAG, "saving video thumbnail at path: " + mediaFile + ", video path: " + filevideopath.getAbsolutePath());
//                    //Save the thumbnail in a PNG compressed format, and close everything. If something fails, return null
//                    FileOutputStream streamThumbnail = new FileOutputStream(mediaFile);
//
//                    //Other method to get a thumbnail. The problem is that it doesn't allow to get at a specific time
//                    ; //= ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(),MediaStore.Images.Thumbnails.MINI_KIND)
//
//                    thumb=ThumbnailUtils.createVideoThumbnail(filevideopath.getAbsolutePath(), MediaStore.Images.Thumbnails.MICRO_KIND);
//
//
//
//
////                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
////                    try {
////                        retriever.setDataSource(filevideopath.getAbsolutePath());
////                        thumb = retriever.getFrameAtTime((int)  1000000,
////                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
////                        thumb.compress(Bitmap.CompressFormat.PNG, 80, streamThumbnail);
////                        thumb.recycle(); //ensure the image is freed;
////                    } catch (Exception ex) {
////                        Log.i(TAG, "MediaMetadataRetriever got exception:" + ex);
////                    }
////                    streamThumbnail.close();
//                    //update Snip
//
//                    Log.d(TAG, "thumbnail saved successfully");
//                } catch (FileNotFoundException e) {
//                    Log.d(TAG, "File Not Found Exception : check directory path");
//                    e.printStackTrace();
//                } catch (IOException e) {
//                    Log.d(TAG, "IOException while closing the stream");
//                    e.printStackTrace();
//                }

                getVideoThumbnailclick(new File(outputFilePath));
                rlVideo.clearAnimation();
                break;
            }

            case R.id.texture: {
                saveSnipTimeToLocal();
            }
        }
    }

    private void attemptClipConcat() {
        if(clipQueue.size() >= 2){
            File clip1, clip2;
            clip1 = clipQueue.remove();
            clip2 = clipQueue.remove();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String mergeFilePath = "merged-"+timeStamp+".mp4";

            if(processingDialog == null)
                processingDialog = new ProcessingDialog(getActivity());
            processingDialog.show();

            mergeUtils.concatenateFiles(clip1, clip2, clip1.getParent()+"/"+mergeFilePath);
        }
    }

    protected void showDialogSettingsMain() {

        final Dialog dialog = new Dialog(getActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.fragment_settings);

        RelativeLayout con6 = dialog.findViewById(R.id.con6);
        con6.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialogSettingsResolution();
            }
        });
        RelativeLayout feedback = dialog.findViewById(R.id.con2);
        feedback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((AppMainActivity) getActivity()).loadFragment(Feedback_fragment.newInstance(), true);
                dialog.dismiss();
            }
        });

        dialog.show();
    }


    protected void showDialogSettingsResolution() {

        Dialog dialog = new Dialog(getActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.fragment_resolution);

        dialog.show();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private boolean shouldShowRequestPermissionRationale(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Requests permissions needed for recording video.
     */
    private void requestVideoPermissions() {
        if (shouldShowRequestPermissionRationale(VIDEO_PERMISSIONS)) {
            //new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            ActivityCompat.requestPermissions(getActivity(), VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
            if (grantResults.length == VIDEO_PERMISSIONS.length) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {

                      /*  ErrorDialog.newInstance(getString(R.string.permission_request))
                                .show(getChildFragmentManager(), FRAGMENT_DIALOG);*/
                        break;
                    }
                }
            } else {
               /* ErrorDialog.newInstance(getString(R.string.permission_request))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);*/
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


    /**
     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
     */
    @SuppressWarnings("MissingPermission")
    private void openCamera(int width, int height) {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            requestVideoPermissions();
            return;
        }
        final Activity activity = getActivity();
        if (null == activity || activity.isFinishing()) {
            return;
        }
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            Log.d(TAG, "tryAcquire");
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            String cameraId = manager.getCameraIdList()[0];

            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));

            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, mVideoSize);

            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            configureTransform(width, height);
            mMediaRecorder = new MediaRecorder();
            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                requestPermission();
                return;
            }
            manager.openCamera(cameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            activity.finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            /*ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);*/
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    public void requestPermission() {
        Dexter.withActivity(getActivity()).withPermissions(Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        // check if all permissions are granted or not
                        if (report.areAllPermissionsGranted()) {
                            if (mTextureView.isAvailable()) {
                                openCamera(mTextureView.getWidth(), mTextureView.getHeight());
                            } else {
                                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
                            }
                        }
                        // check for permanent denial of any permission show alert dialog
                        if (report.isAnyPermissionPermanentlyDenied()) {
                            // open Settings activity
                            showSettingsDialog();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).withErrorListener(error -> Toast.makeText(getActivity().getApplicationContext(), "Error occurred! ", Toast.LENGTH_SHORT).show())
                .onSameThread()
                .check();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getString(R.string.message_need_permission));
        builder.setMessage(getString(R.string.message_permission));
        builder.setPositiveButton(getString(R.string.title_go_to_setting), (dialog, which) -> {
            dialog.cancel();
            openSettings();
        });
        builder.show();

    }

    // navigating settings app
    private void openSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getActivity().getPackageName(), null);
        intent.setData(uri);
        startActivityForResult(intent, 101);
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() {
        Log.d(TAG, "AVA start preview");
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);


            Surface previewSurface = new Surface(texture);
            mPreviewBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mPreviewSession = session;
//                            updatePreview();
                            //  calling capture request without starting another thread.
                            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                            try {
                                mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Activity activity = getActivity();
                            if (null != activity) {
                                Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (IllegalStateException e1) {
            e1.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(final CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);


    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY);
        }

        mTextureView.setTransform(matrix);
    }

    /**
     * Sets up the media recorder for capturing the video from the surface
     * will output as H264 MPEG4 with AAC audio from the mic.
     * <p>
     * if recordClips is true, mMediaRecorder.setMaxDuration is set to clipDuration
     * this is monitored using infoListener and the recording is restarted automatically.
     * else recording proceeds as normal.
     *
     * @throws IOException
     */
    private void setUpMediaRecorder() throws IOException {
        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }

        mMediaRecorder.reset();
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//        if (outputFilePath == null || outputFilePath.isEmpty()) {
        outputFilePath = getOutputMediaFile().getAbsolutePath();
//        }
        mMediaRecorder.setOutputFile(outputFilePath);
        if (recordClips) {    //  so that the actual recording is not affected by clip duration.
            mMediaRecorder.setMaxDuration(clipDuration.intValue());
        }
//        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        CamcorderProfile profile = chooseCamcorderProfile();
        mMediaRecorder.setVideoFrameRate(profile.videoFrameRate);
        mMediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setAudioEncodingBitRate(profile.audioBitRate);
        mMediaRecorder.setAudioSamplingRate(profile.audioSampleRate);

//        mMediaRecorder.setVideoEncodingBitRate(10000000);
//        mMediaRecorder.setVideoFrameRate(30);
//        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
//        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED && recordClips) {
                    try {
                        startRecordingVideo();
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        mMediaRecorder.prepare();
    }

    private String getVideoFilePath(Context context) {
        final File dir = context.getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis() + ".mp4";
    }


    private File getOutputMediaFile() {
        if (clipQueue == null) {
            clipQueue = new LinkedList<>();
        }

        // External sdcard file location
        File mediaStorageDir = new File(requireActivity().getDataDir(),
                VIDEO_DIRECTORY_NAME);
        // Create storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "Oops! Failed create "
                        + VIDEO_DIRECTORY_NAME + " directory");
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        File mediaFile;

        mediaFile = new File(mediaStorageDir.getPath() + File.separator
                + "VID_" + timeStamp + ".mp4");

        //  adds the created clips to queue
//        if (recordClips) {
        Log.d(TAG, "clip added to queue");
        clipQueue.add(mediaFile);
//        }

        return mediaFile;
    }

    /**
     * Sets up the media recorder and starts the recording session
     */
    private void startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
//            closePreviewSession();

            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
//                    updatePreview();

                    mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    try {
                        mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }

                    mIsRecordingVideo = true;
                    mMediaRecorder.start();
                    /*getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // UI
                            // mButtonVideo.setText(R.string.stop);
                            mIsRecordingVideo = true;
                            // Start recording
                            mMediaRecorder.start();
                        }
                    });*/
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }

        //  we don't need to show the chronometer till the user presses the record button.
        if (recordPressed) {
            actualClipTime = SystemClock.elapsedRealtime() - actualClipTime;    // clip has ended
            mChronometer.setBase(SystemClock.elapsedRealtime());
            mChronometer.start();
            mChronometer.setVisibility(View.VISIBLE);
        } else{
            //  keeps a count of how long each clip was
            actualClipTime = SystemClock.elapsedRealtime() - actualClipTime;
        }
    }

    /**
     * Gets the best Camcorder profile based on the preview dimensions.
     *
     * @return CamcorderProfile
     */
    private CamcorderProfile chooseCamcorderProfile() {
        ArrayList<Integer> cameraProfiles = new ArrayList<>();
        ArrayList<CamcorderProfile> candidateProfiles = new ArrayList<>();

        cameraProfiles.add(CamcorderProfile.QUALITY_2160P);
        cameraProfiles.add(CamcorderProfile.QUALITY_1080P);
        cameraProfiles.add(CamcorderProfile.QUALITY_720P);
        cameraProfiles.add(CamcorderProfile.QUALITY_480P);

        for (int p : cameraProfiles) {
            if (CamcorderProfile.hasProfile(p)) {
                CamcorderProfile tmp = CamcorderProfile.get(p);
                if (tmp.videoFrameWidth <= mPreviewSize.getWidth() && tmp.videoFrameHeight <= mPreviewSize.getHeight() ||
                        tmp.videoFrameWidth <= mPreviewSize.getHeight() && tmp.videoFrameHeight <= mPreviewSize.getWidth())
                    candidateProfiles.add(tmp);
            }
        }

        Comparator<CamcorderProfile> comparator = new Comparator<CamcorderProfile>() {
            @Override
            public int compare(CamcorderProfile p1, CamcorderProfile p2) {
                if (p1 != null && p2 != null)
                    return (p2.videoFrameWidth * p2.videoFrameHeight) - (p1.videoFrameWidth * p1.videoFrameHeight);
                else
                    return 0;
            }
        };

        candidateProfiles.sort(comparator);
        if (candidateProfiles.size() != 0)
            return candidateProfiles.get(0);
        return CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    /**
     * Stops the mediaRecorder and resets it.
     * <p>
     * Adds snip details
     */
    private void stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false;
        //  mButtonVideo.setText(R.string.record);
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        mChronometer.stop();
        mChronometer.setVisibility(View.INVISIBLE);
        mChronometer.setText("");
        clipQueue.forEach(file -> {
            int currentClipDuration;

            if(file == clipQueue.peek())
                currentClipDuration = (int) TimeUnit.MILLISECONDS.toSeconds(actualClipTime);
            else
                currentClipDuration = timerSecond;

            totalDuration[0] += currentClipDuration;
            addSnip(file.getAbsolutePath(), currentClipDuration, /*totalDuration[0]*/ currentClipDuration); //
        });
        outputFilePath = null;
        startPreview();
    }

    private void addSnip(String snipFilePath, int snipDuration, int totalDuration) {
        Activity activity = requireActivity();
        //            Toast.makeText(activity, "Video saved: " + outputFilePath,
//                    Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Video saved: " + outputFilePath);
        Snip parentSnip = new Snip();
        parentSnip.setStart_time(0);
        parentSnip.setEnd_time(0);
        parentSnip.setIs_virtual_version(0);
        parentSnip.setParent_snip_id(0);
        parentSnip.setSnip_duration(snipDuration);
        parentSnip.setTotal_video_duration(totalDuration);
        parentSnip.setVid_creation_date(System.currentTimeMillis());
        parentSnip.setEvent_id(AppClass.getAppInstance().getLastEventId());
        if (AppClass.getAppInstance().getSnipDurations().size() > 0) {
            parentSnip.setHas_virtual_versions(1);
        } else {
            parentSnip.setHas_virtual_versions(0);
        }
        AppClass.getAppInstance().setInsertionInProgress(true);
        appRepository.insertSnip(this, parentSnip);
        parentSnip.setVideoFilePath(snipFilePath);
//            AppClass.getAppInsatnce().saveAllSnips(parentSnip);
//            AppClass.getAppInsatnce().saveAllEventSnips();
//            AppClass.getAppInsatnce().saveAllParentSnips(parentSnip);
//            AppClass.getAppInsatnce().setEventParentSnips();
//        hdSnips = new Hd_snips();
//        hdSnips.setVideo_path_processed(snipFilePath);
//            parentSnip.setVideoFilePath(outputFilePath);
//            AppClass.getAppInsatnce().saveAllSnips(parentSnip);
    }

    @Override
    public void onTaskCompleted(Snip snip) {
        //  todo: merged and trimmed file needs to be added in HD_snip nothing else
        if (snip.getIs_virtual_version() == 0) {
//            snip.setSnip_id(AppClass.getAppInstance().getLastSnipId());
            hdSnips = new Hd_snips();
            hdSnips.setVideo_path_processed(snip.getVideoFilePath());
//            hdSnips.setSnip_id(AppClass.getAppInstance().getLastSnipId());
            hdSnips.setSnip_id(snip.getSnip_id());
            appRepository.insertHd_snips(hdSnips);
            saveSnipToDB(snip, hdSnips.getVideo_path_processed());
        }
        getVideoThumbnail(snip, new File(hdSnips.getVideo_path_processed()));
    }


    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final android.app.Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.permission_request)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent, VIDEO_PERMISSIONS,
                                    REQUEST_VIDEO_PERMISSIONS);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    parent.getActivity().finish();
                                }
                            })
                    .create();
        }

    }

    public void saveSnipToDB(Snip parentSnip, String filePath) {
//        String chronoText = mChronometer.getText().toString();
//        Log.e("chrono m reading",chronoText);
        List<Integer> snipDurations = AppClass.getAppInstance().getSnipDurations();
        if (snipDurations.size() > 0) {
            Event event = AppClass.getAppInstance().getLastCreatedEvent();
//            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy");
//            String currentDateandTime = sdf.format(new Date());
//            EventData eventData = new EventData();
//            eventData.setEvent_id(event.getEvent_id());
//            eventData.setEvent_title(event.getEvent_title());
//            eventData.setEvent_created(event.getEvent_created());

            for (int endSecond : snipDurations) {
                int startSecond = Math.max((endSecond - 5), 0);
                Snip snip = new Snip();
                snip.setStart_time(startSecond);
                snip.setEnd_time(endSecond);
                snip.setIs_virtual_version(1);
                snip.setHas_virtual_versions(0);
                snip.setParent_snip_id(parentSnip.getSnip_id());
                snip.setSnip_duration(endSecond - startSecond);
                snip.setVid_creation_date(System.currentTimeMillis());
                snip.setEvent_id(event.getEvent_id());
                appRepository.insertSnip(this, snip);
//                parentSnip.setHas_virtual_versions(1);
//                appRepository.updateSnip(parentSnip);
                snip.setVideoFilePath(filePath);
//                eventData.addEventSnip(snip);
//                AppClass.getAppInsatnce().saveAllSnips(snip);
//                eventData.addEventSnip(parentSnip);
            }
//            AppClass.getAppInsatnce().saveAllEventSnips();
            AppClass.getAppInstance().clearSnipDurations();
        }

//        AppViewModel appViewModel = ViewModelProviders.of(this).get(AppViewModel.class);
//        appViewModel.getSnipsLiveData().observe(this, snips -> {
//            if(snips.size() > 0) {
//                for (Snip snip : snips) {
//                    if (snip.getParent_snip_id() == parentSnip.getSnip_id() || snip.getSnip_id() == parentSnip.getSnip_id()) {
//                        getVideoThumbnail(snip, new File(filePath), snips.indexOf(snip) == snips.size() - 1);
//                    }
//                }
//            }
//            ((AppMainActivity) getActivity()).loadFragment(FragmentGalleryNew.newInstance());

//        });

        //TODO
    }

    private void saveSnipTimeToLocal() {
        if (timerSecond != 0) {

            int endSecond = timerSecond;
            AppClass.getAppInstance().setSnipDurations(endSecond);
            // on screen tap blinking starts
            rlVideo.startAnimation(animBlink);
            blinkEffect.setVisibility(VISIBLE);
            blinkEffect.animate()
                    .alpha(02f)
                    .setDuration(100)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            blinkEffect.setVisibility(View.GONE);
                            blinkEffect.clearAnimation();
                            rlVideo.clearAnimation();
                        }
                    });

//        Log.d("seconds", String.valueOf(endSecond));
        }
    }

    private void getVideoThumbnail(Snip snip, File videoFile) {
        try {
//            File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
//                    VIDEO_DIRECTORY_NAME);
            File thumbsStorageDir = new File(requireActivity().getDataDir() + "/" + VIDEO_DIRECTORY_NAME,
                    THUMBS_DIRECTORY_NAME);

            if (!thumbsStorageDir.exists()) {
                if (!thumbsStorageDir.mkdirs()) {
                    Log.d(TAG, "Oops! Failed create "
                            + VIDEO_DIRECTORY_NAME + " directory");
                    return;
                }
            }
            File fullThumbPath;

            fullThumbPath = new File(thumbsStorageDir.getPath() + File.separator
                    + "snip_" + snip.getSnip_id() + ".png");
            Log.d(TAG, "saving video thumbnail at path: " + fullThumbPath + ", video path: " + videoFile.getAbsolutePath());
            //Save the thumbnail in a PNG compressed format, and close everything. If something fails, return null
            FileOutputStream streamThumbnail = new FileOutputStream(fullThumbPath);

            //Other method to get a thumbnail. The problem is that it doesn't allow to get at a specific time
            Bitmap thumb; //= ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(),MediaStore.Images.Thumbnails.MINI_KIND);
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(videoFile.getAbsolutePath());
                if (snip.getIs_virtual_version() != 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        thumb = retriever.getScaledFrameAtTime((int) snip.getStart_time() * 1000000,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 100, 100);
                    } else {
                        thumb = retriever.getFrameAtTime((int) snip.getStart_time() * 1000000,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    }
                } else {
                    thumb = retriever.getFrameAtTime();
                }
                thumb.compress(Bitmap.CompressFormat.PNG, 80, streamThumbnail);
                thumb.recycle(); //ensure the image is freed;
            } catch (Exception ex) {
                Log.i(TAG, "MediaMetadataRetriever got exception:" + ex);
            }
            streamThumbnail.close();
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
            Log.d(TAG, "thumbnail saved successfully");
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File Not Found Exception : check directory path");
            e.printStackTrace();
        } catch (IOException e) {
            Log.d(TAG, "IOException while closing the stream");
            e.printStackTrace();
        }
    }

    private void getVideoThumbnailclick(File videoFile) {
        try {

            File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                    VIDEO_DIRECTORY_NAME1);
            // Create storage directory if it does not exist
            if (!mediaStorageDir.exists()) {
                if (!mediaStorageDir.mkdirs()) {
                    return;
                }
            }
            File mediaFile = new File(mediaStorageDir.getPath() + File.separator
                    + "PhotoTHUM_" + System.currentTimeMillis() + ".png");


            if (!mediaStorageDir.exists()) {
                if (!mediaStorageDir.mkdirs()) {
                    Log.d(TAG, "Oops! Failed create "
                            + VIDEO_DIRECTORY_NAME1 + " directory");
                    return;
                }
            }

            Log.d(TAG, "saving video thumbnail at path: " + mediaFile + ", video path: " + videoFile.getAbsolutePath());
            //Save the thumbnail in a PNG compressed format, and close everything. If something fails, return null
            FileOutputStream streamThumbnail = new FileOutputStream(mediaFile);

            //Other method to get a thumbnail. The problem is that it doesn't allow to get at a specific time
            Bitmap thumb; //= ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(),MediaStore.Images.Thumbnails.MINI_KIND);
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(videoFile.getAbsolutePath());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    thumb = retriever.getScaledFrameAtTime((int) 1 * 1000000,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 100, 100);
                } else {
                    thumb = retriever.getFrameAtTime((int) 1 * 1000000,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                }


                //     thumb = retriever.getFrameAtTime();


                thumb.compress(Bitmap.CompressFormat.PNG, 80, streamThumbnail);
                thumb.recycle(); //ensure the image is freed;
            } catch (Exception ex) {
                Log.i(TAG, "MediaMetadataRetriever got exception:" + ex);
            }
            streamThumbnail.close();

            Log.d(TAG, "thumbnail saved successfully");
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File Not Found Exception : check directory path");
            e.printStackTrace();
        } catch (IOException e) {
            Log.d(TAG, "IOException while closing the stream");
            e.printStackTrace();
        }
    }

//    public void dispatchTouchEvent(MotionEvent me) {
//        // Call onTouchEvent of SimpleGestureFilter class
//        this.detector.onTouchEvent(me);
//    }

//    @Override
//    public void onSwipe(int direction) {
//
//        //Detect the swipe gestures and display toast
//        String showToastMessage = "";
//
//        switch (direction) {
//
//            case GestureFilter.SWIPE_RIGHT:
//                showToastMessage = "You have Swiped Right.";
//                break;
//            case GestureFilter.SWIPE_LEFT:
//                showToastMessage = "You have Swiped Left.";
//                break;
//            case GestureFilter.SWIPE_DOWN:
//                showToastMessage = "You have Swiped Down.";
//                break;
//            case GestureFilter.SWIPE_UP:
//                showToastMessage = "You have Swiped Up.";
//                break;
//
//        }
//        Toast.makeText(getActivity(), showToastMessage, Toast.LENGTH_SHORT).show();
//    }


//    public  void accessRoomDatabase(){
//        //Inserting data to Table
//        Event event = new Event();
//        Snip snip = new Snip();
//        Hd_snips hd_snips = new Hd_snips();
//        event.setEvent_title("test data");
//        event.setEvent_created(345678987);
//        AppRepository appRepository = AppRepository.getInstance();
//        //Insert Data
////        appRepository.insertEvent(event);
//
//        AppViewModel appViewModel = ViewModelProviders.of(this).get(AppViewModel.class);
//        //Retriving Data from table
//        appViewModel.getEventLiveData().observe(this, new Observer<List<Event>>() {
//            @Override
//            public void onChanged(List<Event> events) {
//                Log.e("data loaded","data loaded");
//
//                ///Handle data here
//            }
//        });
//        //Updating Data
//        appRepository.updateEvent(event);
//
//        //Delete data
//        appRepository.deleteEvent(event);
//
//    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnTaskCompleted) {
            thumbnailProcesingCompleted = (OnTaskCompleted) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnGreenFragmentListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        thumbnailProcesingCompleted = null;
    }

//    clip edit listener
    @Override
    public void failed(VideoOp operation) {

    }

    @Override
    public void changed(VideoOp operation, String videoPath) {
        switch (operation){
            case MERGED:    // merge was done successfully
                break;
            case CONCAT:    // concatenation was done successfully
                if (processingDialog != null) {
                    processingDialog.dismiss();
                }
                //  Associating with the latest HD_snip entry; getLastHDSnipID
                hdSnips.setVideo_path_unprocessed(videoPath);
                hdSnips.setHd_snip_id((int) AppClass.getAppInstance().getLastHDSnipId());
                appRepository.updateHDSnip(hdSnips);
                break;
            case TRIMMED:
                break;
            case SPEED:
                break;
            case FRAMES:
                break;
            case KEY_FRAMES:
                break;
            case ERROR:
                break;
        }
    }
}