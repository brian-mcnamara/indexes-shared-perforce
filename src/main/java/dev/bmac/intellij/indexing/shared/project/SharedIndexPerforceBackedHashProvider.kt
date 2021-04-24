package dev.bmac.intellij.indexing.shared.project

import com.google.common.hash.Hashing
import com.intellij.indexing.shared.platform.hash.SharedIndexContentHash
import com.intellij.indexing.shared.platform.hash.SharedIndexContentHashProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.indexing.IndexedFile
import org.jetbrains.idea.perforce.perforce.CommandArguments
import org.jetbrains.idea.perforce.perforce.FStat
import org.jetbrains.idea.perforce.perforce.P4File
import org.jetbrains.idea.perforce.perforce.PerforceRunner
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.LineNumberReader
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

class SharedIndexPerforceBackedHashProvider : SharedIndexContentHashProvider {
    override val info: SharedIndexContentHash
        get() = SharedIndexPerforceBackendHash

    override fun computeHash(content: IndexedFile): ByteArray? {
        val fileMap = fileMapRef.get()
        if (fileMap == null) {
            if (future == null) {
                future = ApplicationManager.getApplication().executeOnPooledThread{ buildFileMap(content.project) }
            } else {
                if (future?.isDone == true) {
                    //TODO handle failures
                }
            }
        } else {
            val path = content.file.fileSystem.getNioPath(content.file)?.toString() ?: return null
            val fstat = fileMap[path] ?: return null

            if (fstat.action.isNotBlank()) {
                //The file is checked out and could have modifications.
                return null
            }

            //Hack hack for Salesforce...
            val fileIndex = ProjectRootManager.getInstance(content.project).fileIndex
            val isSource = fileIndex.isInSource(content.file)
            //TODO figure out hashing

            val hash = ByteBuffer.allocate(info.hashLength)
                    .putLong(fstat.haveRev.toULong().toLong())
                    .put(Hashing.sha1().hashString(fstat.depotFile, Charset.defaultCharset()).asBytes())
                    .put(if (isSource) 0x01 else 0x00)
                    .array()

            return hash
        }
        return null
    }

    fun buildFileMap(project: Project) {
        //TODO when can I delete the reference and clear up memory?
        fileMapRef.set(getHaveList(project))
    }

    companion object {
        private val LOGGER = Logger.getInstance(SharedIndexPerforceBackedHashProvider::class.java)
        private val FSTAT_DEPOT_PREFIX = "... depotFile "
        private val FSTAT_CLIENT_PREFIX = "... clientFile "
        private val FSTAT_HAVEREV_PREFIX = "... haveRev "
        private val FSTAT_ACTION_PREFIX = "... action "
        private val fileMapRef = AtomicReference<Map<String, FStat>?>()
        private var future : Future<*>? = null

        private fun getHaveList(project: Project) : Map<String, FStat> {
            val roots = getPerforceRoots(project)
            val fileMap = HashMap<String, FStat>()
            for (root in roots) {
                val perforceRoot = P4File.create(root)
                val runner = PerforceRunner.getInstance(project)
                val commandArguments = CommandArguments()
                //cstat returns a list of changes the client is aware of, which if it has (#have) them, can be used to determine
                //the latest the client has. Also there exists fstatBulk in the plugin, but we only need clientFile and haveRev
                //so using this to reduce the memory overhead for large codebases. Should add this back to perforce plugin
                commandArguments.append("fstat").append("-T depotFile,clientFile,haveRev,action").append(perforceRoot.recursivePath + "#have")
                val manager = PerforceConnectionManager.getInstance(project)
                val execResult = runner.executeP4Command(commandArguments.arguments, manager.getConnectionForFile(perforceRoot)!!)
                if (execResult.exitCode != 0) {
                    LOGGER.error("fstat failed with non-zero exit code")
                } else {
                    execResult.allowSafeStdoutUsage { parseFstat(it, fileMap) }
                }
            }
            return fileMap
        }

        private fun parseFstat(result : InputStream, fileMap : HashMap<String, FStat>) {
            val reader = LineNumberReader(InputStreamReader(result))
            try {
                var line: String?
                var fstat = FStat()
                while (reader.readLine().also { line = it } != null) {
                    when {
                        line!!.startsWith(FSTAT_DEPOT_PREFIX) -> {
                            fstat.depotFile = line!!.substring(FSTAT_DEPOT_PREFIX.length)
                        }
                        line!!.startsWith(FSTAT_CLIENT_PREFIX) -> {
                            fstat.clientFile = line!!.substring(FSTAT_CLIENT_PREFIX.length)
                        }
                        line!!.startsWith(FSTAT_HAVEREV_PREFIX) -> {
                            fstat.haveRev = line!!.substring(FSTAT_HAVEREV_PREFIX.length)
                        }
                        line!!.startsWith(FSTAT_ACTION_PREFIX) -> {
                            fstat.action = line!!.substring(FSTAT_ACTION_PREFIX.length)
                        }
                        line!!.isBlank() -> {
                            if (fstat.clientFile.isNotBlank()) {
                                fileMap[fstat.clientFile] = fstat
                            }
                            fstat = FStat()
                        }
                    }
                }
            } catch (e: IOException) {
                LOGGER.error("Failed to parse fstat result", e)
            }
        }
    }
}