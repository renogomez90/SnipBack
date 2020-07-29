package com.hipoint.snipback;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.MediaController;
import android.widget.VideoView;

import com.hipoint.snipback.R;

public class ActivityPlayVideo extends Swipper {
    VideoView videoView;
    private String uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        Intent intent=getIntent();
        uri=intent.getStringExtra("uri");

        videoView = (VideoView) findViewById(R.id.videoView);
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);
        Uri video1 = Uri.parse(uri);
        videoView.setVideoURI(video1);
        videoView.start();
//        Brightness(Orientation.CIRCULAR);
//        Volume(Orientation.VERTICAL);
        Seek(Orientation.HORIZONTAL, videoView);
        set(this);
    }
}
