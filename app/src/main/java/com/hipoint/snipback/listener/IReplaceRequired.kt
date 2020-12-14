package com.hipoint.snipback.listener

/**
 * gets details of files to be replaced
 * */
interface IReplaceRequired {
    fun replace(oldFilePath: String, newFilePath: String)
    fun parent(parentSnipId: Int)
}