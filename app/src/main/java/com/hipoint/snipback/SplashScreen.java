package com.hipoint.snipback;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.hipoint.snipback.R;

public class SplashScreen extends AppCompatActivity {
    Intent intent;
    private TextView tv_version;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        (this).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        tv_version=findViewById(R.id.tv_version);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            tv_version.setText("V"+version+ ".0\n © 2020 SNIPBACK. ALL RIGHTS RESERVED");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        Handler handler = new Handler();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(getApplicationContext(),R.color.colorPrimary));
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                intent= new Intent(getApplicationContext(),AppMainActivity.class);
                startActivity(intent);
                finish();

            }
        }, 5000);
    }


}
