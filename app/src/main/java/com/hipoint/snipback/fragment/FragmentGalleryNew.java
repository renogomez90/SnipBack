package com.hipoint.snipback.fragment;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateFormat;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.hipoint.snipback.ActivityPlayVideo;
import com.hipoint.snipback.AppMainActivity;
import com.hipoint.snipback.R;
import com.hipoint.snipback.Utils.CommonUtils;
import com.hipoint.snipback.VideoMode;
import com.hipoint.snipback.adapter.AdapterGallery;
import com.hipoint.snipback.adapter.CategoryItemRecyclerAdapter;
import com.hipoint.snipback.adapter.MainRecyclerAdapter;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.room.entities.AllCategory;
import com.hipoint.snipback.room.entities.CategoryItem;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.EventData;
import com.hipoint.snipback.room.entities.Hd_snips;
import com.hipoint.snipback.room.entities.Snip;
import com.hipoint.snipback.room.repository.AppViewModel;
import com.kaopiz.kprogresshud.KProgressHUD;

import java.io.File;
import java.time.Month;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class FragmentGalleryNew extends Fragment{
    private View rootView;
    RecyclerView mainCategoryRecycler;
    MainRecyclerAdapter mainRecyclerAdapter;
    SwipeRefreshLayout pullToRefresh;
    ImageButton filter_button, view_button, menu_button, camera_button;
    TextView filter_label, view_label, menu_label, photolabel;
    ImageView autodelete_arrow, player_view_image;
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
    private RelativeLayout rlLoader;


    RelativeLayout relativeLayout_menu, relativeLayout_autodeleteactions, layout_autodelete, layout_filter, layout_multidelete, click, import_con;

    List<Snip> snipArrayList= new ArrayList<>();

    public static FragmentGalleryNew newInstance() {
        FragmentGalleryNew fragment = new FragmentGalleryNew();
        return fragment;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_gallery_new, container, false);

        (getActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        import_con = rootView.findViewById(R.id.import_con);
        player_view_image = rootView.findViewById(R.id.player_view_image);
        photolabel = rootView.findViewById(R.id.photolabel);
        recycler_view = rootView.findViewById(R.id.recycler_view);
        menu_button = rootView.findViewById(R.id.dropdown_menu);
        view_button = rootView.findViewById(R.id._button_view);
        camera_button = rootView.findViewById(R.id.camera);
        filter_button = rootView.findViewById(R.id.filter);
        filter_label = rootView.findViewById(R.id.filter_text);
        view_label = rootView.findViewById(R.id._button_view_text);
        click = rootView.findViewById(R.id.click);
        rlLoader = rootView.findViewById(R.id.showLoader);
        pullToRefresh = rootView.findViewById(R.id.pullToRefresh);
        click.setVisibility(View.GONE);

        if(AppClass.getAppInsatnce().isInsertionInProgress()){
            rlLoader.setVisibility(View.VISIBLE);
        }else{
            rlLoader.setVisibility(View.INVISIBLE);
        }

        // direct to gallery to view

        photolabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_GET_CONTENT);
                intent.setType("video/*");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

        camera_button.setOnClickListener(v -> ((AppMainActivity) getActivity()).loadFragment(VideoMode.newInstance(), false));
        menu_button.setOnClickListener(v -> {
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
            layout_autodelete.setOnClickListener(v1 -> {
                relativeLayout_autodeleteactions.setVisibility(View.VISIBLE);
                autodelete_arrow.setImageResource(R.drawable.ic_keyboard_arrow_down_black_24dp);

            });
            layout_multidelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    relativeLayout_autodeleteactions.setVisibility(View.GONE);
                    autodelete_arrow.setImageResource(R.drawable.ic_forward);
                    dialog.cancel();
                    ((AppMainActivity) getActivity()).loadFragment(FragmentMultiDeletePhoto.newInstance(),true);


                }
            });

            RelativeLayout layout_import = dialog.findViewById(R.id.layout_import);
            layout_import.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    Intent intent = new Intent();
                    intent.setType("video/*");
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    startActivityForResult(Intent.createChooser(intent, "Select Video"), 1111);

                    dialog.dismiss();
                }
            });

            dialog.show();
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
//                ((AppMainActivity) getActivity()).loadFragment(FragmentPlayVideo.newInstance(uri.toString()));
                Intent intent = new Intent(getActivity(), ActivityPlayVideo.class);
                intent.putExtra("uri", uri.toString());
                startActivity(intent);

            }
        });

        mainCategoryRecycler = rootView.findViewById(R.id.main_recycler);

