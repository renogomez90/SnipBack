package com.example.snipback.fragment;

import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.snipback.AppMainActivity;
import com.example.snipback.R;
import com.example.snipback.VideoMode;
import com.example.snipback.adapter.AdapterPhotos;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Util;


import java.io.File;

import static android.app.Activity.RESULT_OK;
import static com.facebook.share.internal.ShareConstants.CONTENT_URL;

public class FragmentGallery extends Fragment {
    private View rootView;
    ImageButton filter_button, view_button, menu_button;
    TextView filter_label, view_label, menu_label, photolabel;
    ImageView autodelete_arrow;
    RecyclerView recycler_view;

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


    RelativeLayout relativeLayout_menu, relativeLayout_autodeleteactions, layout_autodelete, layout_filter, layout_multidelete,click;

    public static FragmentGallery newInstance() {
        FragmentGallery fragment = new FragmentGallery();
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_gallery, container, false);
        ( getActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        photolabel = rootView.findViewById(R.id.photolabel);
        recycler_view = rootView.findViewById(R.id.recycler_view);
        menu_button = rootView.findViewById(R.id.dropdown_menu);
        view_button = rootView.findViewById(R.id._button_view);
        filter_button = rootView.findViewById(R.id.filter);
        filter_label = rootView.findViewById(R.id.filter_text);
        view_label = rootView.findViewById(R.id._button_view_text);
        click=rootView.findViewById(R.id.click);
        recycler_view.setLayoutManager(new LinearLayoutManager(getActivity()));
        AdapterPhotos adapterPhotos = new AdapterPhotos(getActivity());
        recycler_view.setAdapter(adapterPhotos);

        // exo player



        //
        menu_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Dialog dialog = new Dialog(getActivity());
                view_button.setImageResource(R.drawable.ic_view_unselected);
                view_label.setTextColor(getResources().getColor(R.color.colorDarkGreyDim));
                Window window = dialog.getWindow();
                WindowManager.LayoutParams wlp = window.getAttributes();
                wlp.gravity = Gravity.BOTTOM;
                wlp.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                window.setAttributes(wlp);
                dialog.setContentView(R.layout.menu_layout);
                WindowManager.LayoutParams params = dialog.getWindow().getAttributes(); // change this to your dialog.
                params.y = 150;
                dialog.getWindow().setAttributes(params);
                layout_autodelete = dialog.findViewById(R.id.layout_autodelete);
                relativeLayout_autodeleteactions = dialog.findViewById(R.id.layout_autodeleteactions);
                autodelete_arrow = dialog.findViewById(R.id.autodelete_arrow);
                layout_multidelete = dialog.findViewById(R.id.layout_multipledelete);
                layout_autodelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        relativeLayout_autodeleteactions.setVisibility(View.VISIBLE);
                        autodelete_arrow.setImageResource(R.drawable.ic_keyboard_arrow_down_black_24dp);

                    }
                });
                layout_multidelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        relativeLayout_autodeleteactions.setVisibility(View.GONE);
                        autodelete_arrow.setImageResource(R.drawable.ic_forward);
                        dialog.cancel();
                        ((AppMainActivity) getActivity()).loadFragment(FragmentMultiDeletePhoto.newInstance());


                    }
                });

                RelativeLayout layout_import = dialog.findViewById(R.id.layout_import);
                layout_import.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        Intent intent = new Intent();
                        intent.setType("video/*");
                        intent.setAction(Intent.ACTION_GET_CONTENT);
                        startActivityForResult(Intent.createChooser(intent,"Select Video"),1111);
