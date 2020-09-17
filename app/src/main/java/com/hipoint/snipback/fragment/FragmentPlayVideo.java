package com.hipoint.snipback.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.hipoint.snipback.AppMainActivity;
import com.hipoint.snipback.R;
import com.hipoint.snipback.Utils.CommonUtils;
import com.hipoint.snipback.Utils.OnSwipeTouchListener;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.DefaultTimeBar;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.hipoint.snipback.Utils.TrimmerUtils;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.Hd_snips;
import com.hipoint.snipback.room.entities.Snip;
import com.hipoint.snipback.room.repository.AppRepository;
import com.hipoint.snipback.room.repository.AppViewModel;
import com.kaopiz.kprogresshud.KProgressHUD;

import java.io.File;
import java.util.Objects;

import Jni.FFmpegCmd;
import VideoHandle.OnEditorListener;

public class FragmentPlayVideo extends Fragment {
    private View rootView;
    ImageView tag;
    private BandwidthMeter bandwidthMeter;
    private TrackSelector trackSelector;
    private TrackSelection.Factory trackSelectionFactory;
    private SimpleExoPlayer player;
    private DataSource.Factory dataSourceFactory;
    private ExtractorsFactory extractorsFactory;
    private DefaultBandwidthMeter defaultBandwidthMeter;
    private MediaSource mediaSource;
    private Uri uri;
    private PlayerView simpleExoPlayerView;
    private String uriType;

    private Switch play_pause;
    private boolean play = true;
    private TextView exo_duration;

    private DefaultTimeBar exo_progress;
    private long current_posi;

    // new
    private float seekdistance = 0;
    float initialX, initialY, currentX, currentY;
    float condition2;

    private Event event;
    private AppRepository appRepository;
    private AppViewModel appViewModel;
    private ImageButton tvConvertToReal;

    // new added
    private Snip snip;
    private RelativeLayout back_arrow, button_camera;
    boolean paused = false;


    public static FragmentPlayVideo newInstance(Snip snip) {
        FragmentPlayVideo fragment = new FragmentPlayVideo();
        Bundle bundle = new Bundle();
        bundle.putParcelable("snip", snip);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.layout_play_video, container, false);

        appRepository = new AppRepository(requireActivity().getApplicationContext());
        appViewModel = ViewModelProviders.of(this).get(AppViewModel.class);
        snip = requireArguments().getParcelable("snip");
        appViewModel.getEventByIdLiveData(Objects.requireNonNull(snip).getEvent_id()).observe(getViewLifecycleOwner(), snipevent -> {
            event = snipevent;
        });
        exo_duration = rootView.findViewById(R.id.exo_duration);
        button_camera = rootView.findViewById(R.id.button_camera);
        back_arrow = rootView.findViewById(R.id.back_arrow);


//        uri = Uri.parse(getArguments().getString("uri"));
        bandwidthMeter = new DefaultBandwidthMeter();
        extractorsFactory = new DefaultExtractorsFactory();
        trackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        trackSelector = new DefaultTrackSelector(trackSelectionFactory);
        defaultBandwidthMeter = new DefaultBandwidthMeter();
        dataSourceFactory = new DefaultDataSourceFactory(getActivity(),
                Util.getUserAgent(getActivity(), "mediaPlayerSample"), defaultBandwidthMeter);
        mediaSource = new ExtractorMediaSource(Uri.parse(snip.getVideoFilePath()),
                dataSourceFactory,
                extractorsFactory,
                null,
                null);

        player = ExoPlayerFactory.newSimpleInstance(getActivity(), trackSelector);
        exo_progress = rootView.findViewById(R.id.exo_progress);

