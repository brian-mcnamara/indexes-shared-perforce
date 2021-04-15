package dev.bmac.intellij.indexing.shared.project

import com.intellij.indexing.shared.platform.hash.SharedIndexContentHash
import com.intellij.indexing.shared.platform.hash.SharedIndexContentHashExporter
import com.intellij.indexing.shared.platform.hash.SharedIndexContentHashExporterContext
import com.intellij.indexing.shared.platform.hash.SharedIndexContentHashProvider
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.withPushPop
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.idea.perforce.application.PerforceVcs

class SharedIndexPerforceContentHashExporter : SharedIndexContentHashExporter {
    override val info: SharedIndexContentHash
        get() = SharedIndexPerforceBackendHash

    override fun createHashProvider(context: SharedIndexContentHashExporterContext, indicator: ProgressIndicator): SharedIndexContentHashProvider? {
        val project = context.project
        val projectRoot = project.projectFile!!.parent.parent
        val vcs = VcsUtil.getVcsFor(project, projectRoot)
        if (vcs !is PerforceVcs) return null
        val provider = SharedIndexPerforceBackedHashProvider()
        indicator.withPushPop {
            indicator.isIndeterminate = true
            indicator.text = "Fetching file revisions from Perforce"
            provider.buildFileMap(project)
        }
        return provider
    }
}