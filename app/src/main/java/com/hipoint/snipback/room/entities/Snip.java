package com.hipoint.snipback.room.entities;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "Snip")
public class Snip implements Parcelable {
    public final static String TABLE_NAME = "Snip";
    private final static String COLUMN_SNIP_ID = "snip_id";
    private final static String COLUMN_EVENT_ID = "event_id";
    private final static String COLUMN_SNIP_DURATION = "snip_duration";
    private final static String COLUMN_STARTTIME = "start_time";
    private final static String COLUMN_ENDTIME = "end_time";
    private final static String COLUMN_VID_CREATION_DATE = "vid_creation_date";
    private final static String COLUMN_PARENT_SNIP_ID = "parent_snip_id";
    private final static String COLUMN_HAS_VERTUAL_VERSIONS = "has_virtual_versions";
    private final static String COLUMN_IS_VERTUALVERSION = "is_virtual_version";
    private final static String COLUMN_TOTAL_VIDEO_DURATION = "total_video_duration";

    public Snip(){

    }

    protected Snip(Parcel in) {
        snip_id = in.readInt();
        event_id = in.readInt();
        snip_duration = in.readDouble();
        start_time = in.readDouble();
        end_time = in.readDouble();
        vid_creation_date = in.readLong();
        parent_snip_id = in.readInt();
        has_virtual_versions = in.readInt();
        is_virtual_version = in.readInt();
        total_video_duration = in.readInt();
        videoFilePath = in.readString();
        thumbnailPath = in.readString();
    }

    public static final Creator<Snip> CREATOR = new Creator<Snip>() {
        @Override
        public Snip createFromParcel(Parcel in) {
            return new Snip(in);
        }

        @Override
        public Snip[] newArray(int size) {
            return new Snip[size];
        }
    };

    public int getSnip_id() {
        return snip_id;
    }

    public void setSnip_id(int snip_id) {
        this.snip_id = snip_id;
    }

    public int getEvent_id() {
        return event_id;
    }

    public void setEvent_id(int event_id) {
        this.event_id = event_id;
    }

    public double getSnip_duration() {
        return snip_duration;
    }

    public void setSnip_duration(double snip_duration) {
        this.snip_duration = snip_duration;
    }

    public double getStart_time() {
        return start_time;
    }

    public void setStart_time(double start_time) {
        this.start_time = start_time;
    }

    public double getEnd_time() {
        return end_time;
    }

    public void setEnd_time(double end_time) {
        this.end_time = end_time;
    }

    public long getVid_creation_date() {
        return vid_creation_date;
    }

    public void setVid_creation_date(long vid_creation_date) {
        this.vid_creation_date = vid_creation_date;
    }

    public int getParent_snip_id() {
        return parent_snip_id;
    }

    public void setParent_snip_id(int parent_snip_id) {
        this.parent_snip_id = parent_snip_id;
    }

    public int getHas_virtual_versions() {
        return has_virtual_versions;
    }

    public void setHas_virtual_versions(int has_virtual_versions) {
        this.has_virtual_versions = has_virtual_versions;
    }

    public int getIs_virtual_version() {
        return is_virtual_version;
    }

    public void setIs_virtual_version(int is_virtual_version) {
        this.is_virtual_version = is_virtual_version;
    }

    public int getTotal_video_duration() {
        return total_video_duration;
    }

    public void setTotal_video_duration(int total_video_duration) {
        this.total_video_duration = total_video_duration;
    }

    public String getVideoFilePath() {
        return videoFilePath;
    }

    public void setVideoFilePath(String videoFilePath) {
        this.videoFilePath = videoFilePath;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    @PrimaryKey(autoGenerate = true)
    @NonNull
    @ColumnInfo(name = COLUMN_SNIP_ID)
    private int snip_id;

    @ColumnInfo(name = COLUMN_EVENT_ID)
    private int event_id;
    @ColumnInfo(name = COLUMN_SNIP_DURATION,typeAffinity = ColumnInfo.REAL)
    private  double snip_duration;
    @ColumnInfo(name = COLUMN_STARTTIME,typeAffinity = ColumnInfo.REAL)
    private  double start_time;
    @ColumnInfo(name = COLUMN_ENDTIME,typeAffinity = ColumnInfo.REAL)
    private double end_time;
    @ColumnInfo(name = COLUMN_VID_CREATION_DATE)
    private long vid_creation_date;
    @ColumnInfo(name = COLUMN_PARENT_SNIP_ID)
    private int parent_snip_id;
    @ColumnInfo(name = COLUMN_HAS_VERTUAL_VERSIONS)
    private int has_virtual_versions;
    @ColumnInfo(name = COLUMN_IS_VERTUALVERSION)
    private int is_virtual_version;
    @ColumnInfo(name = COLUMN_TOTAL_VIDEO_DURATION)
    private int total_video_duration;

    private String videoFilePath;
    private String thumbnailPath;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(snip_id);
        parcel.writeInt(event_id);
        parcel.writeDouble(snip_duration);
        parcel.writeDouble(start_time);
        parcel.writeDouble(end_time);
        parcel.writeLong(vid_creation_date);
        parcel.writeInt(parent_snip_id);
        parcel.writeInt(has_virtual_versions);
        parcel.writeInt(is_virtual_version);
        parcel.writeInt(total_video_duration);
        parcel.writeString(videoFilePath);
        parcel.writeString(thumbnailPath);
    }

    @Override()
    public boolean equals(Object other) {
        // This is unavoidable, since equals() must accept an Object and not something more derived
        if (other instanceof Snip) {
            // Note that I use equals() here too, otherwise, again, we will check for referential equality.
            // Using equals() here allows the Model class to implement it's own version of equality, rather than
            // us always checking for referential equality.
            Snip otherProduct = (Snip) other;
            return otherProduct.getSnip_id() == this.getSnip_id();
        }

        return false;
    }
}
