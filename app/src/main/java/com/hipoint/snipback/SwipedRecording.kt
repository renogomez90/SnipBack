package com.hipoint.snipback

data class SwipedRecording(var originalFilePath: String?) {
    val timestamps: ArrayList<Int> = arrayListOf()
}