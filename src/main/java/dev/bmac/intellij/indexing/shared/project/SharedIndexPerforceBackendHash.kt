package dev.bmac.intellij.indexing.shared.project

import com.intellij.indexing.shared.platform.hash.SharedIndexContentHash

object SharedIndexPerforceBackendHash : SharedIndexContentHash {
    override val hashLength: Int
        get() = 25 //TODO random number!
    override val performanceRank: Double
        get() = 400.0
    override val providerId: String
        get() = "perforce"
    override val version: String
        get() = "1"
}