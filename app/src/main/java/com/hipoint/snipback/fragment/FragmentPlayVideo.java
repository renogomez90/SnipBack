package com.hipoint.snipback.fragment;

import android.content.pm.ActivityInfo;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.exoplayer2.ui.PlayerControlView;
import com.hipoint.snipback.AppMainActivity;
import com.hipoint.snipback.R;
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
import com.hipoint.snipback.room.entities.Snip;

import static android.content.Context.WINDOW_SERVICE;

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

    // new added
    private Snip snip;


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

        snip = getArguments().getParcelable("snip");
        exo_duration = rootView.findViewById(R.id.exo_duration);

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

        simpleExoPlayerView = (PlayerView) rootView.findViewById(R.id.player_view);
        simpleExoPlayerView.setOnTouchListener(new OnSwipeTouchListener(getActivity()) {

            public void onSwipeTop() {
                Toast.makeText(getActivity(), "top", Toast.LENGTH_SHORT).show();
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
        player.prepare(mediaSource);
        player.setPlayWhenReady(true);


        if (snip.getIs_virtual_version() == 1) {
            exo_progress.setDuration(5000);
            player.seekTo(player.getCurrentPosition() +(long) snip.getStart_time()*1000);
            new CountDownTimer( (long) snip.getSnip_duration() * 1000, 1000) {
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

        OrientationEventListener mOrientationListener = new OrientationEventListener(
                getActivity()) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == 0 || orientation == 180) {
                    (getActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                } else if (orientation == 90 || orientation == 270) {
                    (getActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                }
            }
        };

        if (mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        }

        play_pause = rootView.findViewById(R.id.play_pause);
        play_pause.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    if (player != null) {
                        player.setPlayWhenReady(false);
                        player.stop();
//                        current_posi = player.getCurrentPosition();

                    }
                } else {


                    if (snip.getIs_virtual_version() == 1) {

                        player.prepare(mediaSource);
                        player.setPlayWhenReady(true);
                        player.seekTo(player.getCurrentPosition());
                        current_posi = player.getCurrentPosition();
                        new CountDownTimer((long) snip.getSnip_duration() * 1000 - current_posi, 1000) {
                            @Override
                            public void onTick(long millisUntilFinished) {

                            }

                            @Override
                            public void onFinish() {

                                if (player != null) {
                                    player.setPlayWhenReady(false);
                                    player.stop();
                                    current_posi = player.getCurrentPosition();

                                }

                            }
                        }.start();
                    } else {
                        player.prepare(mediaSource);
                        player.setPlayWhenReady(true);
                        player.seekTo(current_posi + 100);
                    }

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
                ((AppMainActivity) getActivity()).loadFragment(CreateTag.newInstance(), true);
            }
        });

        rootView.setFocusableInTouchMode(true);
        rootView.requestFocus();
        rootView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    player.release();
                    ((AppMainActivity) getActivity()).loadFragment(FragmentGalleryNew.newInstance(), true);
                    return true;
                }
                return false;
            }
        });

        return rootView;


    }


}

