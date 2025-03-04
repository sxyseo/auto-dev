package cc.unitmesh.devti.language.compiler.exec

import cc.unitmesh.devti.devin.InsCommand
import cc.unitmesh.devti.devin.dataprovider.BuiltinCommand
import cc.unitmesh.devti.language.utils.lookupFile
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern


/**
 * The `DirInsCommand` class is responsible for listing files and directories in a tree-like structure for a given directory path within a project.
 * It implements the `InsCommand` interface and provides an `execute` method to perform the directory listing operation asynchronously.
 *
 * The tree structure is visually represented using indentation and symbols (`├──`, `└──`) to denote files and subdirectories. Files are listed
 * first, followed by subdirectories, which are recursively processed to display their contents.
 *
 * Example output:
 * ```
 * myDirectory/
 *   ├── file1.txt
 *   ├── file2.txt
 *   └── subDirectory/
 *       ├── file3.txt
 *       └── subSubDirectory/
 *           └── file4.txt
 * ```
 *
 * About depth design:
 * In Java Langauge, the depth of dir are very long
 * In JavaScript Langauge, the dirs files are too many
 *
 * @param myProject The project instance in which the directory resides.
 * @param dir The path of the directory to list.
 */
class DirInsCommand(private val myProject: Project, private val dir: String) : InsCommand {
    override val commandName: BuiltinCommand = BuiltinCommand.DIR
    private val HASH_FILE_PATTERN: Pattern = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:\\.json|@[0-9a-f]+\\.json)$",
        Pattern.CASE_INSENSITIVE
    )

    fun isHashJson(file: VirtualFile?): Boolean {
        return file != null && HASH_FILE_PATTERN.matcher(file.name).matches()
    }

    private val output = StringBuilder()

    override suspend fun execute(): String? {
        val virtualFile = myProject.lookupFile(dir) ?: return "File not found: $dir"
        val future = CompletableFuture<String>()
        val task = object : Task.Backgroundable(myProject, "Processing context", false) {
            override fun run(indicator: ProgressIndicator) {
                val psiDirectory = runReadAction {
                    PsiManager.getInstance(myProject!!).findDirectory(virtualFile)
                }

                if (psiDirectory == null) {
                    future.complete("Directory not found: $dir")
                    return
                }

                output.appendLine("$dir/")
                runReadAction { listDirectory(myProject!!, psiDirectory, 1) }
                future.complete(output.toString())
            }
        }

        ProgressManager.getInstance()
            .runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))

        return future.get()
    }

    private fun listDirectory(project: Project, directory: PsiDirectory, depth: Int) {
        if (isExclude(project, directory)) return

        val files = directory.files
        val subdirectories = directory.subdirectories

        for ((index, file) in files.withIndex()) {
            /// skip binary files? ignore hashed file names, like `f5086740-a1a1-491b-82c9-ab065a9d1754.json`
            if (file.fileType.isBinary) continue
            if (isHashJson(file.virtualFile)) continue

            if (index == files.size - 1) {
                output.appendLine("${"  ".repeat(depth)}└── ${file.name}")
            } else {
                output.appendLine("${"  ".repeat(depth)}├── ${file.name}")
            }
        }

        for ((index, subdirectory) in subdirectories.withIndex()) {
            if (isExclude(project, directory)) continue

            if (index == subdirectories.size - 1) {
                output.appendLine("${"  ".repeat(depth)}└── ${subdirectory.name}/")
            } else {
                output.appendLine("${"  ".repeat(depth)}├── ${subdirectory.name}/")
            }
            listDirectory(project, subdirectory, depth + 1)
        }
    }

    private fun isExclude(project: Project, directory: PsiDirectory): Boolean {
        if (directory.name == ".idea") return true

        val status = FileStatusManager.getInstance(project).getStatus(directory.virtualFile)
        return status == FileStatus.IGNORED
    }
}

