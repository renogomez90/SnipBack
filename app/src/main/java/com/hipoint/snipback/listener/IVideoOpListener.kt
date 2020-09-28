package com.hipoint.snipback.listener

import com.hipoint.snipback.listener.IVideoOpListener.VideoOp

/**
 * [IVideoOpListener] is used to listen to events of video editing during recording phase.
 * [VideoOp] enum of video operations that can be tracked for success or failure
 */
interface IVideoOpListener {
    enum class VideoOp {
        MERGED,  //  Videos merged with encoding; slower
        CONCAT,  //  videos concatenation without re-encoding; faster
        TRIMMED,  // Trimming video duration
        SPLIT,  // Split the video
        SPEED,  //   change the playback speed of the video
        FRAMES,  //  Extract frames from video
        KEY_FRAMES,  //  Adds additional keyframes for smoother seeking
        ERROR
    }

    fun failed(operation: VideoOp)
    fun changed(operation: VideoOp, processedVideoPath: String)
}