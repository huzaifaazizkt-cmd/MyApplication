package com.example.myapplication.data

internal fun filterAppList(apps: List<AppListItem>, locked: Set<String>, showLocked: Boolean, query: String): List<AppListItem> {
    val search = query.trim()
    return apps.filter { item ->
        (item.packageName in locked) == showLocked &&
            (search.isEmpty() || item.appName.contains(search, ignoreCase = true))
    }
}