        tvConvertToReal = rootView.findViewById(R.id.tvConvertToReal);
        tvConvertToReal.setOnClickListener(view -> validateVideo(snip));
        if ((snip != null ? snip.getIs_virtual_version() : 0) == 1) {
            tvConvertToReal.setVisibility(View.VISIBLE);
        }else{
            tvConvertToReal.setVisibility(View.GONE);
        }
        simpleExoPlayerView = (PlayerView) rootView.findViewById(R.id.player_view);
        simpleExoPlayerView.setOnTouchListener(new OnSwipeTouchListener(getActivity()) {

            public void onSwipeTop() {
//                Toast.makeText(getActivity(), "top", Toast.LENGTH_SHORT).show();
            }

            public void onSwipeRight(float diffX) {

                if (player.getCurrentPosition() < player.getDuration()) {

                    player.seekTo((player.getCurrentPosition() + (long) diffX));
                    simpleExoPlayerView.showController();
                } else if (player.getCurrentPosition() == player.getDuration()) {
                    player.seekTo(0);
                    simpleExoPlayerView.showController();
                } else {
                    player.seekTo(0);
                    simpleExoPlayerView.showController();
                }


            }

            public void onSwipeLeft(float diffX) {
                if (player.getCurrentPosition() == 0) {

                } else {
                    player.seekTo((player.getCurrentPosition() - (long) diffX));
                    simpleExoPlayerView.showController();
                }

                // changeSeek(diffX,diffY,distanceCovered,"X");
            }

            public void onSwipeBottom() {
                Toast.makeText(getActivity(), "bottom", Toast.LENGTH_SHORT).show();
            }

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                simpleExoPlayerView.showController();
                return super.onTouch(v, event);
            }


        });
        simpleExoPlayerView.setPlayer(player);
        simpleExoPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
        player.prepare(mediaSource);
        player.setPlayWhenReady(true);


        back_arrow.setOnClickListener(v -> {
            player.release();
            getActivity().onBackPressed();
        });

        button_camera.setOnClickListener(v -> {
//            (AppMainActivity).loadFragment(VideoMode.newInstance(),true);
            player.release();
            Intent intent1 = new Intent(getActivity(), AppMainActivity.class);
            startActivity(intent1);
            getActivity().finishAffinity();
        });

        exo_progress.setVisibility(View.INVISIBLE);

        // can hide seekbar in exoplayer
//        exo_progress.setVisibility(View.INVISIBLE);

        if (snip.getIs_virtual_version() == 1) {
            exo_progress.setDuration((long)snip.getSnip_duration() * 1000);
            player.seekTo(player.getCurrentPosition() + (long) snip.getStart_time() * 1000);
            new CountDownTimer((long) snip.getSnip_duration() * 1000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {

                }

                @Override
                public void onFinish() {

//                        videoView.stopPlayback();
//                        videoView.resume();
//                        play_pause.setChecked(true);

                    if (player != null) {
                        player.setPlayWhenReady(false);
                        player.stop();
                        current_posi = player.getCurrentPosition();

                    }

                }
            }.start();
        }


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
//            ( getActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
//        } else {
//            ( getActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
//        }

