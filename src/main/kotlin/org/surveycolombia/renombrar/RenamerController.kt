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
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Clase que almacena el resultado de una operación de renombrado
 */
data class ResultadoRenombrado(
    val exitos: MutableList<String> = mutableListOf(),
    val errores: MutableList<String> = mutableListOf(),
    val omitidos: MutableList<String> = mutableListOf(),
    val archivosLargos: MutableList<String> = mutableListOf(),
    val archivosExistentes: MutableList<String> = mutableListOf(),
    val sinSufijo: MutableList<String> = mutableListOf()  // Nuevo: archivos sin sufijo reconocido
)

class RenamerController {

    // === Componentes FXML ===
    @FXML private lateinit var csvSection: VBox
    @FXML private lateinit var buscarCarpeta: Label
    @FXML private lateinit var btnGenerarCSV: Button
    @FXML private lateinit var cargarCSV: Label
    @FXML private lateinit var btnCargarCSV: Button
    @FXML private lateinit var btnRenamer: Button
    @FXML private lateinit var btnCrearEstructura: Button
    @FXML private lateinit var choiceColumnOldName: ChoiceBox<String>
    @FXML private lateinit var choiceColumnNewName: ChoiceBox<String>
    @FXML private lateinit var chkRenombrarCarpetas: CheckBox
    @FXML private lateinit var chkRenombrarArchivos: CheckBox
    @FXML private lateinit var chkUsarEstructura: CheckBox
    @FXML private lateinit var rbEstructuraDefault: RadioButton
    @FXML private lateinit var rbEstructuraCustom: RadioButton
    @FXML private lateinit var btnCargarJson: Button
    @FXML private lateinit var lblJsonEstructura: Label

    // === Variables de estado ===
    private var selectedDirectory: File? = null
    private var selectedCsvFile: File? = null
    private var selectedStructureJson: File? = null
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

    // Formato para timestamp en logs
    private val logFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @FXML
    private fun initialize() {
        // Configuración inicial
        btnCargarCSV.isDisable = true
        choiceColumnNewName.isDisable = true
        choiceColumnOldName.isDisable = true
        btnGenerarCSV.isDisable = true
        btnRenamer.isDisable = false
        chkRenombrarArchivos.isSelected = false
        chkRenombrarCarpetas.isSelected = true

        // Configurar ToggleGroup para los RadioButtons
        val estructuraToggle = ToggleGroup()
        rbEstructuraDefault.toggleGroup = estructuraToggle
        rbEstructuraCustom.toggleGroup = estructuraToggle
        rbEstructuraDefault.isSelected = true

        // Configurar visibilidad de la sección JSON
        estructuraToggle.selectedToggleProperty().addListener { _, _, newToggle ->
            btnCargarJson.isDisable = newToggle != rbEstructuraCustom
        }

        // Listeners para actualizar estado de UI
        val checkListener = { _: ObservableValue<out Boolean>, _: Boolean, _: Boolean ->
            updateUIState()
        }
        chkRenombrarCarpetas.selectedProperty().addListener(checkListener)
        chkRenombrarArchivos.selectedProperty().addListener(checkListener)
    }

    private fun updateUIState() {
        val soloArchivos = chkRenombrarArchivos.isSelected && !chkRenombrarCarpetas.isSelected

        // Ocultar/mostrar sección CSV cuando solo se renombran archivos
        csvSection.isVisible = !soloArchivos
        csvSection.isManaged = !soloArchivos

        // Habilitar/deshabilitar botones según estado
        btnGenerarCSV.isDisable = selectedDirectory == null
        btnCargarCSV.isDisable = selectedDirectory == null || soloArchivos

        // Botón ejecutar siempre activo
        btnRenamer.isDisable = false
    }

    @FXML
    private fun buscarCarpetaRaizClick() {
        val stage = btnRenamer.scene.window as Stage
        val directoryChooser = DirectoryChooser().apply {
            title = "Seleccionar carpeta raíz"
        }

        selectedDirectory = directoryChooser.showDialog(stage)
        buscarCarpeta.text = selectedDirectory?.absolutePath ?: "No se ha seleccionado carpeta"

        updateUIState()
        escribeLog("Carpeta seleccionada: ${selectedDirectory?.absolutePath ?: "ninguna"}")
    }

