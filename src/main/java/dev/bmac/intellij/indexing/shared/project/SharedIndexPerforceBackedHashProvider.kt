package dev.bmac.intellij.indexing.shared.project

import com.intellij.indexing.shared.platform.hash.SharedIndexContentHash
import com.intellij.indexing.shared.platform.hash.SharedIndexContentHashProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.IndexedFile
import org.jetbrains.idea.perforce.perforce.CommandArguments
import org.jetbrains.idea.perforce.perforce.P4File
import org.jetbrains.idea.perforce.perforce.PerforceRunner
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager
import java.io.*
import java.nio.ByteBuffer
import java.nio.file.Path
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
                    //TODO
                }
            }
        } else {
            val path = content.file.fileSystem.getNioPath(content.file)
            val revision = fileMap[path] ?: return null
            return ByteBuffer.allocate(Long.SIZE_BYTES).putLong(revision).array()
        }
        return null
    }

    fun buildFileMap(project: Project) {
        //TODO when can I delete the reference and clear up memory?
        fileMapRef.set(getHaveList(project))
    }

    companion object {
        private val LOGGER = Logger.getInstance(SharedIndexPerforceBackedHashProvider::class.java)
        private val FSTAT_CLIENT_PREFIX = "... clientFile "
        private val FSTAT_HAVEREV_PREFIX = "... haveRev "
        private val fileMapRef = AtomicReference<Map<Path, Long>?>()
        private var future : Future<*>? = null

        private fun getHaveList(project: Project) : Map<Path, Long> {
            val perforceRoot = P4File.create(project.projectFile!!.parent.parent)
            val runner = PerforceRunner.getInstance(project)
            val commandArguments = CommandArguments()
            //cstat returns a list of changes the client is aware of, which if it has (#have) them, can be used to determine
            //the latest the client has. Also there exists fstatBulk in the plugin, but we only need clientFile and haveRev
            //so using this to reduce the memory overhead for large codebases. Should add this back to perforce plugin
            commandArguments.append("fstat").append("-T clientFile,haveRev").append(perforceRoot.recursivePath + "#have")
            val manager = PerforceConnectionManager.getInstance(project)
            val execResult = runner.executeP4Command(commandArguments.arguments, manager.getConnectionForFile(perforceRoot)!!)
            return if (execResult.exitCode != 0) {
                LOGGER.error("fstat failed with non-zero exit code")
                emptyMap()
            } else {
                var map = emptyMap<Path,Long>()
                execResult.allowSafeStdoutUsage { map = parseFstat(it) }
                map
            }
        }

        private fun parseFstat(result : InputStream) : Map<Path, Long> {
            val reader = LineNumberReader(InputStreamReader(result))
            val fileMap = HashMap<Path, Long>()
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.startsWith(FSTAT_CLIENT_PREFIX)) {
                        val file = File(line!!.substring(FSTAT_CLIENT_PREFIX.length))
                        line = reader.readLine() ?: throw IOException("Unexpected end of file")
                        if (!line!!.startsWith(FSTAT_HAVEREV_PREFIX)) {
                            LOGGER.warn("Unexpected content in fstat result")
                            continue
                        }
                        val changelist = line!!.substring(FSTAT_HAVEREV_PREFIX.length)
                        fileMap[file.toPath()] = changelist.toLong()
                    }
                }
            } catch (e: IOException) {
                LOGGER.error("Failed to parse fstat result", e)
            }
            return fileMap
        }
    }
}