//        OrientationEventListener mOrientationListener = new OrientationEventListener(
//                getActivity()) {
//            @Override
//            public void onOrientationChanged(int orientation) {
//                if(!getActivity().isFinishing()) {
//                    if (orientation == 0 || orientation == 180) {
//                        (getActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
//                    } else if (orientation == 90 || orientation == 270) {
//                        (getActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
//                    }
//                }
//            }
//        };
//
//        if (mOrientationListener.canDetectOrientation()) {
//            mOrientationListener.enable();
//        }

        play_pause = rootView.findViewById(R.id.play_pause);
        play_pause.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    if (player != null) {
                        player.setPlayWhenReady(false);
                        player.getPlaybackState();
                        paused = true;
//                        current_posi = player.getCurrentPosition();
                    }
                } else {
                    paused = false;
                    if (snip.getIs_virtual_version() == 1) {
//                        player.prepare(mediaSource);
//                        player.setPlayWhenReady(true);
                        player.setPlayWhenReady(true);
                        player.getPlaybackState();
                        player.seekTo(player.getCurrentPosition()+(long) snip.getStart_time());
                        current_posi = player.getCurrentPosition();
                        new CountDownTimer((long) snip.getSnip_duration() * 1000 - current_posi, 1000) {
                            @Override
                            public void onTick(long millisUntilFinished) {

                            }

                            @Override
                            public void onFinish() {

                                if (player != null && paused==false) {
                                    player.setPlayWhenReady(false);
                                    player.stop();
                                    current_posi = player.getCurrentPosition();

                                }

                            }
                        }.start();
                    }
//                    player.prepare(mediaSource);
                    player.setPlayWhenReady(true);
                    player.getPlaybackState();
                    player.seekTo(player.getCurrentPosition() + 100);


//////                    player.setPlayWhenReady(true);
//                    player.setPlayWhenReady(!player.getPlayWhenReady());

                }
            }
        });


        player.addListener(new ExoPlayer.EventListener() {
            @Override
            public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
                Log.e("failure", String.valueOf(timeline));


            }

            @Override
            public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

            }

            @Override
            public void onLoadingChanged(boolean isLoading) {

            }

            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
//                updateProgressBar();
            }

            @Override
            public void onRepeatModeChanged(int repeatMode) {

            }

            @Override
            public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {

            }

            @Override
            public void onPositionDiscontinuity(int reason) {

            }

            @Override
            public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

            }

            @Override
            public void onSeekProcessed() {

            }
        });


        tag = rootView.findViewById(R.id.tag);
        tag.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                ((AppMainActivity) getActivity()).loadFragment(CreateTag.newInstance(), true);
            }
        });

        rootView.setFocusableInTouchMode(true);
        rootView.requestFocus();
        rootView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    player.release();
                    getActivity().onBackPressed();
//                    ((AppMainActivity) getActivity()).loadFragment(FragmentGalleryNew.newInstance(), true);

                    return true;
                }
                return false;
            }
        });

        return rootView;
    }

    private String VIDEO_DIRECTORY_NAME = "Snipback";
    private void validateVideo(Snip snip) {
        String destinationPath = snip.getVideoFilePath();

        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                VIDEO_DIRECTORY_NAME);
        // Create storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return;
            }
        }
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator
                + "VID_" + System.currentTimeMillis() + ".mp4");

        String[] complexCommand = {"ffmpeg", "-i", String.valueOf(destinationPath), "-ss", TrimmerUtils.formatCSeconds((long) snip.getStart_time()),
                "-to", TrimmerUtils.formatCSeconds((long) snip.getEnd_time()), "-async", "1", String.valueOf(mediaFile)};
        KProgressHUD hud = CommonUtils.showProgressDialog(getActivity());
        FFmpegCmd.exec(complexCommand, 0, new OnEditorListener() {
            @Override
            public void onSuccess() {
                snip.setIs_virtual_version(0);
                snip.setVideoFilePath(mediaFile.getAbsolutePath());
                AppClass.getAppInstance().setEventSnipsFromDb(event,snip);
                appRepository.updateSnip(snip);
                Hd_snips hdSnips = new Hd_snips();
                hdSnips.setVideo_path_processed(mediaFile.getAbsolutePath());
                hdSnips.setSnip_id(snip.getSnip_id());
                appRepository.insertHd_snips(hdSnips);
                AppClass.getAppInstance().setInsertionInProgress(true);
                if (hud.isShowing())
                    hud.dismiss();
                getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), "Video saved to gallery", Toast.LENGTH_SHORT).show());

//                appViewModel.loadGalleryDataFromDB(ActivityPlayVideo.this);

            }

            @Override
            public void onFailure() {
                if (hud.isShowing())
                    hud.dismiss();
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getActivity(), "Failed to trim", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onProgress(float progress) {
            }
        });
    }


}