    @FXML
    private fun cargarEstructuraJsonClick() {
        val stage = btnRenamer.scene.window as Stage
        val chooser = FileChooser().apply {
            title = "Seleccionar estructura JSON"
            extensionFilters.add(FileChooser.ExtensionFilter("JSON", "*.json"))
        }

        selectedStructureJson = chooser.showOpenDialog(stage)
        lblJsonEstructura.text = selectedStructureJson?.name ?: "No se ha seleccionado archivo"

        if (selectedStructureJson != null) {
            escribeLog("JSON de estructura cargado: ${selectedStructureJson!!.name}")
        }
    }

    @FXML
    private fun crearEstructuraClick() {
        val dir = selectedDirectory ?: run {
            mostrarAlerta("Advertencia", "Seleccione la carpeta raíz primero.")
            return
        }

        crearEstructura(dir)
        mostrarAlerta("Éxito", "Proceso de creación de estructura finalizado.")
    }

    @FXML
    private fun generarCSVConSubcarpetasClick() {
        if (selectedDirectory == null) {
            mostrarAlerta("Error", "Seleccione una carpeta raíz primero.", Alert.AlertType.ERROR)
            return
        }

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val csvFile = File(selectedDirectory, "subcarpetas_${timestamp}.csv")

        try {
            csvFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write("nombre_actual,nuevo_nombre\n")
                selectedDirectory!!
                    .listFiles { f -> f.isDirectory }
                    ?.forEach { folder ->
                        writer.write("${folder.name},\n")
                    }
            }

            val mensaje = "CSV generado exitosamente en: ${csvFile.absolutePath}"
            mostrarAlerta("Éxito", mensaje)
            escribeLog("GENERAR CSV: $mensaje")
            escribeLog("Total de carpetas listadas: ${selectedDirectory!!.listFiles { f -> f.isDirectory }?.size ?: 0}")
        } catch (e: Exception) {
            val errorMsg = "No se puede generar el CSV: ${e.message}"
            mostrarAlerta("Error", errorMsg, Alert.AlertType.ERROR)
            escribeLog("ERROR generando CSV: $errorMsg")
        }
    }

    @FXML
    private fun cargarArchivoCSVClick() {
        val stage = btnRenamer.scene.window as Stage
        val fileChooser = FileChooser().apply {
            title = "Seleccionar Archivo CSV"
            extensionFilters.add(FileChooser.ExtensionFilter("Archivos CSV", "*.csv"))
        }

        selectedCsvFile = fileChooser.showOpenDialog(stage)
        cargarCSV.text = selectedCsvFile?.name ?: "No se ha cargado CSV"

        leerColumnasCSV()
        updateUIState()

        if (selectedCsvFile != null) {
            escribeLog("CSV cargado: ${selectedCsvFile!!.name}")
        }
    }

    private fun leerColumnasCSV() {
        val (header, _) = leerEncabezadoYSeparador(selectedCsvFile) ?: run {
            mostrarAlerta("Error", "Error al leer el archivo CSV", Alert.AlertType.ERROR)
            return
        }

        choiceColumnOldName.items.setAll(header)
        choiceColumnNewName.items.setAll(header)
        choiceColumnOldName.isDisable = false
        choiceColumnNewName.isDisable = false

        escribeLog("Columnas CSV cargadas: ${header.size} columnas")
    }

    private fun leerEncabezadoYSeparador(archivo: File?): Pair<List<String>, String>? {
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
            mostrarAlerta("Advertencia", "Seleccione la carpeta primero.")
            return
        }

        if (!chkRenombrarCarpetas.isSelected && !chkRenombrarArchivos.isSelected) {
            mostrarAlerta("Advertencia", "Seleccione al menos una opción de renombrado")
            return
        }

        escribeLog("=== INICIO PROCESO DE RENOMBRADO ===")
        escribeLog("Carpeta raíz: ${dir.absolutePath}")
        escribeLog("Renombrar carpetas: ${chkRenombrarCarpetas.isSelected}")
        escribeLog("Renombrar archivos: ${chkRenombrarArchivos.isSelected}")

        val resultadoTotal = ResultadoRenombrado()

        if (chkRenombrarCarpetas.isSelected) {
            val oldNameColumn = choiceColumnOldName.selectionModel.selectedItem
            val newNameColumn = choiceColumnNewName.selectionModel.selectedItem

            if (oldNameColumn == null || newNameColumn == null) {
                mostrarAlerta("Advertencia", "Seleccione las columnas para el renombrado de carpetas")
                return
            }

            escribeLog("Columnas seleccionadas: '$oldNameColumn' -> '$newNameColumn'")
            cargarMapeoDeCSV(oldNameColumn, newNameColumn)
            escribeLog("Total de mapeos cargados: ${nameMappings.size}")

            val resultadoCarpetas = recorrerYRenombrarCarpetas(dir)

            // Consolidar resultados
            resultadoTotal.exitos.addAll(resultadoCarpetas.exitos)
            resultadoTotal.errores.addAll(resultadoCarpetas.errores)
            resultadoTotal.omitidos.addAll(resultadoCarpetas.omitidos)
            resultadoTotal.archivosExistentes.addAll(resultadoCarpetas.archivosExistentes)
        }

        if (chkRenombrarArchivos.isSelected) {
            val resultadoArchivos = recorrerYRenombrarArchivos(dir)

            // Consolidar resultados
            resultadoTotal.exitos.addAll(resultadoArchivos.exitos)
            resultadoTotal.errores.addAll(resultadoArchivos.errores)
            resultadoTotal.omitidos.addAll(resultadoArchivos.omitidos)
            resultadoTotal.archivosLargos.addAll(resultadoArchivos.archivosLargos)
            resultadoTotal.archivosExistentes.addAll(resultadoArchivos.archivosExistentes)
            resultadoTotal.sinSufijo.addAll(resultadoArchivos.sinSufijo)
        }

        generarResumenYLog(resultadoTotal)
        mostrarResumen(resultadoTotal)
    }

    private fun cargarMapeoDeCSV(oldNameColumn: String, newNameColumn: String) {
        val (header, sep) = leerEncabezadoYSeparador(selectedCsvFile) ?: return

        val indexOld = header.indexOf(oldNameColumn)
        val indexNew = header.indexOf(newNameColumn)

        if (indexOld == -1 || indexNew == -1) {
            escribeLog("ERROR: Columnas no encontradas en CSV")
            return
        }

        nameMappings.clear()
        var lineCount = 0

        selectedCsvFile!!.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val valores = line.split(sep).map { it.trim() }
                lineCount++

                if (valores.size > maxOf(indexOld, indexNew)) {
                    nameMappings[valores[indexOld]] = valores[indexNew]
                } else {
                    escribeLog("ADVERTENCIA: Línea $lineCount tiene menos columnas de las esperadas")
                }
            }
        }

        escribeLog("Total de líneas procesadas en CSV: $lineCount")
    }

    private fun recorrerYRenombrarCarpetas(directory: File): ResultadoRenombrado {
        val resultado = ResultadoRenombrado()

        escribeLog("Iniciando renombrado de carpetas...")
        escribeLog("Total de carpetas a procesar: ${nameMappings.size}")

        nameMappings.toList().sortedByDescending { it.first.length }.forEachIndexed { index, (oldName, newName) ->
            val src = File(directory, oldName)
            val dst = File(directory, newName)

            when {
                !src.exists() -> {
                    resultado.errores.add("No existe: $oldName")
                    escribeLog("ERROR [${index + 1}]: Carpeta no existe - $oldName")
                }

                dst.exists() -> {
                    resultado.archivosExistentes.add(newName)
                    escribeLog("OMITIDO [${index + 1}]: Ya existe - $newName")
                }

                !validarNombre(newName) -> {
                    resultado.errores.add("Nombre inválido: $newName")
                    escribeLog("ERROR [${index + 1}]: Nombre inválido - $newName")
                }

                src.renameTo(dst) -> {
                    resultado.exitos.add("$oldName -> $newName")
                    escribeLog("ÉXITO [${index + 1}]: Carpeta renombrada - $oldName -> $newName")

                    // Renombrar archivos dentro de la carpeta con el nombre de la carpeta padre (segundo nivel)
                    if (chkRenombrarArchivos.isSelected) {
                        renombrarArchivosEnCarpeta(dst, newName, resultado)
                    }
                }

                else -> {
                    resultado.errores.add("Error al renombrar: $oldName")
                    escribeLog("ERROR [${index + 1}]: Error al renombrar - $oldName")
                }
            }
        }

        return resultado
    }

    private fun renombrarArchivosEnCarpeta(carpeta: File, nombreCarpetaPadre: String, resultado: ResultadoRenombrado) {
        escribeLog("  Renombrando archivos en carpeta: ${carpeta.name}")

        carpeta.walkTopDown().filter { it.isFile }.forEach { archivo ->
            val suffix = encontrarSufijo(archivo.nameWithoutExtension)

            if (suffix != null) {
                val extension = if (archivo.extension.isNotEmpty()) ".${archivo.extension}" else ""
                val newFileName = "$nombreCarpetaPadre$suffix$extension"
                val newFile = File(archivo.parent, newFileName)

                when {
                    newFile.exists() -> {
                        resultado.archivosExistentes.add(newFileName)
                        escribeLog("    OMITIDO: Archivo ya existe - $newFileName")
                    }

                    newFileName.length > 255 -> {
                        resultado.archivosLargos.add(newFileName)
                        escribeLog("    ERROR: Nombre demasiado largo - $newFileName")
                    }

                    else -> {
                        try {
                            Files.move(archivo.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                            resultado.exitos.add("${archivo.name} -> $newFileName")
                            escribeLog("    ÉXITO: ${archivo.name} -> $newFileName")
                        } catch (e: IOException) {
                            resultado.errores.add("${archivo.name}: ${e.message}")
                            escribeLog("    ERROR: ${archivo.name} - ${e.message}")
                        }
                    }
                }
            } else {
                resultado.sinSufijo.add(archivo.name)
                escribeLog("    SIN SUFIJO: ${archivo.name} - No se reconoce sufijo")
            }
        }
    }

    private fun encontrarSufijo(nombreArchivo: String): String? {
        return fileSuffixes.firstOrNull { suffix ->
            nombreArchivo.endsWith(suffix)
        }
    }

    private fun recorrerYRenombrarArchivos(raiz: File): ResultadoRenombrado {
        val resultado = ResultadoRenombrado()

        escribeLog("Iniciando renombrado de archivos...")
        escribeLog("Estructura buscada: [raíz]/[carpeta_proyecto]/[subcarpeta]/archivo")
        escribeLog("Se usará el nombre de la carpeta_proyecto (segundo nivel)")

        raiz.listFiles()?.filter { it.isDirectory }?.forEach { carpetaProyecto ->
            escribeLog("Procesando carpeta proyecto: ${carpetaProyecto.name}")

            carpetaProyecto.walkTopDown()
                .filter { it.isFile }
                .forEach { archivo ->
                    val suffix = encontrarSufijo(archivo.nameWithoutExtension)

                    if (suffix != null) {
                        val extension = if (archivo.extension.isNotEmpty()) ".${archivo.extension}" else ""
                        val newFileName = "${carpetaProyecto.name}$suffix$extension"
                        val newFile = File(archivo.parent, newFileName)

                        when {
                            newFile.exists() -> {
                                resultado.archivosExistentes.add(newFileName)
                                escribeLog("  OMITIDO: Ya existe - ${archivo.name} -> $newFileName")
                            }

                            newFileName.length > 255 -> {
                                resultado.archivosLargos.add(newFileName)
                                escribeLog("  ERROR: Nombre demasiado largo - $newFileName")
                            }

                            else -> {
                                try {
                                    Files.move(archivo.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                                    resultado.exitos.add("${archivo.name} -> $newFileName")
                                    escribeLog("  ÉXITO: ${archivo.name} -> $newFileName")
                                } catch (e: IOException) {
                                    resultado.errores.add("${archivo.name}: ${e.message}")
                                    escribeLog("  ERROR: ${archivo.name} - ${e.message}")
                                }
                            }
                        }
                    } else {
                        resultado.sinSufijo.add(archivo.name)
                        escribeLog("  SIN SUFIJO: ${archivo.name} - No se reconoce sufijo")
                    }
                }
        }

        return resultado
    }

    private fun generarResumenYLog(resultado: ResultadoRenombrado) {
        val resumen = buildString {
            appendLine("\n=== RESUMEN DE RENOMBRADO ===")
            appendLine("Fecha: ${LocalDateTime.now().format(logFormatter)}")
            appendLine("Éxitos: ${resultado.exitos.size}")
            appendLine("Errores: ${resultado.errores.size}")
            appendLine("Omitidos: ${resultado.omitidos.size}")
            appendLine("Archivos largos: ${resultado.archivosLargos.size}")
            appendLine("Archivos existentes: ${resultado.archivosExistentes.size}")
            appendLine("Archivos sin sufijo reconocido: ${resultado.sinSufijo.size}")
            appendLine("------------------------------")

            if (resultado.exitos.isNotEmpty()) {
                appendLine("\nÉXITOS (primeros 10):")
                resultado.exitos.take(10).forEach { appendLine("  ✓ $it") }
                if (resultado.exitos.size > 10) {
                    appendLine("  ... y ${resultado.exitos.size - 10} más")
                }
            }

            if (resultado.errores.isNotEmpty()) {
                appendLine("\nERRORES (primeros 5):")
                resultado.errores.take(5).forEach { appendLine("  ✗ $it") }
                if (resultado.errores.size > 5) {
                    appendLine("  ... y ${resultado.errores.size - 5} más")
                }
            }

            if (resultado.sinSufijo.isNotEmpty()) {
                appendLine("\nARCHIVOS SIN SUFIJO RECONOCIDO (primeros 5):")
                resultado.sinSufijo.take(5).forEach { appendLine("  ? $it") }
                if (resultado.sinSufijo.size > 5) {
                    appendLine("  ... y ${resultado.sinSufijo.size - 5} más")
                }
            }
        }

        println(resumen)
        escribeLog(resumen)
    }

    private fun mostrarResumen(resultado: ResultadoRenombrado) {
        val mensaje = buildString {
            appendLine("✅ PROCESO COMPLETADO")
            appendLine("=======================")
            appendLine("Éxitos: ${resultado.exitos.size}")
            appendLine("Errores: ${resultado.errores.size}")
            appendLine("Omitidos: ${resultado.omitidos.size + resultado.archivosExistentes.size}")

            if (resultado.errores.isNotEmpty()) {
                appendLine("\n⚠ Se encontraron errores. Ver log para detalles.")
            }

            if (resultado.sinSufijo.isNotEmpty()) {
                appendLine("\nℹ ${resultado.sinSufijo.size} archivos no tenían sufijo reconocido")
            }

            appendLine("\n📄 Log guardado en: ${selectedDirectory?.absolutePath}/Renombrador.log")
        }

        mostrarAlerta("Proceso Completado", mensaje, Alert.AlertType.INFORMATION)
    }

    private fun crearEstructura(dir: File) {
        // Implementación básica - puedes expandir esto según tus necesidades
        escribeLog("Creando estructura de carpetas en: ${dir.absolutePath}")

        val estructura = listOf("documentos", "imagenes", "archivos", "backup")
        estructura.forEach { carpeta ->
            val nuevaCarpeta = File(dir, carpeta)
            if (nuevaCarpeta.mkdir()) {
                escribeLog("  ✓ Carpeta creada: $carpeta")
            } else {
                escribeLog("  ℹ Carpeta ya existe: $carpeta")
            }
        }
    }

    private fun mostrarAlerta(titulo: String, mensaje: String, tipo: Alert.AlertType = Alert.AlertType.INFORMATION) {
        Alert(tipo).apply {
            title = titulo
            contentText = mensaje
            showAndWait()
        }
        escribeLog("ALERTA [$tipo]: $titulo - $mensaje")
    }

    private fun validarNombre(nombre: String): Boolean {
        val caracteresProhibidos = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        return !nombre.any { it in caracteresProhibidos }
    }

    private fun escribeLog(mensaje: String) {
        selectedDirectory?.let { dir ->
            try {
                val logFile = File(dir, "Renombrador.log")
                val timestamp = LocalDateTime.now().format(logFormatter)
                logFile.appendText("[$timestamp] $mensaje\n")
            } catch (e: Exception) {
                println("Error escribiendo en log: ${e.message}")
            }
        }
    }
}