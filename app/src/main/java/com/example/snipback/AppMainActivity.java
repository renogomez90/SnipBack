package com.example.snipback;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.snipback.fragment.IntroFragmentViewPager;
import com.example.snipback.fragment.RegisterFragment;
import com.example.snipback.fragment.TrialOver;

public class AppMainActivity extends AppCompatActivity {
    int PERMISSION_ALL = 1;
    String[] PERMISSIONS = {
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.INTERNET};
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.appmain_activity);


        RegisterFragment videoMode = new RegisterFragment();
        loadFragment(videoMode);

        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
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
