package dev.bmac.intellij.indexing.shared.project

import com.google.common.hash.Hashing
import com.intellij.indexing.shared.platform.hash.SharedIndexContentHash

object SharedIndexPerforceBackendHash : SharedIndexContentHash {
    override val hashLength: Int
        get() = Long.SIZE_BYTES + (Hashing.sha1().bits() / Byte.SIZE_BITS)
    override val performanceRank: Double
        get() = 400.0
    override val providerId: String
        get() = "perforce"
    override val version: String
        get() = "1"
}