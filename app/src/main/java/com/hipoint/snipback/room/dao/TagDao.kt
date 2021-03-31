package com.hipoint.snipback.room.dao

import androidx.lifecycle.LiveData
import androidx.room.*
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

    @get:Query("SELECT * from Tag")
    val tagsData: LiveData<List<Tags>>

}