package com.hipoint.snipback.listener

/**
 * save action listener
 */
interface ISaveListener {
    /**
     * saves as a new file
     * */
    fun saveAs()

    /**
     * save over existing file
     * */
    fun save()

    /**
     * cancels the save action
     * */
    fun cancel()

    /**
     * exit the current screen/fragment
     * */
    fun exit()
}