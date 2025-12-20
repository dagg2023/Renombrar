package org.surveycolombia.renombrar.services

import java.io.File
import org.surveycolombia.renombrar.model.FolderNode

object StructureLoader {

    fun loadJson(file: File): List<FolderNode> {
        val text = file.readText(Charsets.UTF_8)
        val jsonArray = org.json.JSONArray(text)

        return parseArray(jsonArray)
    }

    private fun parseArray(array: org.json.JSONArray): List<FolderNode> {
        val namesInLevel = mutableSetOf<String>()
        val result = mutableListOf<FolderNode>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.getString("name").trim()

            require(name.isNotEmpty()) { "Nombre vacío" }
            require(!namesInLevel.contains(name.lowercase())) {
                "Nombre duplicado: $name"
            }
            namesInLevel.add(name.lowercase())

            val children = if (obj.has("children"))
                parseArray(obj.getJSONArray("children"))
            else emptyList()

            result.add(FolderNode(name, children))
        }
        return result
    }
}
