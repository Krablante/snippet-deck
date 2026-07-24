package com.rrajath.expander.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SnippetConverters {
    @TypeConverter
    fun aliasesToJson(aliases: List<String>): String = gson.toJson(aliases)

    @TypeConverter
    fun jsonToAliases(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<String>>(value, aliasesType).orEmpty()
        }.getOrDefault(emptyList())
    }

    private companion object {
        val gson = Gson()
        val aliasesType = object : TypeToken<List<String>>() {}.type
    }
}
