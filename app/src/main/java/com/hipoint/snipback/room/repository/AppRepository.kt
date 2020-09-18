package com.hipoint.snipback.room.repository

import android.content.Context
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.room.dao.EventDao
import com.hipoint.snipback.room.dao.Hd_snipsDao
import com.hipoint.snipback.room.dao.SnipsDao
import com.hipoint.snipback.room.db.RoomDB
import com.hipoint.snipback.room.entities.Event
import com.hipoint.snipback.room.entities.Hd_snips
import com.hipoint.snipback.room.entities.Snip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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

    interface OnTaskCompleted {
        suspend fun onTaskCompleted(snip: Snip?)
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

    /*
    private inner class InsertEventAsync(private val dao: EventDao) : AsyncTask<Event?, Void?, Void?>() {
        protected override fun doInBackground(vararg events: Event): Void? {
            AppClass.getAppInstance().lastEventId = dao.insert(events[0]).toInt()
            AppClass.getAppInstance().setLastCreatedEvent(events[0])
            return null
        }
    }
    */

    //data update
    suspend fun updateEvent(event: Event) {
//        UpdateEventAsync(eventDao).execute(event)
        withContext(IO) {
            eventDao.update(event)
        }
    }

    /*
        private inner class UpdateEventAsync(private val dao: EventDao) : AsyncTask<Event?, Void?, Void?>() {
            protected override fun doInBackground(vararg events: Event): Void? {
                dao.update(events[0])
                return null
            }
        }
        */
    //data delete
    suspend fun deleteEvent(event: Event) {
//        DeleteEventAsync(eventDao).execute(event)
        withContext(IO) {
            eventDao.delete(event)
        }
    }

/*
    private inner class DeleteEventAsync(private val dao: EventDao) : AsyncTask<Event?, Void?, Void?>() {
        protected override fun doInBackground(vararg events: Event): Void? {
            dao.delete(events[0])
            return null
        }
    }
*/

    suspend fun deleteAllEvent() {
//        DeleteAllEventAsync(eventDao).execute()
        withContext(IO) { eventDao.deleteAll() }
    }

/*
    private inner class DeleteAllEventAsync(private val dao: EventDao) : AsyncTask<Void?, Void?, Void?>() {
        protected override fun doInBackground(vararg voids: Void): Void? {
            dao.deleteAll()
            return null
        }
    }
*/

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

/*
    private inner class InsertHDSnipAsync(private val dao: Hd_snipsDao) : AsyncTask<Hd_snips?, Void?, Void?>() {
        protected override fun doInBackground(vararg hd_snips: Hd_snips): Void? {
            AppClass.getAppInstance().lastHDSnipId = dao.insert(hd_snips[0])
            return null
        }
    }
*/

    //data update
    suspend fun updateHDSnip(hd_snips: Hd_snips) {
//        UpdateHDSnipAsync(hd_snipsDao).execute(hd_snips)
        withContext(IO) {
            hd_snipsDao.update(hd_snips)
        }
    }

/*
    private inner class UpdateHDSnipAsync(private val dao: Hd_snipsDao) : AsyncTask<Hd_snips?, Void?, Void?>() {
        protected override fun doInBackground(vararg hd_snips: Hd_snips): Void? {
            dao.update(hd_snips[0])
            return null
        }
    }
*/

    //data delete
    suspend fun deleteHDSnip(hd_snips: Hd_snips) {
//        DeleteHDSnipAsync(hd_snipsDao).execute(hd_snips)
        withContext(IO) {
            hd_snipsDao.delete(hd_snips)
        }
    }
/*
    private inner class DeleteHDSnipAsync(private val dao: Hd_snipsDao) : AsyncTask<Hd_snips?, Void?, Void?>() {
        protected override fun doInBackground(vararg hd_snips: Hd_snips): Void? {
            dao.delete(hd_snips[0])
            return null
        }
    }*/

    suspend fun deleteAllHDSnip() {
//        DeleteAllHDSnipAsync(hd_snipsDao).execute()
        withContext(IO) {
            hd_snipsDao.deleteAll()
        }
    }

    /*
    private inner class DeleteAllHDSnipAsync(private val dao: Hd_snipsDao) : AsyncTask<Void?, Void?, Void?>() {
        protected override fun doInBackground(vararg voids: Void): Void? {
            dao.deleteAll()
            return null
        }
    }
*/
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
/*
    private inner class InsertSnipAsync(private val listener: OnTaskCompleted, private val dao: SnipsDao) : AsyncTask<Snip?, Void?, Snip>() {
        private var snipId = 0
        protected override fun doInBackground(vararg snips: Snip): Snip {
            snipId = dao.insert(snips[0]).toInt()
            AppClass.getAppInstance().lastSnipId = snipId
            return snips[0]
        }

        override fun onPostExecute(aVoid: Snip) {
//            aVoid.setSnip_id(AppClass.getAppInstance().getLastSnipId());
//  since lastSnipId is asyncly updated we are not getting the correct snip_id, only the latest one.
            aVoid.snip_id = snipId
            listener.onTaskCompleted(aVoid)
            super.onPostExecute(aVoid)
        }
    }
*/

    //data update
    suspend fun updateSnip(snip: Snip) {
//        UpdateSnipAsync(snipsDao).execute(snip)
        withContext(IO) {
            snipsDao.update(snip)
        }
    }

/*
    private inner class UpdateSnipAsync(private val dao: SnipsDao) : AsyncTask<Snip?, Void?, Void?>() {
        protected override fun doInBackground(vararg snips: Snip): Void? {
            dao.update(snips[0])
            return null
        }
    }
*/

    //data delete
    suspend fun deleteSnip(snip: Snip) {
//        DeleteSnipAsync(snipsDao).execute(snip)
        withContext(IO) {
            snipsDao.delete(snip)
        }
    }
/*

    private inner class DeleteSnipAsync(private val dao: SnipsDao) : AsyncTask<Snip?, Void?, Void?>() {
        protected override fun doInBackground(vararg snips: Snip): Void? {
            dao.delete(snips[0])
            return null
        }
    }
*/

    suspend fun deleteAllSnip() {
//        DeleteAllSnipAsync(snipsDao).execute()
        withContext(IO) {
            snipsDao.deleteAll()
        }
    }

    /*private inner class DeleteAllSnipAsync(private val dao: SnipsDao) : AsyncTask<Void?, Void?, Void?>() {
        protected override fun doInBackground(vararg voids: Void): Void? {
            dao.deleteAll()
            return null
        }
    }
*/

    var eventId = 0
    fun getLastInsertedEventId(activity: AppCompatActivity?): Int {
        val appViewModel = ViewModelProviders.of(activity!!).get(AppViewModel::class.java)
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
        val appViewModel = ViewModelProviders.of(activity!!).get(AppViewModel::class.java)
        appViewModel.snipsLiveData.observe(activity, Observer { snips: List<Snip> ->
            if (snips.isNotEmpty()) {
                val lastSnip = snips[snips.size - 1]
                snipId = lastSnip.snip_id
                AppClass.getAppInstance().lastSnipId = snipId
            }
        })
        return snipId
    } //Snip Table Actions END//

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
        val db = RoomDB.getDatabase(context)
        eventDao = db.eventDao()
        hd_snipsDao = db.hd_snipsDao()
        snipsDao = db.snipsDao()
    }
}