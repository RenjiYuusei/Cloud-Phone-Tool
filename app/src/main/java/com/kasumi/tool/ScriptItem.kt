package com.kasumi.tool

data class ScriptItem(
    val id: String,
    val name: String,
    val gameName: String,
    val url: String?, // URL to Lua script (will be wrapped in loadstring)
    val localPath: String? = null // Path to local .txt file
)

data class PreloadScript(
    val name: String,
    val gameName: String,
    val url: String // URL to Lua script
)
