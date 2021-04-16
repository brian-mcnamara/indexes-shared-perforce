package dev.bmac.intellij.indexing.shared.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.perforce.application.PerforceManager
import org.jetbrains.idea.perforce.application.PerforceVcs

fun getPerforceRoots(project: Project) : List<VirtualFile> = ProjectLevelVcsManager.getInstance(project)
            .allActiveVcss
            .filterIsInstance<PerforceVcs>()
            .map { it.rootsByConnections }
            .flatten()
            .filter {
                try {
                    PerforceManager.ensureValidClient(project, it.getFirst())
                    true
                } catch (e : VcsException) {
                    false
                }}.map { it.second }.flatten().toList()