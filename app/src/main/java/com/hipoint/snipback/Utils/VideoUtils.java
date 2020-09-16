package com.hipoint.snipback.Utils;

import android.media.MediaMetadataRetriever;
import android.util.Log;
import com.hipoint.snipback.listener.IVideoOpListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import VideoHandle.EpEditor;
import VideoHandle.EpVideo;
import VideoHandle.OnEditorListener;

public class VideoUtils {
    private String TAG = VideoUtils.class.getSimpleName();
    private IVideoOpListener opListener;

    public VideoUtils(IVideoOpListener videoOpListener) {
        opListener = videoOpListener;
    }

    /**
     * mergeRecordedFiles attempts to merge two video files into the same file
     * using coroutines to run in the background
     * uses clip1 dimensions for final merged file
     *
     * @param clip1      First clip to be merged
     * @param clip2      Second clip to be merged
     * @param outputPath Path to save output
     */
    public void mergeRecordedFiles(File clip1, File clip2, String outputPath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(clip1.getAbsolutePath());
        int width = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        int height = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        retriever.release();

        ArrayList<EpVideo> epVideos = new ArrayList<>();
        epVideos.add(new EpVideo(clip1.getAbsolutePath()));
        epVideos.add(new EpVideo(clip2.getAbsolutePath()));

        EpEditor.OutputOption options = new EpEditor.OutputOption(outputPath);
        options.setHeight(height);
        options.setWidth(width);
        options.frameRate = 30;
        options.bitRate = 10; // default

        EpEditor.merge(epVideos, options, new OnEditorListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Merge Success");
                opListener.changed(IVideoOpListener.VideoOp.MERGED, outputPath);
            }

            @Override
            public void onFailure() {
                Log.d(TAG, "Merge Failed");
                opListener.failed(IVideoOpListener.VideoOp.MERGED);
            }

            @Override
            public void onProgress(float progress) {

            }
        });
    }

    /**
     * concatenateFiles attempts to concatenate two video files without re-encoding.
     * this will only work when the files are identical
     * using coroutines to run in the background
     * uses clip1 dimensions for final merged file
     *
     * @param clip1      First clip to be merged
     * @param clip2      Second clip to be merged
     * @param outputPath Path to save output
     */
    public void concatenateFiles(File clip1, File clip2, String outputPath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        retriever.setDataSource(clip1.getAbsolutePath());
        long duration1 = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

        retriever.setDataSource(clip2.getAbsolutePath());
        long duration2 = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

        retriever.release();

        String tmpFile = createFileList(clip1, clip2);
        String cmd = "-f concat -safe 0 -i "+tmpFile+" -c copy -y -b:v 1M "+outputPath;
        Log.d(TAG, cmd);

        EpEditor.execCmd(cmd, duration1 + duration2, new OnEditorListener() {
            @Override
            public void onSuccess() {
                new File(tmpFile).delete();
                Log.d(TAG, "Concat Success");
                opListener.changed(IVideoOpListener.VideoOp.CONCAT, outputPath);
            }

            @Override
            public void onFailure() {
                Log.d(TAG, "Concat Failed");
                opListener.failed(IVideoOpListener.VideoOp.CONCAT);
            }

            @Override
            public void onProgress(float progress) {

            }
        });
    }

    /**
     * Trims the full length clip to the last 2 minutes chunks of clipDuration
     * @param clip clip to be trimmed
     * @param parentFolder Path to save output
     * */
    public void trimToClip(File clip, String parentFolder, int numOfClips, long clipDuration) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(clip.getAbsolutePath());
        long duration = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));    // in miliseconds
        retriever.release();

        long sec = TimeUnit.MILLISECONDS.toSeconds(duration);
        long clipDurationSeconds = TimeUnit.MILLISECONDS.toSeconds(clipDuration);
        String outputPath = parentFolder+"/cutClip.mp4";
        String cmd = "-i "+clip.getAbsolutePath()+" -ss "+(sec - (clipDurationSeconds * numOfClips))+" -to "+sec+" -async 1 -strict -2 -c copy "+outputPath;

        Log.d(TAG, "CMD =" +cmd);
        EpEditor.execCmd(cmd, 1, new OnEditorListener() {
            @Override
            public void onSuccess() {
                opListener.changed(IVideoOpListener.VideoOp.TRIMMED, outputPath);
            }

            @Override
            public void onFailure() {
                opListener.failed(IVideoOpListener.VideoOp.TRIMMED);
            }

            @Override
            public void onProgress(float progress) {

            }
        });
    }


    private String createFileList(File clip1, File clip2) {
        File f = new File(clip1.getParent() + "/tmp.txt");
        PrintWriter pw;
        try {
            pw = new PrintWriter(new FileWriter(f));
            pw.print("file '"+clip1.getAbsolutePath()+"'\n");
            pw.print("file '"+clip2.getAbsolutePath()+"'");
            pw.flush();
            pw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return f.getAbsolutePath();
    }
}