//                        Matisse.from(FragmentGallery.this)
////                                .choose(MimeType.ofVideo())
////                                .countable(false)
//////                                    .capture(true)
//////                                    .captureStrategy(
//////                                            new CaptureStrategy(true, "com.zhihu.matisse.sample.fileprovider"))
////                                .maxSelectable(1)
////                                .gridExpectedSize(getResources().getDimensionPixelSize(R.dimen.grid_expected_size))//                                           .restrictOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
////                                .thumbnailScale(0.85f)
////                                .imageEngine(new PicassoEngine())
////                                .showSingleMediaType(true)
////                                .forResult(1111);
                        dialog.dismiss();
                    }
                });

                dialog.show();
            }
        });
        filter_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Dialog dialogFilter = new Dialog(getActivity());
                Window window = dialogFilter.getWindow();
                filter_button.setImageResource(R.drawable.ic_filter_selected);
                filter_label.setTextColor(getResources().getColor(R.color.colorPrimaryDimRed));
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                dialogFilter.setContentView(R.layout.filter_layout);
                dialogFilter.show();
            }
        });
        click.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((AppMainActivity) getActivity()).loadFragment(FragmentPlayVideo.newInstance(uri.toString()));
                player.release();

//                Intent intent = new Intent(getActivity(), FragmentPlayVideo.class);
//                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
//                intent.putExtra("uri", uri);
//                startActivity(intent);
            }
        });
        return rootView;
    }

//    @Override
//    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        // get selected images from selector
//        if (requestCode == 1111) {
//            if (resultCode == RESULT_OK) {
//                ContentResolver cr = getActivity().getContentResolver();
//                String type = cr.getType(Matisse.obtainResult(data).get(0));
//                type = type.substring(0, 5);
//                Log.e("SampleActivity", "type: " + type.substring(0, 5));
//                uri=Matisse.obtainResult(data).get(0);
//
//                bandwidthMeter = new DefaultBandwidthMeter();
//                extractorsFactory = new DefaultExtractorsFactory();
//
//                trackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
//
//                trackSelector = new DefaultTrackSelector(trackSelectionFactory);
//
//                defaultBandwidthMeter = new DefaultBandwidthMeter();
//                dataSourceFactory = new DefaultDataSourceFactory(getActivity(),
//                        Util.getUserAgent(getActivity(), "mediaPlayerSample"), defaultBandwidthMeter);
//
//                mediaSource = new ExtractorMediaSource(uri,
//                        dataSourceFactory,
//                        extractorsFactory,
//                        null,
//                        null);
//
//                player = ExoPlayerFactory.newSimpleInstance(getActivity(),trackSelector);
//                simpleExoPlayerView = (PlayerView) getActivity().findViewById(R.id.player_view);
//                simpleExoPlayerView.setPlayer(player);
//                player.prepare(mediaSource);
//                player.setPlayWhenReady(true);
//
//
//
//            }
//
//        }
//        super.onActivityResult(requestCode, resultCode, data);
//    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == 1111) {
                uri = data.getData();
                String videopath = uri.getPath();
                File file = new File(videopath);
                Log.e("path",file.getAbsolutePath());



                bandwidthMeter = new DefaultBandwidthMeter();

                extractorsFactory = new DefaultExtractorsFactory();

                trackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);

                trackSelector = new DefaultTrackSelector(trackSelectionFactory);

                defaultBandwidthMeter = new DefaultBandwidthMeter();
                dataSourceFactory = new DefaultDataSourceFactory(getActivity(),
                        Util.getUserAgent(getActivity(), "mediaPlayerSample"), defaultBandwidthMeter);

                mediaSource = new ExtractorMediaSource(uri,
                        dataSourceFactory,
                        extractorsFactory,
                        null,
                        null);

                player = ExoPlayerFactory.newSimpleInstance(getActivity(),trackSelector);
                simpleExoPlayerView = (PlayerView) getActivity().findViewById(R.id.player_view);
                simpleExoPlayerView.hideController();
                simpleExoPlayerView.setPlayer(player);
                player.prepare(mediaSource);
                player.setPlayWhenReady(true);

                simpleExoPlayerView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(getActivity(), FragmentPlayVideo.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        intent.putExtra("uri", uri);
                        startActivity(intent);
                    }
                });
//
            }
        }
    }

}


