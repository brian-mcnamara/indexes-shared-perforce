package dev.bmac.intellij.indexing.shared.project

import com.intellij.indexing.shared.ultimate.project.ProjectSharedIndexRecentCommits
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.withPushPop
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.idea.perforce.application.PerforceVcs
import org.jetbrains.idea.perforce.perforce.CommandArguments
import org.jetbrains.idea.perforce.perforce.P4File
import org.jetbrains.idea.perforce.perforce.PerforceRunner
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager
import java.io.*
import java.util.*
import java.util.regex.Pattern

class PerforceRecentCommitProvider : ProjectSharedIndexRecentCommits {
    override fun listRecentCommits(project: Project, indicator: ProgressIndicator): List<String> {
        if (!Registry.`is`("shared.index.project.perforce.changes")) return emptyList()
        val projectRoot = project.projectFile!!.parent.parent
        val vcs = VcsUtil.getVcsFor(project, projectRoot)
        if (vcs !is PerforceVcs) return emptyList()
        //TODO handle perforce offline
        indicator.withPushPop {
            indicator.isIndeterminate = true
            indicator.text = "Listing recent Perforce changes" //TODO l10n
            return listRecentHaveChanges(project, projectRoot)
        }
    }

    companion object {
        private val LOGGER = Logger.getInstance(PerforceRecentCommitProvider::class.java)
        private val CSTAT_PATTERN = Pattern.compile("\\.\\.\\. change (\\d+)")

        private fun listRecentHaveChanges(project: Project, projectRoot: VirtualFile) : List<String> {
            val perforceRoot = P4File.create(projectRoot)
            val runner = PerforceRunner.getInstance(project)
            val commandArguments = CommandArguments()
            //cstat returns a list of changes the client is aware of, which if it has (#have) them, can be used to determine
            //the latest the client has
            commandArguments.append("cstat").append(perforceRoot.recursivePath + "#have")
            val manager = PerforceConnectionManager.getInstance(project)
            val execResult = runner.executeP4Command(commandArguments.arguments, manager.getConnectionForFile(perforceRoot)!!)
            return if (execResult.exitCode != 0) {
                LOGGER.error("p4 returned non-zero when querying cstat")
                emptyList()
            } else {
                var list = emptyList<String>()
                execResult.allowSafeStdoutUsage { list = parseCstatHaveList(it) }
                list
            }
        }

        private fun parseCstatHaveList(output: InputStream): List<String> {
            val result = LinkedList<String>()
            val reader = LineNumberReader(InputStreamReader(output))
            val maxEntries = Registry.intValue("shared.index.project.perforce.max_changes")
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val matcher = CSTAT_PATTERN.matcher(line!!)
                    if (matcher.matches()) {
                        val changelist = matcher.group(1)
                        result.add(changelist)
                        //cstat can return a lot of records, pop items from the start (earliest)
                        if (result.size > maxEntries) {
                            result.remove()
                        }
                    }
                }
            } catch (e: IOException) {
                LOGGER.error("Failed to parse cstat result", e)
            }
            result.reverse()
            return result
        }
    }
}