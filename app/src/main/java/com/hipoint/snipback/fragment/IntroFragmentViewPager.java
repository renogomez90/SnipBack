package com.hipoint.snipback.fragment;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.hipoint.snipback.R;
import com.hipoint.snipback.adapter.ViewPagerAdapter;
import com.google.android.material.tabs.TabLayout;

public class IntroFragmentViewPager extends Fragment {

    private TabLayout tabLayout;
    private ViewPager viewPager;
    private ViewPagerAdapter adapter;

    public static IntroFragmentViewPager newInstance() {
        IntroFragmentViewPager fragment = new IntroFragmentViewPager();
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.intro_feed_pager_layout, null);

        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);


        tabLayout = view.findViewById(R.id.homeTabs);
        viewPager = view.findViewById(R.id.viewPager);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        setupViewPager(viewPager, 0);

        tabLayout.setupWithViewPager(viewPager);

//        showFeedByCount(showMyEvents);
    }


    private void setupViewPager(ViewPager viewPager, int categoryId) {
        adapter = new ViewPagerAdapter(getChildFragmentManager());
        Fragment introFragment1 = IntroFragment1.newInstance();
        adapter.addFragment(introFragment1, "");

        Fragment introFragment2 = IntroFragment2.newInstance();
        adapter.addFragment(introFragment2, "");

        Fragment introductionFragmentThree = IntroductionFragmentThree.newInstance();
        adapter.addFragment(introductionFragmentThree, "");
        Fragment introduction_fragment_four = Introduction_Fragment_Four.newInstance();
        adapter.addFragment(introduction_fragment_four, "");
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(0);

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
               /* if (position == 1 ) {
                    Fragment introFragment11 = IntroFragment1.newInstance();
                    adapter.addFragment(introFragment11,"");


                }*/
            }

            @Override
            public void onPageScrollStateChanged(int state) {
//                CommonFunctions.showLog("hss", "3");
            }
        });
    }

}
