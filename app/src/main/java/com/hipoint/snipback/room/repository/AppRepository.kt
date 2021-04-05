package com.hipoint.snipback.room.repository

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.room.dao.EventDao
import com.hipoint.snipback.room.dao.Hd_snipsDao
import com.hipoint.snipback.room.dao.SnipsDao
import com.hipoint.snipback.room.dao.TagDao
import com.hipoint.snipback.room.db.RoomDB
import com.hipoint.snipback.room.entities.Event
import com.hipoint.snipback.room.entities.Hd_snips
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.room.entities.Tags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

class AppRepository(context: Context?) {
    private val eventDao: EventDao

    //Event Table Actions START//
    val EventData: LiveData<MutableList<Event>>?
        get() = eventDao.eventData
    private val hd_snipsDao: Hd_snipsDao
    private val HdSnipsData: LiveData<Hd_snips>? = null
    private val snipsDao: SnipsDao
    private val SnipData: LiveData<Snip>? = null
    private val tagDao: TagDao
    private val tagData: LiveData<List<Tags>>
        get() = tagDao.tagsData

    interface OnTaskCompleted {
        suspend fun onTaskCompleted(snip: Snip?)
    }

    interface HDSnipResult{
        suspend fun queryResult(hdSnips: List<Hd_snips>?)
    }

    fun getEventById(eventId: Int): LiveData<Event> {
        return eventDao.getEventByEventId(eventId)
    }

    //data insert
    suspend fun insertEvent(event: Event) {
        withContext(IO) {
            AppClass.getAppInstance().lastEventId = eventDao.insert(event).toInt()
            AppClass.getAppInstance().setLastCreatedEvent(event)
        }
    }

    //data update
    suspend fun updateEvent(event: Event) {
//        UpdateEventAsync(eventDao).execute(event)
        withContext(IO) {
            eventDao.update(event)
        }
    }

    //data delete
    suspend fun deleteEvent(event: Event) {
//        DeleteEventAsync(eventDao).execute(event)
        withContext(IO) {
            eventDao.delete(event)
        }
    }

    suspend fun deleteAllEvent() {
//        DeleteAllEventAsync(eventDao).execute()
        withContext(IO) { eventDao.deleteAll() }
    }

    //Event Table Actions END//
    //HDSNIP Table Actions START//
    val hDSnipsData: LiveData<List<Hd_snips>>
        get() = hd_snipsDao.hdSnipsData

    suspend fun insertHd_snips(hd_snips: Hd_snips) {
//        InsertHDSnipAsync(hd_snipsDao).execute(hd_snips)
        withContext(IO) {
            AppClass.getAppInstance().lastHDSnipId = hd_snipsDao.insert(hd_snips)
        }
    }

    //data update
    suspend fun updateHDSnip(hd_snips: Hd_snips) {
//        UpdateHDSnipAsync(hd_snipsDao).execute(hd_snips)
        withContext(IO) {
            hd_snipsDao.update(hd_snips)
        }
    }

    //data delete
    suspend fun deleteHDSnip(hd_snips: Hd_snips) {
//        DeleteHDSnipAsync(hd_snipsDao).execute(hd_snips)
        withContext(IO) {
            hd_snipsDao.delete(hd_snips)
        }
    }

    suspend fun deleteAllHDSnip() {
//        DeleteAllHDSnipAsync(hd_snipsDao).execute()
        withContext(IO) {
            hd_snipsDao.deleteAll()
        }
    }

    suspend fun getHDSnipsBySnipID(listener: HDSnipResult, snipId: Int){
        val result = CoroutineScope(IO).async{
            hd_snipsDao.getBySnipId(snipId)
        }
        listener.queryResult(result.await())
    }

    suspend fun getHDSnipById(listener: HDSnipResult, hdSnipId: Int){
        val result = CoroutineScope(IO).async{
            hd_snipsDao.getById(hdSnipId)
        }
        listener.queryResult(mutableListOf(result.await()))
    }

    suspend fun getAllHDSnips(): MutableList<Hd_snips>? {
        val result = CoroutineScope(IO).async {
            hd_snipsDao.getAll()
        }
        return result.await()
    }

    //HDSNIP Table Actions END//
    //SNIP table Actions START//
    val snipsData: LiveData<List<Snip>>
        get() = snipsDao.snipsData

    suspend fun insertSnip(listener: OnTaskCompleted, snip: Snip) {
//        InsertSnipAsync(listener, snipsDao).execute(snip)
        val result = CoroutineScope(IO).async {
            val appInstance = AppClass.getAppInstance()
            appInstance.lastSnipId = snipsDao.insert(snip).toInt()
            snip.snip_id = AppClass.getAppInstance().lastSnipId
            appInstance.snips.add(snip)
            snip
        }

        listener.onTaskCompleted(result.await())
    }

