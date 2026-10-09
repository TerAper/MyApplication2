package com.teraper.printmaster.core.data.repository

import org.json.JSONArray

/** An imported row's columns as JSON [[title, value], …]: keeps file order and repeated titles. */
internal object RawFields {

    fun encode(fields: List<Pair<String, String>>): String? =
        if (fields.isEmpty()) null else JSONArray(fields.map { JSONArray(listOf(it.first, it.second)) }).toString()

    fun decode(json: String?): List<Pair<String, String>> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i -> array.getJSONArray(i).let { it.getString(0) to it.getString(1) } }
        }.getOrDefault(emptyList())
    }
}
