package com.hipoint.snipback.listener

import com.hipoint.snipback.Utils.TagFilter

interface IFilterListener {
    fun filterSet(tag: TagFilter?)
}