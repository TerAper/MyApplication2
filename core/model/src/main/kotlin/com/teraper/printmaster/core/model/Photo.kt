package com.teraper.printmaster.core.model

/** What a photo belongs to: a client's printer, one of its cartridges, an order, or an expense (receipt). */
enum class PhotoOwner { PRINTER, CARTRIDGE, ORDER, EXPENSE }

/** [thumbPath] is the small copy for lists, [fullPath] the one shown when opened. */
data class Photo(val id: Long, val owner: PhotoOwner, val ownerId: Long, val thumbPath: String, val fullPath: String)
