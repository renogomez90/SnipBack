package com.hipoint.snipback.listener;

/**
 * {@link IVideoOpListener} is used to listen to events of video editing during recording phase.
 * {@link VideoOp} enum of video operations that can be tracked for success or failure
 */
public interface IVideoOpListener {
    enum VideoOp{
        MERGED, //  Videos merged with encoding; slower
        CONCAT, //  videos concatenation without re-encoding; faster
        TRIMMED, // Trimming video duration
        SPLIT, // Split the video
        SPEED, //   change the playback speed of the video
        FRAMES, //  Extract frames from video
        KEY_FRAMES, //  Adds additional keyframes for smoother seeking
        ERROR
    }

    void failed(VideoOp operation);
    void changed(VideoOp operation, String outputVideoPath);
}
