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
    val tagId: Int,

    @ColumnInfo(name = "snip_id")
    val snipId: Int,

    @ColumnInfo(name = "audio_path")
    val audioPath: String,

    @ColumnInfo(name = "tag_colour_id")
    val colourId: Int,

    @ColumnInfo(name = "share_later")
    val shareLater: Boolean,

    @ColumnInfo(name = "export_later")
    val exportLater: Boolean,

    @ColumnInfo(name = "text_tag")
    val textTag: String
) : Parcelable