package com.hipoint.snipback.fragment;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.hipoint.snipback.R;
import com.hipoint.snipback.room.entities.Snip;

public class VideoEditingFragment extends Fragment {
    private String TAG = VideoEditingFragment.class.getSimpleName();

    //    UI
    private View rootView;
    ImageView back, back1, save, close;
    RelativeLayout layout_extent, play_con;
    LinearLayout play_con1, play_con2;
    ImageButton extent;
    TextView extent_text, end, start;
    ImageButton playBtn;

    //    Exoplayer
    private PlayerView playerView;
    private SimpleExoPlayer player;
    private DefaultBandwidthMeter defaultBandwidthMeter;
    private DataSource.Factory dataSourceFactory;
    private MediaSource mediaSource;

    //    Snip
    private Snip snip;

    public static VideoEditingFragment newInstance(Snip aSnip) {
        VideoEditingFragment fragment = new VideoEditingFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable("snip", aSnip);
        fragment.setArguments(bundle);

        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.video_editing_fragment_main2, container, false);

        layout_extent = rootView.findViewById(R.id.layout_extent);
        playerView = rootView.findViewById(R.id.player_view);
        play_con = rootView.findViewById(R.id.play_con);
        play_con1 = rootView.findViewById(R.id.play_con1);
        play_con2 = rootView.findViewById(R.id.play_con2);
        extent = rootView.findViewById(R.id.extent);
        extent_text = rootView.findViewById(R.id.extent_text);
        save = rootView.findViewById(R.id.save);
        end = rootView.findViewById(R.id.end);
        start = rootView.findViewById(R.id.start);
        close = rootView.findViewById(R.id.close);
        playBtn = rootView.findViewById(R.id.exo_play);

        setupPlayer();

        end.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start.setBackgroundResource(R.drawable.end_curve);
                end.setBackgroundResource(R.drawable.end_curve_red);
            }
        });

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start.setBackgroundResource(R.drawable.start_curve);
                end.setBackgroundResource(R.drawable.end_curve);
            }
        });


        extent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                extent.setImageResource(R.drawable.ic_extent_red);
                extent_text.setTextColor(getResources().getColor(R.color.colorPrimaryDimRed));
                play_con1.setVisibility(View.VISIBLE);
                play_con2.setVisibility(View.GONE);
            }
        });
        back = rootView.findViewById(R.id.back);
        back1 = rootView.findViewById(R.id.back1);
        back.setOnClickListener(v -> showDialogConformation());
        back1.setOnClickListener(v -> showDialogConformation());

        save.setOnClickListener(v -> showDialogSave());

        close.setOnClickListener(v -> showDialogdelete());

        playBtn.setOnClickListener(v -> {
            if (player.isPlaying()){
                player.setPlayWhenReady(false);
                Log.d(TAG, "Stop Playback");
            }else {
                player.setPlayWhenReady(true);
                Log.d(TAG, "Start Playback");
            }
        });

        return rootView;
    }

    /**
     * Setting up the player to play the require snip for editing
     */
    private void setupPlayer() {
        snip = requireArguments().getParcelable("snip");

        defaultBandwidthMeter = new DefaultBandwidthMeter.Builder(requireContext()).build();
        dataSourceFactory = new DefaultDataSourceFactory(requireContext(),
                Util.getUserAgent(requireActivity(), "mediaPlayerSample"), defaultBandwidthMeter);

        mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(snip.getVideoFilePath()));

        player = new SimpleExoPlayer.Builder(requireContext()).build();
        playerView.setPlayer(player);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);

        player.prepare(mediaSource);
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        player.setPlayWhenReady(true);

    }

    protected void showDialogConformation() {

        final Dialog dialog = new Dialog(requireActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.warningdialog_savevideodiscardchanges);

        dialog.show();
    }

    protected void showDialogSave() {

        final Dialog dialog = new Dialog(requireActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.warningdialog_savevideo);

        dialog.show();
    }

    protected void showDialogdelete() {

        final Dialog dialog = new Dialog(requireActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.warningdialog_deletevideo);

        dialog.show();
    }
}
