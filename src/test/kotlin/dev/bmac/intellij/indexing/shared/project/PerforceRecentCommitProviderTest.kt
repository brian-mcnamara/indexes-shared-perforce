package dev.bmac.intellij.indexing.shared.project

import com.intellij.mock.MockProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.idea.perforce.application.PerforceVcs
import org.jetbrains.idea.perforce.perforce.ExecResult
import org.jetbrains.idea.perforce.perforce.P4File
import org.jetbrains.idea.perforce.perforce.PerforceRunner
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class PerforceRecentCommitProviderTest : BasePlatformTestCase() {

    val perforceRunner : PerforceRunner = Mockito.mock(PerforceRunner::class.java)

    @BeforeEach
    public fun init() {
        setUp()

        ProjectLevelVcsManager.getInstance(project).directoryMappings = listOf(VcsDirectoryMapping(project.basePath!!, PerforceVcs.NAME))
    }

    @Test
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