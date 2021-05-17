package dev.bmac.intellij.indexing.shared.project

import com.intellij.mock.MockProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.idea.perforce.application.PerforceVcs
import org.jetbrains.idea.perforce.perforce.ExecResult
import org.jetbrains.idea.perforce.perforce.PerforceRunner
import org.mockito.Mockito

class PerforceRecentCommitProviderTest : BasePlatformTestCase() {

    val perforceRunner : PerforceRunner = Mockito.mock(PerforceRunner::class.java)

    override fun setUp() {
        super.setUp()
        ProjectLevelVcsManager.getInstance(project).directoryMappings = listOf(VcsDirectoryMapping(project.basePath!!, PerforceVcs.NAME))
    }

    override fun tearDown() {
        ProjectLevelVcsManager.getInstance(project).directoryMappings = listOf()
        super.tearDown()
    }

    fun testPerforceCommitProvider() {
        val execResult = ExecResult()
        execResult.exitCode = 0
        execResult.stdout = "... change 1\n" +
                "... status have\n" +
                "\n" +
                "... change 2\n" +
                "... status have\n" +
                "\n"

        Mockito.`when`(perforceRunner.executeP4Command(Mockito.any(), Mockito.any())).thenReturn(execResult)
        val recentCommitProvider = PerforceRecentCommitProvider({_: Project -> perforceRunner },
                { listOf(LocalFileSystem.getInstance().findFileByPath(project.basePath!!)!!) })
        val recentcommits = recentCommitProvider.listRecentCommits(project, MockProgressIndicator())
        assertContainsOrdered(recentcommits, listOf("2", "1"))
    }
}