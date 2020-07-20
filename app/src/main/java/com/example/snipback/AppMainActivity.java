package com.example.snipback;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.snipback.fragment.IntroFragmentViewPager;
import com.example.snipback.fragment.RegisterFragment;
import com.example.snipback.fragment.TrialOver;

public class AppMainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.appmain_activity);

        RegisterFragment videoMode = new RegisterFragment();
        loadFragment(videoMode);

    }

    public   void loadFragment(Fragment fragment) {
        FragmentTransaction fts = getSupportFragmentManager().beginTransaction();
        if (fts != null) {
            fts.replace(R.id.mainFragment, fragment);
            fts.addToBackStack(null);
            fts.commit();
        }
    }

}
