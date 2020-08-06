package com.hipoint.snipback.room.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "Hd_snips")
public class Hd_snips {


    public final static String TABLE_NAME = "Hd_snips";
    private final static String COLUMN_HD_SNIP_ID = "hd_snip_id";
    private final static String COLUMN_SNIP_ID = "snip_id";
    private final static String COLUMN_VIDEO_PATH_PROCESSED = "video_path_processed";
    private final static String COLUMN_VIDEO_PATH_UNPROCESSED = "video_path_unprocessed";


    @PrimaryKey(autoGenerate = true)
    @NonNull
    @ColumnInfo(name = COLUMN_HD_SNIP_ID)
    private int hd_snip_id;

    @ColumnInfo(name = COLUMN_SNIP_ID)
    private int snip_id;
    @ColumnInfo(name = COLUMN_VIDEO_PATH_PROCESSED)
    private String video_path_processed;
    @ColumnInfo(name = COLUMN_VIDEO_PATH_UNPROCESSED)
    private String video_path_unprocessed;

    public int getHd_snip_id() {
        return hd_snip_id;
    }

    public void setHd_snip_id(int hd_snip_id) {
        this.hd_snip_id = hd_snip_id;
    }

    public int getSnip_id() {
        return snip_id;
    }

    public void setSnip_id(int snip_id) {
        this.snip_id = snip_id;
    }

    public String getVideo_path_processed() {
        return video_path_processed;
    }

    public void setVideo_path_processed(String video_path_processed) {
        this.video_path_processed = video_path_processed;
    }

    public String getVideo_path_unprocessed() {
        return video_path_unprocessed;
    }

    public void setVideo_path_unprocessed(String video_path_unprocessed) {
        this.video_path_unprocessed = video_path_unprocessed;
    }
}