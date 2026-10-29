package com.example.myapplication.vault

/** A committed copy is never rolled back after attempting to delete an original. */
internal fun <T> importSafely(stage: () -> T, commit: (T) -> Unit, discardUncommitted: (T) -> Unit,
                              deleteOriginal: (T) -> Unit): T {
    val copy = stage()
    try { commit(copy) } catch (failure: Exception) {
        discardUncommitted(copy)
        throw failure
    }
    deleteOriginal(copy)
    return copy
}
