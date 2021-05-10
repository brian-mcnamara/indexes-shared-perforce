package dev.bmac.intellij.indexing.shared.project

import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.jetbrains.idea.perforce.application.PerforceVcs
import org.jetbrains.idea.perforce.perforce.ExecResult
import org.jetbrains.idea.perforce.perforce.PerforceRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

class SharedIndexPerforceBackendHashProviderTest : BasePlatformTestCase() {

    val perforceRunner : PerforceRunner = Mockito.mock(PerforceRunner::class.java)

    @BeforeEach
    public fun init() {
        setUp()
        ProjectLevelVcsManager.getInstance(project).directoryMappings = listOf(VcsDirectoryMapping(project.basePath!!, PerforceVcs.NAME))
    }

    @Test
    fun testParseHaveList() {
        val execResult = ExecResult()
        execResult.exitCode = 0
        execResult.stdout = "... depotFile //repo/A.java\n" +
                "... clientFile /home/centos/projects/repo/A.java\n" +
                "... haveRev 10\n" +
                "\n" +
                "... depotFile //repo/B.java\n" +
                "... clientFile /home/centos/projects/repo/B.java\n" +
                "... haveRev 8\n" +
                "\n" +
                "... depotFile //repo/C.java\n" +
                "... clientFile /home/centos/projects/repo/C.java\n" +
                "... haveRev 5\n" +
                "... action edit\n" +
                "\n"

        Mockito.`when`(perforceRunner.executeP4Command(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(execResult)
        val haveList = SharedIndexPerforceBackedHashProvider({perforceRunner},
                { listOf(LocalFileSystem.getInstance().findFileByPath(project.basePath!!)!!) })
                .getHaveList(project)
        TestCase.assertEquals(3, haveList.size)

        val aFile = haveList.get("/home/centos/projects/repo/A.java")
        TestCase.assertNotNull(aFile)
        TestCase.assertEquals("10", aFile!!.haveRev)
        TestCase.assertEquals("//repo/A.java", aFile.depotFile)
        TestCase.assertTrue(aFile.action.isEmpty())


        val bFile = haveList.get("/home/centos/projects/repo/B.java")
        TestCase.assertNotNull(bFile)
        TestCase.assertEquals("8", bFile!!.haveRev)
        TestCase.assertEquals("//repo/B.java", bFile.depotFile)
        TestCase.assertTrue(bFile.action.isEmpty())

        val cFile = haveList.get("/home/centos/projects/repo/C.java")
        TestCase.assertNotNull(cFile)
        TestCase.assertEquals("5", cFile!!.haveRev)
        TestCase.assertEquals("//repo/C.java", cFile.depotFile)
        TestCase.assertEquals("edit", cFile.action)
    }
}