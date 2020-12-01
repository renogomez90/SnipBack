package com.hipoint.snipback.Utils

data class BufferDataDetails(val bufferPath: String, val videoPath: String){
    override fun toString(): String {
        return """
            video path = $videoPath
            buffer path = $bufferPath
        """
    }
}