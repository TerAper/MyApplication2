package com.teraper.printmaster.core.model

/** What a photo belongs to: a client's item, one of its parts, an order, an expense (receipt) or a catalog item. */
enum class PhotoOwner { CLIENT_ITEM, CLIENT_ITEM_PART, ORDER, EXPENSE, CATALOG_ITEM }

/** [thumbPath] is the small copy for lists, [fullPath] the one shown when opened. */
data class Photo(val id: Long, val owner: PhotoOwner, val ownerId: Long, val thumbPath: String, val fullPath: String)
