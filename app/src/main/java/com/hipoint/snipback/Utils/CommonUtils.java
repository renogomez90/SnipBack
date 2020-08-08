package com.hipoint.snipback.Utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.util.Log;

import com.hipoint.snipback.room.entities.Snip;
import com.kaopiz.kprogresshud.KProgressHUD;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

public class CommonUtils {

    public static KProgressHUD showProgressDialog(Context context) {
        KProgressHUD hud = KProgressHUD.create(context)
                .setStyle(KProgressHUD.Style.SPIN_INDETERMINATE)
                .show();
        return hud;
    }

    public static String today(){
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
        String day = "";
        switch (dayOfWeek) {
            case Calendar.SUNDAY:
                day = "Sunday";
                break;
            case Calendar.MONDAY:
                day = "Monday";
                break;
            case Calendar.TUESDAY:
                day = "Tuesday";
                break;
            case Calendar.WEDNESDAY:
                day = "Wednesday";
                break;
            case Calendar.THURSDAY:
                day = "Thurday";
                break;
            case Calendar.FRIDAY:
                day = "Friday";
                break;
            case Calendar.SATURDAY:
                day = "Saturday";
                break;
        }
        return day;
    }
    private static String VIDEO_DIRECTORY_NAME_VIRTUAL = "SnipBackVirtual";
    private static String THUMBS_DIRECTORY_NAME = "Thumbs";
    public static void getVideoThumbnail(Context activity, Snip snip) {
        String TAG = "Thumbnail Creation";
        try {
            File thumbsStorageDir = new File(Objects.requireNonNull(activity).getDataDir() + "/" + VIDEO_DIRECTORY_NAME_VIRTUAL,
                    THUMBS_DIRECTORY_NAME);

            if (!thumbsStorageDir.exists()) {
                if (!thumbsStorageDir.mkdirs()) {
                    Log.d(TAG, "Oops! Failed create "
                            + VIDEO_DIRECTORY_NAME_VIRTUAL + " directory");
                    return;
                }
            }
            File fullThumbPath;

            fullThumbPath = new File(thumbsStorageDir.getPath() + File.separator
                    + "snip_" + snip.getSnip_id() + ".png");
            Log.d(TAG, "saving video thumbnail at path: " + fullThumbPath + ", video path: " + snip.getVideoFilePath());
            //Save the thumbnail in a PNG compressed format, and close everything. If something fails, return null
            FileOutputStream streamThumbnail = new FileOutputStream(fullThumbPath);

            //Other method to get a thumbnail. The problem is that it doesn't allow to get at a specific time
            Bitmap thumb; //= ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(),MediaStore.Images.Thumbnails.MINI_KIND);
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(snip.getVideoFilePath());
                if (snip.getIs_virtual_version() != 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        thumb = retriever.getScaledFrameAtTime((int) snip.getStart_time() * 1000000,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 100, 100);
                    } else {
                        thumb = retriever.getFrameAtTime((int) snip.getStart_time() * 1000000,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    }
                } else {
                    thumb = retriever.getFrameAtTime();
                }
                thumb.compress(Bitmap.CompressFormat.PNG, 80, streamThumbnail);
                thumb.recycle(); //ensure the image is freed;
            } catch (Exception ex) {
                Log.i(TAG, "MediaMetadataRetriever got exception:" + ex);
            }
            streamThumbnail.close();
            Log.d(TAG, "thumbnail saved successfully");
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File Not Found Exception : check directory path");
            e.printStackTrace();
        } catch (IOException e) {
            Log.d(TAG, "IOException while closing the stream");
            e.printStackTrace();
        }
    }

}
