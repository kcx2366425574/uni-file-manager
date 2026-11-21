package com.kuangcx.ufm.core

enum class MediaKind { IMAGE, VIDEO, AUDIO, DOC }

private const val MEDIA_TYPE_IMAGE = 1
private const val MEDIA_TYPE_AUDIO = 2
private const val MEDIA_TYPE_VIDEO = 3

data class QuerySpec(
    val selection: String?,
    val selectionArgs: Array<String>?,
    val sortOrder: String,
    val limit: Int,
    val offset: Int
)

object MediaQuery {

    private const val MAX_PAGE = 1000

    /**
     * Builds the query spec for a MediaStore collection.
     *
     * [collection] says which URI the caller will query, because that changes what is legal:
     * `Images.Media` / `Video.Media` / `Audio.Media` are already filtered collections and reject
     * `media_type` with "Invalid token media_type" (verified on API 36). Only the `Files`
     * collection accepts the integer media_type column (IMAGE=1, AUDIO=2, VIDEO=3,
     * DOCUMENT=6, NONE=0 per MediaStore.Files.FileColumns).
     */
    fun build(
        kind: MediaKind,
        sinceMs: Long?,
        limit: Int,
        offset: Int,
        collection: Collection = Collection.FILES
    ): QuerySpec {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        when (collection) {
            Collection.FILES -> when (kind) {
                MediaKind.IMAGE -> clauses += "media_type=$MEDIA_TYPE_IMAGE"
                MediaKind.VIDEO -> clauses += "media_type=$MEDIA_TYPE_VIDEO"
                MediaKind.AUDIO -> clauses += "media_type=$MEDIA_TYPE_AUDIO"
                MediaKind.DOC ->
                    clauses += "media_type NOT IN ($MEDIA_TYPE_IMAGE,$MEDIA_TYPE_AUDIO,$MEDIA_TYPE_VIDEO)"
            }
            // Typed collections already return exactly one kind; adding media_type breaks them.
            Collection.TYPED -> Unit
        }
        if (sinceMs != null && sinceMs > 0) {
            clauses += "date_added>=?"
            args += (sinceMs / 1000).toString()
        }
        return QuerySpec(
            selection = clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            selectionArgs = args.toTypedArray(),
            sortOrder = "date_added DESC",
            limit = limit.coerceIn(1, MAX_PAGE),
            offset = offset.coerceAtLeast(0)
        )
    }

    enum class Collection { FILES, TYPED }
}
