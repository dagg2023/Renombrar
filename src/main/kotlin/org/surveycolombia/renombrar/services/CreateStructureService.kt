/***************************************************************************
 * Renombrador de Archivos
 * --------------------
 * Copyright (C) 2025 by David Andres Gonzalez Gutierrez
 * Email: surveycolombia@gmail.com
 *
 * Servicio para creación de estructura de carpetas
 ***************************************************************************/

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
