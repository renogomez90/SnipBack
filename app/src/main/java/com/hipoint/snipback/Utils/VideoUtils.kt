package com.hipoint.snipback.Utils

import VideoHandle.EpEditor
import VideoHandle.EpEditor.OutputOption
import VideoHandle.EpVideo
import VideoHandle.OnEditorListener
import android.media.MediaMetadataRetriever
import android.util.Log
import com.hipoint.snipback.listener.IVideoOpListener
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.util.*
import java.util.concurrent.TimeUnit

class VideoUtils(private val opListener: IVideoOpListener) {
    private val TAG = VideoUtils::class.java.simpleName

    /**
     * mergeRecordedFiles attempts to merge two video files into the same file
     * using coroutines to run in the background
     * uses clip1 dimensions for final merged file
     *
     * @param clip1      First clip to be merged
     * @param clip2      Second clip to be merged
     * @param outputPath Path to save output
     */
    suspend fun mergeRecordedFiles(clip1: File, clip2: File, outputPath: String) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(clip1.absolutePath)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
        retriever.release()
        val epVideos = arrayListOf<EpVideo>()
        epVideos.apply {
            add(EpVideo(clip1.absolutePath))
            add(EpVideo(clip2.absolutePath))
        }

        val options = OutputOption(outputPath)
        options.apply {
            setHeight(height)
            setWidth(width)
            frameRate = 30
            bitRate = 10 // default
        }

        EpEditor.merge(epVideos, options, object : OnEditorListener {
            override fun onSuccess() {
                Log.d(TAG, "Merge Success")
                opListener.changed(IVideoOpListener.VideoOp.MERGED, outputPath)
            }

            override fun onFailure() {
                Log.d(TAG, "Merge Failed")
                opListener.failed(IVideoOpListener.VideoOp.MERGED)
            }

            override fun onProgress(progress: Float) {}
        })
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
    suspend fun concatenateFiles(clip1: File, clip2: File, outputPath: String) {
        val retriever = MediaMetadataRetriever()

        retriever.setDataSource(clip1.absolutePath)
        val duration1 = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        retriever.setDataSource(clip2.absolutePath)
        val duration2 = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        retriever.release()

        val tmpFile = createFileList(clip1, clip2)
        val cmd = "-hide_banner -loglevel panic -f concat -safe 0 -i $tmpFile -c copy -y -b:v 1M $outputPath"

        Log.d(TAG, "concatenateFiles: cmd= $cmd")
        EpEditor.execCmd(cmd, duration1 + duration2, object : OnEditorListener {
            override fun onSuccess() {
                File(tmpFile).delete()
                Log.d(TAG, "Concat Success")
                opListener.changed(IVideoOpListener.VideoOp.CONCAT, outputPath)
            }

            override fun onFailure() {
                Log.d(TAG, "Concat Failed")
                opListener.failed(IVideoOpListener.VideoOp.CONCAT)
            }

            override fun onProgress(progress: Float) {}
        })
    }

    /**
     * Trims the full length clip to the last 2 minutes chunks of clipDuration
     * @param clip clip to be trimmed
     * @param parentFolder Path to save output
     */
    suspend fun trimToClip(clip: File, outputPath: String, startSecond: Int, endSecond: Int) {
        var start = startSecond
        var end = endSecond
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(clip.absolutePath)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong() // in miliseconds
        retriever.release()

        val sec = TimeUnit.MILLISECONDS.toSeconds(duration)

        if (sec <= start)    // fixes the times to match that of the video
            start = 0
        if (sec < end)
            end = sec.toInt()

        val cmd = "-hide_banner -loglevel panic -i ${clip.absolutePath} -ss $start -to $end -c copy -y $outputPath"

        Log.d(TAG, "trimToClip: cmd= $cmd")
        EpEditor.execCmd(cmd, 1, object : OnEditorListener {
            override fun onSuccess() {
                opListener.changed(IVideoOpListener.VideoOp.TRIMMED, outputPath)
            }

            override fun onFailure() {
                opListener.failed(IVideoOpListener.VideoOp.TRIMMED)
            }

            override fun onProgress(progress: Float) {}
        })
    }

    suspend fun splitVideo(clip: File, splitTime: Int, outputFolder: String) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(clip.absolutePath)
        var duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong() // in miliseconds
        retriever.release()

        duration = TimeUnit.MILLISECONDS.toSeconds(duration)
        if (splitTime > duration)
            throw IllegalArgumentException("splitTime must be within video duration")

        val cmd = "-hide_banner -loglevel panic -i ${clip.absolutePath} -acodec copy -f segment -segment_time $splitTime -vcodec copy -reset_timestamps 1 -map 0 $outputFolder/${clip.nameWithoutExtension}-%d.mp4"

        Log.d(TAG, "splitVideo: cmd= $cmd")

        EpEditor.execCmd(cmd, 1, object : OnEditorListener {
            override fun onSuccess() {
                opListener.changed(IVideoOpListener.VideoOp.SPLIT, "$outputFolder/${clip.nameWithoutExtension}")
            }

            override fun onFailure() {
                opListener.failed(IVideoOpListener.VideoOp.SPLIT)
            }

            override fun onProgress(progress: Float) {
            }

        })
    }

    private fun createFileList(clip1: File, clip2: File): String {
        val f = File("${clip1.parent}/tmp.txt")
        try {
            with(PrintWriter(FileWriter(f))) {
                print("file '${clip1.absolutePath}'\n")
                print("file '${clip2.absolutePath}'")
                flush()
                close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return f.absolutePath
    }
}