    suspend fun getSnipById(snipId: Int): Snip {
        val snip = CoroutineScope(IO).async {
            snipsDao.getSnipById(snipId)
        }
        return snip.await()
    }

    suspend fun getSnipByVideoPath(videoPath: String): Snip {
        val snip = CoroutineScope(IO).async {
            snipsDao.getSnipByVideoPath(videoPath)
        }
        return snip.await()
    }

    //data update
    suspend fun updateSnip(snip: Snip) {
//        UpdateSnipAsync(snipsDao).execute(snip)
        withContext(IO) {
            snipsDao.update(snip)
        }
    }

    //data delete
    suspend fun deleteSnip(snip: Snip) {
//        DeleteSnipAsync(snipsDao).execute(snip)
        withContext(IO) {
            snipsDao.delete(snip)
        }
    }

    suspend fun deleteAllSnip() {
//        DeleteAllSnipAsync(snipsDao).execute()
        withContext(IO) {
            snipsDao.deleteAll()
        }
    }

    suspend fun getAllSnips(): MutableList<Snip>? {
        val result = CoroutineScope(IO).async {
            snipsDao.getAll()
        }
        return result.await()
    }

    var eventId = 0
    fun getLastInsertedEventId(activity: AppCompatActivity?): Int {
        val appViewModel = ViewModelProvider(activity!!).get(AppViewModel::class.java)
        appViewModel.eventLiveData.observe(activity, Observer { events: List<Event> ->
            if (events.isNotEmpty()) {
                val lastEvent = events[events.size - 1]
                eventId = lastEvent.event_id
                AppClass.getAppInstance().lastEventId = eventId
                AppClass.getAppInstance().setLastCreatedEvent(lastEvent)
            }
        })
        return eventId
    }

    var snipId = 0
    fun getLastInsertedSnipId(activity: Fragment?): Int {
        val appViewModel = ViewModelProvider(activity!!).get(AppViewModel::class.java)
        appViewModel.snipsLiveData.observe(activity, Observer { snips: List<Snip> ->
            if (snips.isNotEmpty()) {
                val lastSnip = snips[snips.size - 1]
                snipId = lastSnip.snip_id
                AppClass.getAppInstance().lastSnipId = snipId
            }
        })
        return snipId
    } //Snip Table Actions END//


    //  Tags data
    suspend fun insertTag(tag: Tags) {
//        InsertSnipAsync(listener, snipsDao).execute(snip)
        val result = CoroutineScope(IO).async {
            tagDao.insert(tag)
        }
    }

    suspend fun getTagById(tagId: Int): Tags? {
        val tag = CoroutineScope(IO).async {
            tagDao.getTagById(tagId)
        }
        return tag.await()
    }

    suspend fun getTagBySnipId(snipId: Int): Tags? {
        val tag = CoroutineScope(IO).async {
            tagDao.getTagBySnipId(snipId)
        }
        return tag.await()
    }

    //data update
    suspend fun updateTag(tag: Tags) {
        withContext(IO) {
            tagDao.update(tag)
        }
    }

    //data delete
    suspend fun deleteTag(tag: Tags) {
        withContext(IO) {
            tagDao.delete(tag)
        }
    }

    suspend fun deleteAllTags() {
        withContext(IO) {
            tagDao.deleteAll()
        }
    }

    suspend fun getAllTags(): MutableList<Tags>? {
        val result = CoroutineScope(IO).async {
            tagDao.getAll()
        }
        return result.await() as? MutableList<Tags>
    }

    suspend fun getSnipIdsByColour(colourTagName: String): List<Int>?{
        val result = CoroutineScope(IO).async {
            tagDao.getSnipIdsByColour(colourTagName)
        }

        return result.await()
    }

    suspend fun getSnipIdsByShareLater(): List<Int>?{
        val result = CoroutineScope(IO).async {
            tagDao.getSnipIdsByShareLater()
        }

        return result.await()
    }

    suspend fun getSnipIdsByLinkLater(): List<Int>?{
        val result = CoroutineScope(IO).async {
            tagDao.getSnipIdsByLinkLater()
        }

        return result.await()
    }

    suspend fun getSnipIdsByTextTag(tagText: String): List<Int>?{
        val result = CoroutineScope(IO).async {
            tagDao.getSnipIdsByTextTag(tagText)
        }

        return result.await()
    }


    companion object {
        @JvmStatic
        var instance: AppRepository? = null
            get() {
                if (field == null) {
                    field = AppRepository(AppClass.getAppInstance().context)
                }
                return field
            }
            private set
    }

    init {
//        RoomDB db = AppClass.getAppInsatnce().database;
        val db      = RoomDB.getDatabase(context)
        eventDao    = db.eventDao()
        hd_snipsDao = db.hd_snipsDao()
        snipsDao    = db.snipsDao()
        tagDao      = db.tagDao()
    }
}