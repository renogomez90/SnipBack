package com.hipoint.snipback.room.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "Tag")
data class Tags(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "tag_id")
    val tagId        : Int,              //  ID of the entry

    @ColumnInfo(name = "snip_id")
    val snipId       : Int,              //  ID of the associated snip

    @ColumnInfo(name = "audio_path")
    val audioPath    : String,           //  path of the audio clip tag

    @ColumnInfo(name = "audio_position")
    val audioPosition: Int = 0,          //  when the audio should be played, 1 for start of the clip, 2 for end of the clip

    @ColumnInfo(name = "tag_colour_id")
    val colourId     : Int = 0,          //  based on ordinal of TagColours enum

    @ColumnInfo(name = "share_later")
    val shareLater   : Boolean = false,  //  marked for share later

    @ColumnInfo(name = "export_later")
    val exportLater  : Boolean = false,  //  marked for export later

    @ColumnInfo(name = "text_tag")
    val textTag      : String            //  text tag entry
) : Parcelable