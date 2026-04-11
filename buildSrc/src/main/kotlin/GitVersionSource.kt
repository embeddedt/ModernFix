import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

abstract class GitVersionSource : ValueSource<String, GitVersionSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        val minecraftVersion: Property<String>
        val projectDir: DirectoryProperty
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val minecraftVersion = parameters.minecraftVersion.get()
        val workDir = parameters.projectDir.get().asFile

        val releaseLine = workDir.resolve("release_line.txt").readText().trim()

        val patch = try {
            // Find the most recent first-parent commit that touched release_line.txt
            val lineStartCommit = git(workDir,
                "log", "--first-parent",
                "-n", "1",
                "--format=%H",
                "--",
                "release_line.txt"
            ).trim()

            if (lineStartCommit.isEmpty()) {
                // count all first-parent commits as a safe fallback
                git(workDir, "rev-list", "--count", "--first-parent", "HEAD")
                    .trim().toIntOrNull() ?: 0
            } else {
                git(workDir, "rev-list", "--count", "--first-parent", "$lineStartCommit..HEAD")
                    .trim().toIntOrNull() ?: 0
            }
        } catch (_: Exception) {
            // Git is unavailable or this is not a git repository
            999
        }

        return "$releaseLine.$patch+mc$minecraftVersion"
    }

    private fun git(workDir: File, vararg args: String): String {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine("git", *args)
            standardOutput = output
            workingDir(workDir)
        }
        return output.toString(Charsets.UTF_8)
    }
}
