package org.surveycolombia.renombrar.services

import java.io.File

object CreateStructureService {

    fun create(
        rootPath: File,
        structureFile: File
    ): List<String> {

        val structure = StructureLoader.loadJson(structureFile)
        val builder = FolderBuilder(rootPath)
        return builder.createStructure(structure)
    }
}
