package dev.bmac.intellij.indexing.shared.project

import com.intellij.indexing.shared.platform.hash.SharedIndexContentHash
import com.intellij.indexing.shared.platform.hash.SharedIndexContentHashExporter
import com.intellij.indexing.shared.platform.hash.SharedIndexContentHashExporterContext
import com.intellij.indexing.shared.platform.hash.SharedIndexContentHashProvider
import com.intellij.openapi.progress.ProgressIndicator

class SharedIndexPerforceContentHashExporter : SharedIndexContentHashExporter {
    override val info: SharedIndexContentHash
        get() = SharedIndexPerforceBackendHash

    override fun createHashProvider(context: SharedIndexContentHashExporterContext, indicator: ProgressIndicator): SharedIndexContentHashProvider? {
        return SharedIndexPerforceBackedHashProvider()
    }
}