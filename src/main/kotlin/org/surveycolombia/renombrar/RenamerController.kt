/***************************************************************************
 * Renombrador de Archivos
 * --------------------
 * Copyright (C) 2025 by David Andres Gonzalez Gutierrez
 * Email: surveycolombia@gmail.com
 *
 * Este software se distribuye bajo la licencia GNU GPL v3 con restricciones adicionales.
 * Puedes usarlo, modificarlo y distribuirlo libremente, siempre y cuando se mantenga
 * este aviso de copyright y la licencia original.
 *
 * Derechos de Autor:
 * El autor mantiene todos los derechos intelectuales sobre el software.
 * Cualquier modificación o redistribución debe reconocer la autoría original.
 *
 * Restricción Comercial:
 * Queda prohibido el uso comercial de este software sin la autorización expresa
 * y por escrito del autor.
 *
 * GNU General Public License v3.0:
 * https://www.gnu.org/licenses/gpl-3.0.html
 ***************************************************************************/

package org.surveycolombia.renombrar

import javafx.beans.value.ObservableValue
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Stage
import java.io.*
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ResultadoRenombrado(
    val exitos: MutableList<String> = mutableListOf(),
    val errores: MutableList<String> = mutableListOf(),
    val omitidos: MutableList<String> = mutableListOf(),
    val archivosLargos: MutableList<String> = mutableListOf(),
    val archivosExistentes: MutableList<String> = mutableListOf()
)

class RenamerController {

    // === Componentes FXML ===
    @FXML private lateinit var csvSection: VBox
    @FXML private lateinit var buscarCarpeta: Label
    @FXML private lateinit var btnGenerarCSV: Button
    @FXML private lateinit var cargarCSV: Label
    @FXML private lateinit var btnCargarCSV: Button
    @FXML private lateinit var btnRenamer: Button
    @FXML private lateinit var choiceColumnOldName: ChoiceBox<String>
    @FXML private lateinit var choiceColumnNewName: ChoiceBox<String>
    @FXML private lateinit var chkRenombrarCarpetas: CheckBox
    @FXML private lateinit var chkRenombrarArchivos: CheckBox

    // === Variables de estado ===
    private var selectedDirectory: File? = null
    private var selectedCsvFile: File? = null
    private val nameMappings = mutableMapOf<String, String>()

    private val fileSuffixes by lazy {
        val baseSuffixes = listOf(
            "_SIN_BAN", "_SIN_COC", "_CROQUIS", "_ANEXO",
            "_AD", "_EP", "_SJ", "_DP", "_DI", "_FAC", "_ACA",
            "_EST", "_COC", "_BAN", "_CER", "_LOTE", "_PRI", "_OTRO", "_NC"
        )

        val letterSuffixes = listOf(
            "_FAC", "_ACA", "_EST", "_COC", "_BAN",
            "_NC", "_SIN_BAN", "_SIN_COC", "_CER"
        )

        val compoundSuffixes = ('A'..'Z').flatMap { letter ->
            letterSuffixes.map { suffix -> "_${letter}$suffix" }
        }

        compoundSuffixes + baseSuffixes.sortedByDescending { it.length }
    }

    @FXML
    private fun initialize() {

        btnCargarCSV.isDisable = true
        choiceColumnNewName.isDisable = true
        choiceColumnOldName.isDisable = true
        btnGenerarCSV.isDisable = true

        // 🔧 EJECUTAR SIEMPRE ACTIVO
        btnRenamer.isDisable = false

        chkRenombrarArchivos.isSelected = false
        chkRenombrarCarpetas.isSelected = true

        val checkListener =
            { _: ObservableValue<out Boolean>, _: Boolean, _: Boolean ->
                updateUIState()
            }

        chkRenombrarCarpetas.selectedProperty().addListener(checkListener)
        chkRenombrarArchivos.selectedProperty().addListener(checkListener)
    }

