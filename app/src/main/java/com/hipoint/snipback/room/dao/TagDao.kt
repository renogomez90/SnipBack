package com.hipoint.snipback.room.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hipoint.snipback.enums.TagColours
import com.hipoint.snipback.room.entities.Tags

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(tag: Tags): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(tag: Tags)

    @Delete
    fun delete(tag: Tags)

    @Query("DELETE FROM Tag")
    fun deleteAll()

    @Query("SELECT * FROM Tag WHERE tag_id LIKE :id")
    fun getTagById(id: Int): Tags?

    @Query("SELECT * FROM Tag WHERE snip_id LIKE :id")
    fun getTagBySnipId(id: Int): Tags?

    @Query("SELECT * FROM Tag")
    fun getAll(): List<Tags>?

    @Query("SELECT snip_id FROM Tag WHERE tag_colour_id LIKE :colourTagName")
    fun getSnipIdsByColour(colourTagName: String): List<Int>?

    @Query("SELECT snip_id FROM Tag WHERE share_later LIKE 1")
    fun getSnipIdsByShareLater(): List<Int>?

    @Query("SELECT snip_id FROM Tag WHERE link_later LIKE 1")
    fun getSnipIdsByLinkLater(): List<Int>?

    @Query("SELECT snip_id FROM Tag WHERE instr(text_tag, :tagText)")
    fun getSnipIdsByTextTag(tagText: String): List<Int>?

    @Query("SELECT snip_id FROM Tag WHERE audio_path NOT NULL AND audio_path != ''")
    fun getSnipIdsByAudioTag(): List<Int>?

    @get:Query("SELECT * from Tag")
    val tagsData: LiveData<List<Tags>>

}