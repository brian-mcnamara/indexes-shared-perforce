package dev.bmac.intellij.indexing.shared.project

import com.intellij.indexing.shared.platform.hash.SharedIndexContentHash
import com.intellij.indexing.shared.platform.hash.SharedIndexContentHashProvider
import com.intellij.util.indexing.IndexedFile

class SharedIndexPerforceBackedHashProvider : SharedIndexContentHashProvider {
    override val info: SharedIndexContentHash
        get() = SharedIndexPerforceBackendHash

    override fun computeHash(content: IndexedFile): ByteArray? {
        TODO("Not yet implemented")
    }
}