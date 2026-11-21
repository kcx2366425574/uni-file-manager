package com.kuangcx.ufm.core

enum class SortKey { NAME, SIZE, TIME, TYPE }

data class Entry(
    val name: String,
    val path: String,
    val dir: Boolean,
    val size: Long,
    val mtime: Long,
    val mime: String
)

fun sortEntries(entries: List<Entry>, key: SortKey, asc: Boolean): List<Entry> {
    val byName = compareBy<Entry> { it.name.lowercase() }
    val primary = when (key) {
        SortKey.NAME -> byName
        SortKey.SIZE -> compareBy { it.size }
        SortKey.TIME -> compareBy { it.mtime }
        SortKey.TYPE -> compareBy { it.mime }
    }.let { if (asc) it else it.reversed() }
    // Directories always float to the top; the chosen key is the tiebreak, name is the last resort.
    return entries.sortedWith(compareByDescending<Entry> { it.dir }.then(primary).then(byName))
}
