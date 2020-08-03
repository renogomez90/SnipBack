package com.hipoint.snipback;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.hipoint.snipback.R;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.Hd_snips;
import com.hipoint.snipback.room.entities.Snip;
import com.hipoint.snipback.room.repository.AppRepository;
import com.hipoint.snipback.room.repository.AppViewModel;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AppMainActivity extends AppCompatActivity {
    int PERMISSION_ALL = 1;
    String[] PERMISSIONS = {
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.INTERNET};

    private static String VIDEO_DIRECTORY_NAME = "SnipBackVirtual";
    private static String THUMBS_DIRECTORY_NAME = "Thumbs";

    AppViewModel appViewModel;
    private ArrayList<String> thumbs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.appmain_activity);

        appViewModel = ViewModelProviders.of(this).get(AppViewModel.class);

//        RegisterFragment videoMode = new RegisterFragment();
//        loadFragment(videoMode);
        if24HoursCompleted();

        loadGalleryDataFromDB();

        loadFragment(VideoMode.newInstance());

        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }

    }

    private void addDailyEvent() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd z");
        String currentDateandTime = sdf.format(new Date());
        Event event = new Event();
        event.setEvent_title(currentDateandTime);
        event.setEvent_created(System.currentTimeMillis());
        AppRepository appRepository = AppRepository.getInstance();
        appRepository.insertEvent(event);
    }

    private void if24HoursCompleted() {
        appViewModel.getEventLiveData().observe(this, events -> {
            if (events != null && events.size() > 0) {
                Event lastEvent = events.get(events.size() - 1);
                long diff = System.currentTimeMillis() - lastEvent.getEvent_created();
//                long seconds = diff / 1000;
//                long minutes = diff / 1000 / 60;
                long hours = diff / 1000 / 60 / 60;
                if (hours >= 24) {
                    addDailyEvent();
                }
            } else {
                addDailyEvent();
            }
        });
    }

    private void loadGalleryDataFromDB() {
        getFilePathFromInternalStorage();
        List<Hd_snips> hdSnips = new ArrayList<>();
        appViewModel.getHDSnipsLiveData().observe(this, hd_snips -> {
            if (hd_snips != null && hdSnips.size() > 0) {
                hdSnips.addAll(hd_snips);
            }
        });
        appViewModel.getSnipsLiveData().observe(this, snips -> {
            if (snips != null && snips.size() > 0) {
                for (Snip snip : snips) {
                    for (Hd_snips hdSnip : hdSnips) {
                        if (hdSnip.getSnip_id() == snip.getSnip_id()) {
                            snip.setVideoFilePath(hdSnip.getVideo_path_processed());
                            if(thumbs.size() > 0) {
                                for (String filePath : thumbs) {
                                    File file = new File(filePath);
                                    String[] snipNameWithExtension = file.getName().split("_");
                                    if(snipNameWithExtension.length > 0){
                                        String[] snipName = snipNameWithExtension[1].split(".");
                                        if(snipName.length > 0) {
                                            int snipId = Integer.parseInt(snipName[0]);
                                            if(snipId == snip.getSnip_id()){
                                                snip.setThumbnailPath(filePath);
                                            }
                                        }

                                    }
                                }
                            }
                            AppClass.getAppInsatnce().saveAllSnips(snip);
                        }
                    }
                }

            }
        });
    }

    private void getFilePathFromInternalStorage() {
        File directory;
        File photoDirectory;
        if (Environment.getExternalStorageState() == null) {
            //create new file directory object
            directory = new File(Environment.getDataDirectory()
                    + "/" + VIDEO_DIRECTORY_NAME + "/");
            photoDirectory = new File(Environment.getDataDirectory()
                    + "/" + VIDEO_DIRECTORY_NAME + "/" + THUMBS_DIRECTORY_NAME + "/");
            if (photoDirectory.exists()) {
                File[] dirFiles = photoDirectory.listFiles();
                if (dirFiles != null && dirFiles.length != 0) {
                    for (int ii = 0; ii <= dirFiles.length; ii++) {
                        thumbs.add(dirFiles[ii].getAbsolutePath());
                    }
                }
            }
            // if no directory exists, create new directory
            if (!directory.exists()) {
                directory.mkdir();
            }
        } else if (Environment.getExternalStorageState() != null) {
            // search for directory on SD card
            directory = new File(Environment.getExternalStorageDirectory()
                    + "/" + VIDEO_DIRECTORY_NAME + "/");
            photoDirectory = new File(
                    Environment.getExternalStorageDirectory()
                            + "/" + VIDEO_DIRECTORY_NAME + "/" + THUMBS_DIRECTORY_NAME + "/");
            if (photoDirectory.exists()) {
                File[] dirFiles = photoDirectory.listFiles();
                if (dirFiles != null && dirFiles.length > 0) {
                    for (File dirFile : dirFiles) {
                        thumbs.add(dirFile.getAbsolutePath());
                    }
                    dirFiles = null;
                }
            }
            // if no directory exists, create new directory to store test
            // results
            if (!directory.exists()) {
                directory.mkdir();
            }
        }
    }


    public void loadFragment(Fragment fragment) {
        FragmentTransaction fts = getSupportFragmentManager().beginTransaction();
        if (fts != null) {
            fts.replace(R.id.mainFragment, fragment);
            fts.addToBackStack(null);
            fts.commit();
        }
    }

    @Override
    public void onBackPressed() {
        int count = getSupportFragmentManager().getBackStackEntryCount();
        if (count == 0) {
            super.onBackPressed();
        } else {
            getSupportFragmentManager().popBackStack();
        }
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }


}
