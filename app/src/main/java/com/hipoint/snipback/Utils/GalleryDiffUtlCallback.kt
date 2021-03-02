package com.hipoint.snipback.Utils

import androidx.recyclerview.widget.DiffUtil
import com.hipoint.snipback.room.entities.EventData

class GalleryDiffUtlCallback(
    private val oldList: List<EventData>,
    private val newList: List<EventData>,
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return if (oldItemPosition < oldList.size && newItemPosition < newList.size)
            oldList[oldItemPosition].event.event_id == newList[newListSize].event.event_id
        else false
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return if (oldItemPosition < oldList.size && newItemPosition < newList.size) {
            val sameId = oldList[oldItemPosition].event.event_id == newList[newListSize].event.event_id
            val samePath =
                oldList[oldItemPosition].event.event_created == newList[newListSize].event.event_created
            val sameDuration =
                oldList[oldItemPosition].event.event_title == newList[newListSize].event.event_title

            val sameSizeSnips =
                oldList[oldItemPosition].snips.size == newList[newItemPosition].snips.size
            val sameSizePSnips =
                oldList[oldItemPosition].parentSnip.size == newList[newItemPosition].parentSnip.size

            sameId && samePath && sameDuration && sameSizeSnips && sameSizePSnips
        }else false
    }

    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
        return super.getChangePayload(oldItemPosition, newItemPosition)
    }
}