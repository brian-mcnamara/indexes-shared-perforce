package dev.bmac.intellij.indexing.shared.project

import com.intellij.indexing.shared.ultimate.project.ProjectSharedIndexRecentCommits
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.withPushPop
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.idea.perforce.application.PerforceManager
import org.jetbrains.idea.perforce.application.PerforceVcs
import org.jetbrains.idea.perforce.perforce.CommandArguments
import org.jetbrains.idea.perforce.perforce.P4File
import org.jetbrains.idea.perforce.perforce.PerforceRunner
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager
import java.io.*
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet

class PerforceRecentCommitProvider : ProjectSharedIndexRecentCommits {
    override fun listRecentCommits(project: Project, indicator: ProgressIndicator): List<String> {
        if (!Registry.`is`("shared.index.project.perforce.changes")) return emptyList()
        val roots = getPerforceRoots(project)
        //TODO handle perforce offline
        indicator.withPushPop {
            indicator.isIndeterminate = true
            indicator.text = "Listing recent Perforce changes" //TODO l10n
            return listRecentHaveChanges(project, roots).map { it.toString() }
        }
    }

    companion object {
        private val LOGGER = Logger.getInstance(PerforceRecentCommitProvider::class.java)
        private val CSTAT_PREFIX = "... change "

        private fun listRecentHaveChanges(project: Project, projectRoots: List<VirtualFile>) : List<Long> {
            val changes = LinkedHashSet<Long>()
            for (projectRoot in projectRoots) {
                val perforceRoot = P4File.create(projectRoot)
                val runner = PerforceRunner.getInstance(project)
                val commandArguments = CommandArguments()
                //cstat returns a list of changes the client is aware of, which if it has (#have) them, can be used to determine
                //the latest the client has
                commandArguments.append("cstat").append(perforceRoot.recursivePath + "#have")
                val manager = PerforceConnectionManager.getInstance(project)
                val execResult = runner.executeP4Command(commandArguments.arguments, manager.getConnectionForFile(perforceRoot)!!)
                if (execResult.exitCode != 0) {
                    LOGGER.error("p4 returned non-zero when querying cstat")
                } else {
                    execResult.allowSafeStdoutUsage { parseCstatHaveList(it, changes) }
                }
            }
            return changes.reversed()
        }

        private fun parseCstatHaveList(output: InputStream, collection : HashSet<Long>) {
            val reader = LineNumberReader(InputStreamReader(output))
            val maxEntries = Registry.intValue("shared.index.project.perforce.max_changes")
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.startsWith(CSTAT_PREFIX)) {
                        val changelist = line!!.substring(CSTAT_PREFIX.length)
                        collection.add(changelist.toLong())
                        //cstat can return a lot of records, pop items from the start (earliest)
                        if (collection.size > maxEntries) {
                            collection.remove(collection.iterator().next())
                        }
                    }
                }
            } catch (e: IOException) {
                LOGGER.error("Failed to parse cstat result", e)
            }
        }
    }
}