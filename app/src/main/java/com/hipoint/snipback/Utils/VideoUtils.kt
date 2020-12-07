package com.hipoint.snipback.Utils

import VideoHandle.EpEditor
import VideoHandle.EpEditor.OutputOption
import VideoHandle.EpVideo
import VideoHandle.OnEditorListener
import android.media.MediaMetadataRetriever
import android.util.Log
import com.hipoint.snipback.enums.CurrentOperation
import com.hipoint.snipback.listener.IVideoOpListener
import com.hipoint.snipback.videoControl.SpeedDetails
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.lang.StringBuilder
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

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
    suspend fun mergeRecordedFiles(clip1: File, clip2: File, outputPath: String, comingFrom: CurrentOperation) {
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
                opListener.changed(IVideoOpListener.VideoOp.MERGED, comingFrom, outputPath)
            }

            override fun onFailure() {
                Log.d(TAG, "Merge Failed")
                opListener.failed(IVideoOpListener.VideoOp.MERGED, comingFrom)
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
    /*suspend fun concatenateFiles(clip1: File, clip2: File, outputPath: String, comingFrom: CurrentOperation) {
        val retriever = MediaMetadataRetriever()

        retriever.setDataSource(clip1.absolutePath)
        val duration1 = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        retriever.setDataSource(clip2.absolutePath)
        val duration2 = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        retriever.release()

        val tmpFile = createFileList(arrayListOf(clip1, clip2))
        val cmd = "-hide_banner -loglevel panic -f concat -safe 0 -i $tmpFile -g 2 -c copy -threads 4 -y -b:v 1M $outputPath"

        Log.d(TAG, "concatenateFiles: cmd= $cmd")
        try {
            EpEditor.execCmd(cmd, duration1 + duration2, object : OnEditorListener {
                override fun onSuccess() {
                    File(tmpFile).delete()
                    Log.d(TAG, "Concat Success")
                    opListener.changed(IVideoOpListener.VideoOp.CONCAT, comingFrom, outputPath)
                }

                override fun onFailure() {
                    Log.d(TAG, "Concat Failed")
                    opListener.failed(IVideoOpListener.VideoOp.CONCAT, comingFrom)
                }

                override fun onProgress(progress: Float) {}
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }*/

    suspend fun concatenateMultiple(fileList: List<File>, outputPath: String, comingFrom: CurrentOperation){
        val retriever = MediaMetadataRetriever()
        var totalDuration = 0L
        fileList.forEach {
            if(it.exists() && it.length() > 0) {
                retriever.setDataSource(it.absolutePath)
                totalDuration += retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
            }
        }
        retriever.release()

        val tmpFile = createFileList(fileList)
        val cmd = "-hide_banner -loglevel panic -f concat -safe 0 -i $tmpFile -g 2 -c copy -threads 4 -y -b:v 1M $outputPath"

        Log.d(TAG, "concatenateFiles: cmd= $cmd")
        try {
            EpEditor.execCmd(cmd, totalDuration, object : OnEditorListener {
                override fun onSuccess() {
                    File(tmpFile).delete()
                    Log.d(TAG, "Concat Success")
                    opListener.changed(IVideoOpListener.VideoOp.CONCAT, comingFrom, outputPath)
                }

                override fun onFailure() {
                    Log.d(TAG, "Concat Failed")
                    opListener.failed(IVideoOpListener.VideoOp.CONCAT, comingFrom)
                }

                override fun onProgress(progress: Float) {}
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    /**
     * Trims the full length clip to the last 2 minutes chunks of clipDuration
     * @param clip clip to be trimmed
     * @param parentFolder Path to save output
     */
    suspend fun trimToClip(clip: File, outputPath: String, startSecond: Int, endSecond: Int, comingFrom: CurrentOperation) {
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

//        val cmd = "-hide_banner -loglevel panic -i ${clip.absolutePath} -ss $start -to $end -preset ultrafast -y $outputPath"   // with re-encoding
        val cmd = "-hide_banner -loglevel panic -ss $start -i ${clip.absolutePath} -to $end -avoid_negative_ts make_zero -c copy -copyts -threads 4 -y $outputPath"   // without re-encoding

        Log.d(TAG, "trimToClip: cmd= $cmd")
        try {
            EpEditor.execCmd(cmd, 1, object : OnEditorListener {
                override fun onSuccess() {
                    opListener.changed(IVideoOpListener.VideoOp.TRIMMED, comingFrom, outputPath)
                }

                override fun onFailure() {
                    opListener.failed(IVideoOpListener.VideoOp.TRIMMED, comingFrom)
                }

                override fun onProgress(progress: Float) {}
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addIDRFrame(clip:File, outputFolder: String, comingFrom: CurrentOperation){

//        val cmd = "-i ${clip.absolutePath} -c:v libx264 -profile:v baseline -level 3.0 -x264opts keyint=5:min-keyint=5 -g 10 -movflags +faststart+rtphint -maxrate:v 4000k -bufsize:v 4500k -preset ultrafast -threads 4 -y $outputFolder/out.mp4"
        val cmd = "-i ${clip.absolutePath} -vcodec libx264 -x264-params keyint=3:min-keyint=3 -g 2 -preset ultrafast -threads 4 -y $outputFolder/out.mp4"
        EpEditor.execCmd(cmd, 1, object : OnEditorListener {
            override fun onSuccess() {
                //mv $outputFolder/out.mp4 ava_${clip.absolutePath}
                File("$outputFolder/out.mp4").renameTo(clip)
                opListener.changed(IVideoOpListener.VideoOp.KEY_FRAMES, comingFrom, clip.absolutePath)
            }

            override fun onFailure() {
                opListener.failed(IVideoOpListener.VideoOp.KEY_FRAMES, comingFrom)
            }

            override fun onProgress(progress: Float) {
            }
        })

    }

    suspend fun splitVideo(clip: File, splitTime: Int, outputFolder: String, comingFrom: CurrentOperation) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(clip.absolutePath)
        var duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong() // in miliseconds
        retriever.release()

        duration = TimeUnit.MILLISECONDS.toSeconds(duration)
        if (splitTime > duration)
            throw IllegalArgumentException("splitTime must be within video duration")

        val cmd = "-i ${clip.absolutePath} -f segment -segment_time $splitTime -g 2 -c copy -reset_timestamps 1 -map 0 -threads 4 $outputFolder/${clip.nameWithoutExtension}-%d.mp4"

        Log.d(TAG, "splitVideo: cmd= $cmd")

        try {
            EpEditor.execCmd(cmd, 1, object : OnEditorListener {
                override fun onSuccess() {
                    opListener.changed(IVideoOpListener.VideoOp.SPLIT, comingFrom, "$outputFolder/${clip.nameWithoutExtension}")
                }

                override fun onFailure() {
                    opListener.failed(IVideoOpListener.VideoOp.SPLIT, comingFrom)
                }

                override fun onProgress(progress: Float) {
                }

            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * changes the speed of the video segments detailed through
     * speedDetailsList
     *
     * @param clip video file to be modified
     * @param speedDetailsList ArrayList of SpeedDetails
     * @param outputPath Path to save output
     */
    suspend fun changeSpeed(clip: File, speedDetailsList: ArrayList<SpeedDetails>, outputPath: String, comingFrom: CurrentOperation) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(clip.absolutePath)
        val totalDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()

        val complexFilter = makeComplexFilter(speedDetailsList, totalDuration)
        Log.d(TAG, "changeSpeed: complexFilter = $complexFilter")
        /*val cmd = "-i ${clip.absolutePath} -filter_complex " +
                "[0:v]trim=0:2,setpts=PTS-STARTPTS[v1]; " + //from 0 to 2 sec normal speed; output reference is [v1]
                "[0:v]trim=2:5,setpts=2*(PTS-STARTPTS)[v2]; " + //from 2 to 5 sec slow down * 2
                "[0:v]trim=5,setpts=PTS-STARTPTS[v3]; " +   // 5 sec onwards normal speed
                "[0:a]atrim=0:2,asetpts=PTS-STARTPTS[a1]; " +   // audio follows the same pattern as above
                "[0:a]atrim=2:5,asetpts=PTS-STARTPTS,atempo=0.5[a2]; " +
                "[0:a]atrim=5,asetpts=PTS-STARTPTS[a3]; " +
                "[v1][a1][v2][a2][v3][a3]concat=n=3:v=1:a=1 " +
                "-preset superfast -profile:v baseline $outputPath"*/

        val cmd = "-i ${clip.absolutePath} -filter_complex " + complexFilter + " -map [outv] -map [outa] -strict -2 -g 2 -preset ultrafast -shortest -threads 4 -y $outputPath"

        Log.d(TAG, "changeSpeed: cmd = $cmd")

        EpEditor.execCmd(cmd, 1, object : OnEditorListener {
            override fun onSuccess() {
                opListener.changed(IVideoOpListener.VideoOp.SPEED, comingFrom, outputPath)
            }

            override fun onFailure() {
                opListener.failed(IVideoOpListener.VideoOp.SPEED, comingFrom)
            }

            override fun onProgress(progress: Float) {

            }
        })

    }

    /**
     * generates the filter complex for modifying the video speed as per the segment details
     *
     * @param speedDetailsList ArrayList of SpeedDetails
     * @param totalDuration of the video clip
     *
     * @return generated filter_complex string for ffmpeg
     */
    private fun makeComplexFilter(speedDetailsList: ArrayList<SpeedDetails>, totalDuration: Long): String {

        Log.d(TAG, "makeComplexFilter: received speed details = ")
        speedDetailsList.forEach { println(it.toString()) }

        var i = 1
        //  setting up the filter; checking to see if the start time is at 0
        val vFilterComplex = StringBuffer("")
        val aFilterComplex = StringBuffer("")
        if (speedDetailsList.first().timeDuration?.first!! > 0) {
            vFilterComplex.append("[0:v]trim=0:${speedDetailsList.first().timeDuration?.first!!.toFloat() / 1000},setpts=PTS-STARTPTS[v1];")
            aFilterComplex.append("[0:a]atrim=0:${speedDetailsList.first().timeDuration?.first!!.toFloat() / 1000},asetpts=PTS-STARTPTS,atempo=1[a1];")
            i++
        }

        speedDetailsList.forEachIndexed { index, it ->
            val startTime = it.timeDuration?.first!!.toFloat() / 1000
            val endTime = it.timeDuration?.second!!.toFloat() / 1000

            var k = if (it.isFast) it.multiplier.toFloat() else (1 / it.multiplier.toFloat())
            val audio = StringBuilder("")

            while (2 < k) {
                k /= 2
                audio.append("atempo=2.0,")
            }
            while (k < 0.5) {
                k *= 2
                audio.append("atempo=0.5,")
            }
            audio.append("atempo=$k")

            //  video
            if (it.isFast) {
                vFilterComplex.append("[0:v]trim=$startTime:$endTime,setpts=(PTS-STARTPTS)/${it.multiplier}[v$i];")
            } else {
                vFilterComplex.append("[0:v]trim=$startTime:$endTime,setpts=(PTS-STARTPTS)*${it.multiplier}[v$i];")
            }
            //  audio
            aFilterComplex.append("[0:a]atrim=$startTime:$endTime,asetpts=PTS-STARTPTS,$audio[a$i];")
            //  increments the counter
            i++

            //reset inbetween speed changes
            if(index < speedDetailsList.size - 1) {
                val nextStart = speedDetailsList[index + 1].timeDuration?.first!!.toFloat() / 1000

                //  video
                if (it.isFast) {
                    vFilterComplex.append("[0:v]trim=$endTime:$nextStart,setpts=PTS-STARTPTS[v$i];")
                } else {
                    vFilterComplex.append("[0:v]trim=$endTime:$nextStart,setpts=PTS-STARTPTS[v$i];")
                }
                //  audio
                aFilterComplex.append("[0:a]atrim=$endTime:$nextStart,asetpts=PTS-STARTPTS[a$i];")

                i++
            }
        }

        //  handles the video ending speed
        if (speedDetailsList.last().timeDuration?.second!! < totalDuration) {
            val startTime = speedDetailsList.last().timeDuration?.second!!.toFloat() / 1000
            val endTime = totalDuration.toFloat() / 1000

            vFilterComplex.append("[0:v]trim=$startTime:$endTime,setpts=PTS-STARTPTS[v$i];")
            aFilterComplex.append("[0:a]atrim=$startTime:$endTime,asetpts=PTS-STARTPTS[a$i];")
            i++
        }

        val filterComplex = vFilterComplex.append(aFilterComplex)

        for (j in 1 until i) {
            filterComplex.append("[v$j][a$j]")
        }
        filterComplex.append("concat=n=${i - 1}:v=1:a=1[outv][outa]")

        return filterComplex.toString()
    }

    suspend fun getThumbnails(clip: File, outputParent: String, comingFrom: CurrentOperation) {
        val outputPath = File("$outputParent/previewThumbs/")
        if (outputPath.exists())
            outputPath.deleteRecursively()
        outputPath.mkdirs()

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(clip.absolutePath)
        var duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong() // in miliseconds
        retriever.release()

        duration = TimeUnit.MILLISECONDS.toSeconds(duration)

        val interval = duration.toFloat() / 9

        val cmd = "-skip_frame nokey -i ${clip.absolutePath} -r 1/$interval -s 50x50 -frames:v 10 -threads 4 -y $outputParent/previewThumbs/thumb%03d.bmp"
        /*val cmd = if (duration >= 9)
            "-skip_frame nokey -i ${clip.absolutePath} -r 1/$interval -s 50x50 -frames:v 10 -threads 4 -y $outputParent/previewThumbs/thumb%03d.bmp"
        else    //  since we may not have enough key frames to skip over
            "-i ${clip.absolutePath} -r 1/$interval -s 50x50 -frames:v 10 -threads 4 -y $outputParent/previewThumbs/thumb%03d.bmp"*/

        Log.d(TAG, "getThumbnails: cmd = $cmd")

        EpEditor.execCmd(cmd, 1, object : OnEditorListener {
            override fun onSuccess() {
                opListener.changed(IVideoOpListener.VideoOp.FRAMES, comingFrom, outputPath.absolutePath)
            }

            override fun onFailure() {
                opListener.failed(IVideoOpListener.VideoOp.FRAMES, comingFrom)
            }

            override fun onProgress(progress: Float) {

            }
        })
    }

    private fun createFileList(clipList: List<File>): String {
        val f = File("${clipList[0].parent}/tmp.txt")
        try {
            with(PrintWriter(FileWriter(f))) {
                clipList.forEach {
                    print("file '${it.absolutePath}'\n")
                }
                flush()
                close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return f.absolutePath
    }
}