//        AppViewModel appViewModel = ViewModelProviders.of(getActivity()).get(AppViewModel.class);
        pulltoRefresh();
        pullToRefresh.setRefreshing(false);

        return rootView;
    }

    private void pulltoRefresh() {
        pullToRefresh.setOnRefreshListener(() -> {
            pullToRefresh.setRefreshing(false);
            loadGalleryDataFromDB();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadGalleryDataFromDB();
//        if(AppClass.getAppInsatnce().getAllParentSnip().size() == 0) {
//            loadGalleryDataFromDB();
//            AppClass.getAppInsatnce().setInsertionInProgress(false);
//        }else{
//            List<EventData> allSnipEvent = AppClass.getAppInsatnce().getAllSnip();
//            List<EventData> allParentSnipEvent = AppClass.getAppInsatnce().getAllParentSnip();
//            RecyclerView.LayoutManager layoutManager=new LinearLayoutManager(getActivity());
//            mainCategoryRecycler.setLayoutManager(layoutManager);
//            mainRecyclerAdapter=new MainRecyclerAdapter(getActivity(),allParentSnipEvent,allSnipEvent);
//            mainCategoryRecycler.setAdapter(mainRecyclerAdapter);
//        }
    }

    private void loadGalleryDataFromDB() {
        AppClass.getAppInsatnce().clearAllParentSnips();
        AppClass.getAppInsatnce().clearAllSnips();
        AppViewModel appViewModel = ViewModelProviders.of(this).get(AppViewModel.class);
//        getFilePathFromInternalStorage();
        List<Event> allEvents = new ArrayList<>();
        appViewModel.getEventLiveData().observe(this, events -> {
            if(events != null && events.size() > 0){
                allEvents.addAll(events);
                List<Hd_snips> hdSnips = new ArrayList<>();
                appViewModel.getHDSnipsLiveData().observe(this, hd_snips -> {
                    if (hd_snips != null && hd_snips.size() > 0) {
                        hdSnips.addAll(hd_snips);
                        appViewModel.getSnipsLiveData().observe(this, snips -> {
                            if (snips != null && snips.size() > 0) {
                                for (Snip snip : snips) {
                                    for (Hd_snips hdSnip : hdSnips) {
                                        if (hdSnip.getSnip_id() == snip.getParent_snip_id() || hdSnip.getSnip_id() == snip.getSnip_id()) {
                                            snip.setVideoFilePath(hdSnip.getVideo_path_processed());
                                            for(Event event : allEvents){
                                                if(event.getEvent_id() == snip.getEvent_id()){
                                                    AppClass.getAppInsatnce().setEventSnipsFromDb(event,snip);
                                                    if(snip.getParent_snip_id() == 0){
                                                        AppClass.getAppInsatnce().setEventParentSnipsFromDb(event,snip);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                pullToRefresh.setRefreshing(false);
                                List<EventData> allSnips = AppClass.getAppInsatnce().getAllSnip();
                                List<EventData> allParentSnip = AppClass.getAppInsatnce().getAllParentSnip();
                                if(mainRecyclerAdapter == null) {
                                    RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());
                                    mainCategoryRecycler.setLayoutManager(layoutManager);
                                    mainRecyclerAdapter = new MainRecyclerAdapter(getActivity(), allParentSnip, allSnips);
                                    mainCategoryRecycler.setAdapter(mainRecyclerAdapter);
                                }else {
                                    mainRecyclerAdapter.notifyDataSetChanged();
                                }
                            }
                        });
                    }
                });
            }
        });
    }
    private static String VIDEO_DIRECTORY_NAME = "SnipBackVirtual";
    private static String THUMBS_DIRECTORY_NAME = "Thumbs";
    private ArrayList<String> thumbs = new ArrayList<>();

    private void getFilePathFromInternalStorage() {
        File directory;
        File photoDirectory;
//        if (Environment.getExternalStorageState() == null) {
            //create new file directory object
            directory = new File(Objects.requireNonNull(getActivity()).getDataDir()
                    + "/" + VIDEO_DIRECTORY_NAME + "/");
            photoDirectory = new File(Objects.requireNonNull(getActivity()).getDataDir()
                    + "/" + VIDEO_DIRECTORY_NAME + "/" + THUMBS_DIRECTORY_NAME + "/");
            if (photoDirectory.exists()) {
                File[] dirFiles = photoDirectory.listFiles();
                if (dirFiles != null && dirFiles.length != 0) {
                    for (int ii = 0; ii < dirFiles.length; ii++) {
                        thumbs.add(dirFiles[ii].getAbsolutePath());
                    }
                }
            }
            // if no directory exists, create new directory
            if (!directory.exists()) {
                directory.mkdir();
            }
//        } else if (Environment.getExternalStorageState() != null) {
//            // search for directory on SD card
//            directory = new File(Environment.getExternalStorageDirectory()
//                    + "/" + VIDEO_DIRECTORY_NAME + "/");
//            photoDirectory = new File(
//                    Environment.getExternalStorageDirectory()
//                            + "/" + VIDEO_DIRECTORY_NAME + "/" + THUMBS_DIRECTORY_NAME + "/");
//            if (photoDirectory.exists()) {
//                File[] dirFiles = photoDirectory.listFiles();
//                if (dirFiles != null && dirFiles.length > 0) {
//                    for (File dirFile : dirFiles) {
//                        thumbs.add(dirFile.getAbsolutePath());
//                    }
//                    dirFiles = null;
//                }
//            }
//            // if no directory exists, create new directory to store test
//            // results
//            if (!directory.exists()) {
//                directory.mkdir();
//            }
//        }
    }

    public void onLoadingCompleted(boolean success) {
        if(success){
            rlLoader.setVisibility(View.INVISIBLE);
            List<EventData> allSnips = AppClass.getAppInsatnce().getAllSnip();
            List<EventData> allParentSnip = AppClass.getAppInsatnce().getAllParentSnip();
            if(mainRecyclerAdapter == null) {
                RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());
                mainCategoryRecycler.setLayoutManager(layoutManager);
                mainRecyclerAdapter = new MainRecyclerAdapter(getActivity(), allParentSnip, allSnips);
                mainCategoryRecycler.setAdapter(mainRecyclerAdapter);
            }else {
                mainRecyclerAdapter.notifyDataSetChanged();
            }
        }
    }
}







