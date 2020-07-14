package com.example.snipback;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.snipback.fragment.IntroFragmentViewPager;
import com.example.snipback.fragment.TrialOver;

public class AppMainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.appmain_activity);

        VideoMode videoMode = new VideoMode();
        loadFragment(videoMode);
//        IntroFragmentViewPager fragmentViewPager = new IntroFragmentViewPager();
//        loadFragment(fragmentViewPager);
    }
    public void loadFragment(Fragment fragment) {
        FragmentTransaction fts = getSupportFragmentManager().beginTransaction();
        if (fts != null) {
            fts.replace(R.id.mainFragment, fragment);
            fts.addToBackStack(null);
            fts.commit();
        }
    }

}
