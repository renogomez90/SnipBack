package com.hipoint.snipback.room.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import org.jetbrains.annotations.NotNull

@Parcelize
@Entity(tableName = "Tag")
data class Tags(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "tag_id")
    val tagId: Int? = null,      //  ID of the entry

    @NotNull
    @ColumnInfo(name = "snip_id")
    val snipId: Int,              //  ID of the associated snip

    @NotNull
    @ColumnInfo(name = "audio_path")
    val audioPath: String = "",           //  path of the audio clip tag

    @NotNull
    @ColumnInfo(name = "audio_position")
    val audioPosition: Int = 0,          //  when the audio should be played, 1 for start of the clip, 2 for end of the clip

    @NotNull
    @ColumnInfo(name = "tag_colour_id")
    val colourId: String = "",          //  based on TagColours enum, comma separated

    @ColumnInfo(name = "share_later")
    val shareLater: Boolean = false,  //  marked for share later

    @ColumnInfo(name = "link_later")
    val linkLater: Boolean = false,    //  marked for export later

    @ColumnInfo(name = "text_tag")
    val textTag: String = "",           //  text tag entry
) : Parcelable