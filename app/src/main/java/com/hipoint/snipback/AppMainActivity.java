package com.hipoint.snipback;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MotionEvent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProviders;

import com.hipoint.snipback.Utils.CommonUtils;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.fragment.FragmentGalleryNew;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.repository.AppRepository;
import com.hipoint.snipback.room.repository.AppViewModel;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

public class AppMainActivity extends AppCompatActivity implements VideoMode.OnTaskCompleted {
    int PERMISSION_ALL = 1;
    String[] PERMISSIONS = {
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.INTERNET};

//    private static String VIDEO_DIRECTORY_NAME = "SnipBackVirtual";
//    private static String THUMBS_DIRECTORY_NAME = "Thumbs";
    private List<MyOnTouchListener> onTouchListeners;

    private AppViewModel appViewModel;
//    private ArrayList<String> thumbs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.appmain_activity);

        if (onTouchListeners == null) {
            onTouchListeners = new ArrayList<>();
        }

        appViewModel = ViewModelProviders.of(this).get(AppViewModel.class);

//        RegisterFragment videoMode = new RegisterFragment();
//        loadFragment(videoMode);
        if24HoursCompleted();

//        appViewModel.loadGalleryDataFromDB(this);

        loadFragment(VideoMode.newInstance(), false);

        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA},
                    50); }

    }

    public void registerMyOnTouchListener(MyOnTouchListener listener) {
        onTouchListeners.add(listener);
    }

    private void addDailyEvent() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy");
        String currentDateandTime = sdf.format(new Date());
        Event event = new Event();
        event.setEvent_title(CommonUtils.today() + ", " + currentDateandTime);
        event.setEvent_created(System.currentTimeMillis());
        AppRepository appRepository = new AppRepository(AppClass.getAppInstance());
        appRepository.insertEvent(event, new Continuation<Unit>() {
            @NotNull
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(@NotNull Object o) {

            }
        });
    }

    private void if24HoursCompleted() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy");
        String currentDateandTime = sdf.format(new Date());
        appViewModel.getEventLiveData().observe(this, events -> {
            if (events != null && events.size() > 0) {
                Event lastEvent = events.get(events.size() - 1);
                if(!lastEvent.getEvent_title().equals(CommonUtils.today() + ", " + currentDateandTime)){
                    addDailyEvent();
                }
//                long diff = System.currentTimeMillis() - lastEvent.getEvent_created();
////                long seconds = diff / 1000;
////                long minutes = diff / 1000 / 60;
//                long hours = diff / 1000 / 60 / 60;
//                if (hours >= 8) {
//                    addDailyEvent();
//                }
            } else {
                addDailyEvent();
            }
        });
    }

    //    public void loadFragment(Fragment fragment,boolean addtoBackStack) {
    public void loadFragment(Fragment fragment, boolean addtoBackStack) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.mainFragment, fragment);
        if (addtoBackStack || fragment instanceof FragmentGalleryNew) {
            ft.addToBackStack(null);
        }
        ft.commitAllowingStateLoss();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        for (MyOnTouchListener listener : onTouchListeners)
            listener.onTouch(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
        Fragment myFragment = getSupportFragmentManager().findFragmentById(R.id.mainFragment);
        int count = getSupportFragmentManager().getBackStackEntryCount();
        if (count == 0) {
            super.onBackPressed();
        } else {
            if(myFragment instanceof FragmentGalleryNew){
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            }else{
                getSupportFragmentManager().popBackStack();
            }
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

    @Override
    public void onTaskCompleted(boolean success) {
        if(success){
            Fragment myFragment = getSupportFragmentManager().findFragmentById(R.id.mainFragment);
            if(myFragment instanceof FragmentGalleryNew){
                ((FragmentGalleryNew) myFragment).onLoadingCompleted(success);
            }
        }
    }

    public interface MyOnTouchListener {
        public void onTouch(MotionEvent ev);
    }




}
