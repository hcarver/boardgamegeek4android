@file:JvmName("PreferenceUtils")

package com.boardgamegeek.extensions

import android.content.Context
import androidx.preference.PreferenceManager
import com.boardgamegeek.R
import com.boardgamegeek.provider.BggContract
import java.util.*

fun Context?.addSyncStatus(status: String): Boolean {
    if (this == null) return false
    if (status.isBlank()) return false
    if (isStatusSetToSync(status)) return false
    val statuses: MutableSet<String> = getSyncStatuses(null)?.toMutableSet() ?: mutableSetOf()
    statuses.add(status)
    return putStringSet(PREFERENCES_KEY_SYNC_STATUSES, statuses)
}

fun Context?.setSyncStatuses(statuses: Array<String>): Boolean {
    if (this == null) return false
    return putStringSet(PREFERENCES_KEY_SYNC_STATUSES, HashSet(listOf(*statuses)))
}

fun Context?.isStatusSetToSync(status: String): Boolean {
    if (this == null) return false
    return getSyncStatuses()?.contains(status) ?: false
}

fun Context?.getSyncStatuses(): Set<String>? {
    return getSyncStatuses(this?.resources?.getStringArray(R.array.pref_sync_status_default))
}

fun Context?.getSyncStatuses(defValues: Array<String>? = null): Set<String>? {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    val defSet = if (defValues == null) null else HashSet(listOf(*defValues))
    return sharedPreferences.getStringSet(PREFERENCES_KEY_SYNC_STATUSES, defSet)
}

fun Context?.getSyncStatusesAsSql(): String {
    val selection = StringBuilder()
    val statuses = getSyncStatuses() ?: emptySet()
    for (status in statuses) {
        if (status.isBlank()) continue
        if (selection.isNotBlank()) selection.append(" OR ")
        selection.append(when (status) {
            COLLECTION_STATUS_OWN -> BggContract.Collection.STATUS_OWN.isTrue()
            COLLECTION_STATUS_PREVIOUSLY_OWNED -> BggContract.Collection.STATUS_PREVIOUSLY_OWNED.isTrue()
            COLLECTION_STATUS_PREORDERED -> BggContract.Collection.STATUS_PREORDERED.isTrue()
            COLLECTION_STATUS_FOR_TRADE -> BggContract.Collection.STATUS_FOR_TRADE.isTrue()
            COLLECTION_STATUS_WANT -> BggContract.Collection.STATUS_WANT.isTrue()
            COLLECTION_STATUS_WANT_TO_BUY -> BggContract.Collection.STATUS_WANT_TO_BUY.isTrue()
            COLLECTION_STATUS_WANT_TO_PLAY -> BggContract.Collection.STATUS_WANT_TO_PLAY.isTrue()
            COLLECTION_STATUS_WISHLIST -> BggContract.Collection.STATUS_WISHLIST.isTrue()
            COLLECTION_STATUS_RATED -> BggContract.Collection.RATING.greaterThanZero()
            COLLECTION_STATUS_PLAYED -> BggContract.Collection.NUM_PLAYS.greaterThanZero()
            COLLECTION_STATUS_COMMENTED -> BggContract.Collection.COMMENT.notBlank()
            COLLECTION_STATUS_HAS_PARTS -> BggContract.Collection.HASPARTS_LIST.notBlank()
            COLLECTION_STATUS_WANT_PARTS -> BggContract.Collection.WANTPARTS_LIST.notBlank()
            else -> ""
        })
    }
    return selection.toString()
}

fun Context?.isCollectionSetToSync(): Boolean {
    val statuses = getSyncStatuses()
    return statuses != null && statuses.isNotEmpty()
}

private fun Context.putStringSet(key: String, value: Set<String>): Boolean {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    val editor = sharedPreferences.edit()
    editor.putStringSet(key, value)
    return editor.commit()
}

const val PREFERENCES_KEY_SYNC_STATUSES = "sync_statuses"

const val COLLECTION_STATUS_OWN = "own"
const val COLLECTION_STATUS_PREVIOUSLY_OWNED = "prevowned"
const val COLLECTION_STATUS_PREORDERED = "preordered"
const val COLLECTION_STATUS_FOR_TRADE = "trade"
const val COLLECTION_STATUS_WANT = "want"
const val COLLECTION_STATUS_WANT_TO_BUY = "wanttobuy"
const val COLLECTION_STATUS_WANT_TO_PLAY = "wanttoplay"
const val COLLECTION_STATUS_WISHLIST = "wishlist"
const val COLLECTION_STATUS_PLAYED = "played"
const val COLLECTION_STATUS_RATED = "rated"
const val COLLECTION_STATUS_COMMENTED = "comment"
const val COLLECTION_STATUS_HAS_PARTS = "hasparts"
const val COLLECTION_STATUS_WANT_PARTS = "wantparts"
