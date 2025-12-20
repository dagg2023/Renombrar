package org.surveycolombia.renombrar.model

data class FolderNode(
    val name: String,
    val children: List<FolderNode> = mutableListOf()
)