package org.surveycolombia.renombrar.services

import java.io.File
import org.surveycolombia.renombrar.model.FolderNode

class FolderBuilder(private val root: File) {

    fun createStructure(nodes: List<FolderNode>, level: Int = 0): List<String> {
        val errors = mutableListOf<String>()

        nodes.forEach { node ->
            val folder = File(root, sanitize(node.name))

            try {
                folder.mkdirs()
                println("${" ".repeat(level * 2)}[CREADO] ${folder.absolutePath}")
            } catch (e: Exception) {
                errors.add("Error creando ${folder.name}: ${e.message}")
            }

            if (node.children.isNotEmpty()) {
                val childBuilder = FolderBuilder(folder)
                errors.addAll(childBuilder.createStructure(node.children, level + 1))
            }
        }
        return errors
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[<>:"/\\|?*]"""), "_").trim()
}
