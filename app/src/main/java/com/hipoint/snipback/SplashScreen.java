package com.hipoint.snipback;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProviders;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.hipoint.snipback.R;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.room.repository.AppViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Objects;

public class SplashScreen extends AppCompatActivity {
    Intent intent;
    private TextView tv_version;
    private AppViewModel appViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        (this).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        tv_version=findViewById(R.id.tv_version);
        appViewModel = ViewModelProviders.of(this).get(AppViewModel.class);
        appViewModel.loadLastInsertedEvent(this);
        getThumbnailPath();
        appViewModel.loadGalleryDataFromDB(this);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            tv_version.setText("V"+version+ ".0\n © 2020 SNIPBACK. ALL RIGHTS RESERVED");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        Handler handler = new Handler();
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(getApplicationContext(),R.color.colorPrimary));
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                intent= new Intent(getApplicationContext(),AppMainActivity.class);
                startActivity(intent);
                finish();
            }
        }, 3000);

    }

    private void getThumbnailPath(){
        String VIDEO_DIRECTORY_NAME = "SnipBackVirtual";
        String THUMBS_DIRECTORY_NAME = "Thumbs";
        File thumbsStorageDir = new File(getDataDir() + "/" + VIDEO_DIRECTORY_NAME,
                THUMBS_DIRECTORY_NAME);
        File fullThumbPath = new File(thumbsStorageDir.getPath() + File.separator
                + "snip_");
        AppClass.getAppInsatnce().setThumbFilePathRoot(fullThumbPath.getAbsolutePath());
    }




}