    private fun updateUIState() {

        val soloArchivos =
            chkRenombrarArchivos.isSelected && !chkRenombrarCarpetas.isSelected

        csvSection.isVisible = !soloArchivos
        csvSection.isManaged = !soloArchivos

        btnGenerarCSV.isDisable = selectedDirectory == null
        btnCargarCSV.isDisable = selectedDirectory == null || soloArchivos

        // 🔧 NO deshabilitar nunca el botón Ejecutar
        btnRenamer.isDisable = false
    }

    @FXML
    private fun buscarCarpetaRaizClick() {

        val stage = btnRenamer.scene.window as Stage
        val directoryChooser =
            DirectoryChooser().apply { title = "Seleccionar carpeta raíz" }

        selectedDirectory = directoryChooser.showDialog(stage)
        buscarCarpeta.text =
            selectedDirectory?.absolutePath ?: "No ha seleccionado carpeta"

        updateUIState()
    }

    @FXML
    private fun generarCSVConSubcarpetasClick() {

        if (selectedDirectory == null) {
            mostrarAlerta("Error", "Seleccione una carpeta raíz primero.",
                Alert.AlertType.ERROR)
            return
        }

        val csvFile = File(selectedDirectory, "subcarpetas.csv")

        try {
            csvFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write("nombre_actual\n")
                selectedDirectory!!
                    .listFiles { f -> f.isDirectory }
                    ?.forEach { writer.write("${it.name}\n") }
            }
            mostrarAlerta("Éxito",
                "CSV generado en:\n${csvFile.absolutePath}")
        } catch (e: Exception) {
            mostrarAlerta("Error",
                "No se puede generar el CSV.",
                Alert.AlertType.ERROR)
        }
    }

    @FXML
    private fun cargarArchivoCSVClick() {

        val stage = btnRenamer.scene.window as Stage
        val fileChooser = FileChooser().apply {
            title = "Seleccionar Archivo CSV"
            extensionFilters.add(
                FileChooser.ExtensionFilter("Archivos CSV", "*.csv")
            )
        }

        selectedCsvFile = fileChooser.showOpenDialog(stage)
        cargarCSV.text =
            selectedCsvFile?.name ?: "No se seleccionó ningún archivo"

        leerColumnasCSV()
        updateUIState()
    }

    private fun leerColumnasCSV() {

        val (header, _) =
            leerEncabezadoYSeparador(selectedCsvFile)
                ?: run {
                    mostrarAlerta("Error",
                        "Error al leer el archivo CSV",
                        Alert.AlertType.ERROR)
                    return
                }

        choiceColumnOldName.items.setAll(header)
        choiceColumnNewName.items.setAll(header)
        choiceColumnOldName.isDisable = false
        choiceColumnNewName.isDisable = false
    }

    private fun leerEncabezadoYSeparador(
        archivo: File?
    ): Pair<List<String>, String>? {

        if (archivo == null) return null

        return try {
            archivo.bufferedReader(Charsets.UTF_8).use {
                val line = it.readLine() ?: return null
                val sep = detectSeparator(line)
                Pair(line.split(sep).map { h -> h.trim() }, sep)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun detectSeparator(line: String): String {

        val semicolon = line.count { it == ';' }
        val comma = line.count { it == ',' }
        val tab = line.count { it == '\t' }

        return when {
            semicolon > comma && semicolon > tab -> ";"
            tab > comma && tab > semicolon -> "\t"
            else -> ","
        }
    }

    @FXML
    private fun renombrarClic() {

        val dir = selectedDirectory ?: run {
            mostrarAlerta("Advertencia",
                "Seleccione la carpeta primero.")
            return
        }

        if (!chkRenombrarCarpetas.isSelected &&
            !chkRenombrarArchivos.isSelected) {
            mostrarAlerta("Advertencia",
                "Seleccione al menos una opción de renombrado")
            return
        }

        if (chkRenombrarCarpetas.isSelected) {
            cargarMapeoDeCSV(
                choiceColumnOldName.selectionModel.selectedItem,
                choiceColumnNewName.selectionModel.selectedItem
            )
            recorrerYRenombrarCarpetas(dir)
        }

        if (chkRenombrarArchivos.isSelected) {
            recorrerYRenombrarArchivos(dir)
        }

        mostrarAlerta("Completado",
            "Proceso de renombrado finalizado")
    }

    private fun cargarMapeoDeCSV(
        oldNameColumn: String,
        newNameColumn: String
    ) {

        val (header, sep) =
            leerEncabezadoYSeparador(selectedCsvFile) ?: return

        val indexOld = header.indexOf(oldNameColumn)
        val indexNew = header.indexOf(newNameColumn)

        nameMappings.clear()

        selectedCsvFile!!
            .bufferedReader(Charsets.UTF_8)
            .useLines { lines ->
                lines.drop(1).forEach { line ->
                    if (line.isBlank()) return@forEach
                    val v = line.split(sep).map { it.trim() }
                    if (v.size > maxOf(indexOld, indexNew)) {
                        nameMappings[v[indexOld]] = v[indexNew]
                    }
                }
            }
    }

    private fun recorrerYRenombrarCarpetas(directory: File) {

        val resultado = ResultadoRenombrado()

        nameMappings.toList()
            .sortedByDescending { it.first.length }
            .forEach { (oldName, newName) ->

                val src = File(directory, oldName)
                val dst = File(directory, newName)

                when {
                    !src.exists() ->
                        resultado.errores.add("No existe: $oldName")

                    dst.exists() ->
                        resultado.archivosExistentes.add(newName)

                    src.renameTo(dst) ->
                        resultado.exitos.add("$oldName -> $newName")

                    else ->
                        resultado.errores.add("Error: $oldName")
                }
            }

        generarResumenYLog(resultado,
            "RENOMBRADO DE CARPETAS")
    }

    private fun recorrerYRenombrarArchivos(raiz: File) {

        val resultado = ResultadoRenombrado()

        raiz.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->

                val parent = file.parentFile ?: return@forEach

                val suffix = fileSuffixes.firstOrNull {
                    file.nameWithoutExtension.endsWith(it)
                } ?: return@forEach

                val ext =
                    if (file.extension.isNotEmpty())
                        ".${file.extension}" else ""

                val newName = "${parent.name}$suffix$ext"
                val newFile = File(parent, newName)

                when {
                    newFile.exists() ->
                        resultado.archivosExistentes.add(newName)

                    else -> try {
                        Files.move(file.toPath(), newFile.toPath())
                        resultado.exitos.add(
                            "${file.name} -> $newName"
                        )
                    } catch (e: IOException) {
                        resultado.errores.add(file.name)
                    }
                }
            }

        generarResumenYLog(resultado,
            "RENOMBRADO DE ARCHIVOS")
    }

    private fun generarResumenYLog(
        resultado: ResultadoRenombrado,
        titulo: String
    ) {

        val resumen = buildString {
            appendLine("=== $titulo ===")
            appendLine("Éxitos: ${resultado.exitos.size}")
            appendLine("Errores: ${resultado.errores.size}")
            appendLine("Omitidos: ${resultado.omitidos.size}")
        }

        println(resumen)
        escribeLog(resumen)
    }

    private fun mostrarAlerta(
        titulo: String,
        mensaje: String,
        tipo: Alert.AlertType =
            Alert.AlertType.INFORMATION
    ) {

        Alert(tipo).apply {
            title = titulo
            contentText = mensaje
            showAndWait()
        }
    }

    private fun escribeLog(mensaje: String) {

        val formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        selectedDirectory?.let { dir ->
            File(dir, "Renombrador.log").appendText(
                """
                
                ================================================================================
                EJECUCIÓN: ${LocalDateTime.now().format(formatter)}
                ================================================================================
                $mensaje
                
                """.trimIndent()
            )
        }
    }
}
