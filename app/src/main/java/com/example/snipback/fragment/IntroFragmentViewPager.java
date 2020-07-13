package com.example.snipback.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.example.snipback.R;
import com.example.snipback.adapter.ViewPagerAdapter;
import com.google.android.material.tabs.TabLayout;

import java.util.Objects;

public class IntroFragmentViewPager extends Fragment {

    private TabLayout tabLayout;
    private ViewPager viewPager;
    private ViewPagerAdapter adapter;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.intro_feed_pager_layout, null);

        tabLayout = view.findViewById(R.id.homeTabs);
        viewPager = view.findViewById(R.id.viewPager);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
////
//        ((MainActivity) Objects.requireNonNull(getActivity())).setBottomHighLighted(R.id.home);
//        ((MainActivity) Objects.requireNonNull(getActivity())).handleToolBar("Feed", false, false, false);
        setupViewPager(viewPager, 0);

        tabLayout.setupWithViewPager(viewPager);

//        showFeedByCount(showMyEvents);
    }


    private void setupViewPager(ViewPager viewPager, int categoryId) {
        adapter = new ViewPagerAdapter(getChildFragmentManager());
        Fragment popularFrag = IntroFragment1.newInstance( "popular");
        adapter.addFragment(popularFrag, "");

        Fragment categoriesFrag = IntroFragment2.newInstance( "Hotshots");
        adapter.addFragment(categoriesFrag, "");

        Fragment favFrag = IntroFragment1.newInstance( "favourites");
        adapter.addFragment(favFrag, "");


        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(0);

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (position == 1 ) {
                    Fragment favFrag = IntroFragment2.newInstance( "favourites");
                    adapter.addFragment(favFrag, "");


                }
//                if (position != 1 && position != 3) {
////                    RefreshFeed fragment = (RefreshFeed) adapter.instantiateItem(viewPager, position);
//                    if (fragment instanceof PopularFeedFragment || fragment instanceof LiveFeedFragment || fragment instanceof FollowingFeedFragment) {
//                        fragment.fragmentBecameVisible();
//                    }
//
//                } else if (position == 3) {
//
//                    ((MainActivity) Objects.requireNonNull(getActivity())).handleToolBar("MY EVENTS", false, false, false);
//                } else {
//
//                    ((MainActivity) Objects.requireNonNull(getActivity())).handleToolBar("Feed", false, false, false);
//                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
//                CommonFunctions.showLog("hss", "3");
            }
        });
    }

}
