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

/**
 * @file RenamerController.kt
 * @brief Controlador principal para la interfaz de renombrado de archivos/carpetas.
 *
 * @author David Andres Gonzalez Gutierrez
 * @copyright Survey Colombia 2025 (Licencia GNU GPL v3)
 * @version 1.0
 */

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

/**
 * @class RenamerController
 * @brief Controlador FXML para la operación de renombrado masivo.
 *
 * Maneja:
 * - Selección de directorios raíz
 * - Generación y carga de archivos CSV
 * - Renombrado de carpetas (basado en CSV) y archivos (basado en sufijos)
 */

class RenamerController {
    // === Componentes FXML ===
    @FXML private lateinit var  csvSection: VBox
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


    /**
     * @var fileSuffixes
     * @brief Lista de sufijos para renombrado automático de archivos.
     *
     * Ordenados por prioridad (de más largo a más corto).
     * Formatos:
     * - Compuestos: `_LETRA_SUFIJO` (ej: `_A_FAC`)
     * - Simples: `_SUFIJO` (ej: `_AD`)
     */
    private val fileSuffixes by lazy {
        val baseSuffixes = listOf(
            "_AD", "_EP", "_SJ", "_DP", "_DI", "_FAC", "_ACA", "_EST", "_COC", "_BAN", "_SIN_BAN",
            "_SIN_COC", "_CER", "_LOTE", "_CROQUIS", "_ANEXO", "_PRI", "_OTRO", "_NC"
        )
        val letterSuffixes = listOf("_FAC", "_ACA", "_EST", "_COC", "_BAN", "_NC", "_SIN_BAN", "_SIN_COC", "_CER")

        // Primero los sufijos compuestos (con letra)
        val compoundSuffixes = ('A'..'Z').flatMap { letter ->
            letterSuffixes.map { suffix -> "_${letter}$suffix" }
        }

        // Luego los sufijos simples (ordenados de más largo a más corto)
        val simpleSuffixes = baseSuffixes.sortedByDescending { it.length }

        compoundSuffixes + simpleSuffixes
    }

    // === Métodos FXML ===


    /**
     * @fn initialize
     * @brief Inicializa el estado de la interfaz gráfica.
     *
     * Configura:
     * - Valores iniciales de CheckBox
     * - Listeners para cambios en selecciones
     */
    @FXML
    private fun initialize() {
        // Configuración inicial
        btnCargarCSV.isDisable = true
        btnRenamer.isDisable = true
        choiceColumnNewName.isDisable = true
        choiceColumnOldName.isDisable = true
        btnGenerarCSV.isDisable = true

        // Configuración inicial de los CheckBox
        chkRenombrarArchivos.isSelected = false
        chkRenombrarCarpetas.isSelected = true

        // Listener para deshabilitar botón si no hay ninguna opción seleccionada
        val checkListener = {_: ObservableValue<out Boolean>, _: Boolean, _: Boolean ->
            updateUIState()
        }

        chkRenombrarCarpetas.selectedProperty().addListener(checkListener)
        chkRenombrarArchivos.selectedProperty().addListener(checkListener)

    }

    /**
     * @fn updateUIState
     * @brief Actualiza el estado de los componentes según las selecciones del usuario.
     */

    private fun updateUIState(){
        val soloArchivos = chkRenombrarArchivos.isSelected && !chkRenombrarCarpetas.isSelected
        val soloCarpetas = !chkRenombrarArchivos.isSelected && chkRenombrarCarpetas.isSelected

        // Mostrar/ocultar sección CSV según sea necesario
        csvSection.isVisible = !soloArchivos
        csvSection.isManaged = !soloArchivos

        // Habilitar botones según el estado
        btnGenerarCSV.isDisable = selectedDirectory == null
        btnCargarCSV.isDisable = selectedDirectory == null || soloArchivos

        btnRenamer.isDisable = when {
            selectedDirectory == null -> true
            soloArchivos -> false
            chkRenombrarCarpetas.isSelected -> selectedCsvFile == null ||
                    choiceColumnOldName.selectionModel.isEmpty ||
                    choiceColumnNewName.selectionModel.isEmpty
            else -> false
        }

    }

    /**
     * @fn buscarCarpetaRaizClick
     * @brief Muestra un diálogo para seleccionar la carpeta raíz.
     */
    @FXML
    private fun buscarCarpetaRaizClick() {
        val directoryChooser = DirectoryChooser().apply { title = "Seleccionar carpeta raiz" }
        selectedDirectory = directoryChooser.showDialog(Stage())
        buscarCarpeta.text = selectedDirectory?.absolutePath?: "No ha seleccionado carpetas"
        updateUIState()
    }

    /**
     * @fn generarCSVConSubcarpetasClick
     * @brief Genera un archivo CSV con los nombres de las subcarpetas del directorio seleccionado.
     *
     * @details Crea un archivo llamado "subcarpetas.csv" en la carpeta raíz seleccionada,
     *          con una columna "nombre_actual" que lista todas las subcarpetas.
     *
     * @throws IOException Si ocurre un error al escribir el archivo CSV.
     */
    @FXML
    private fun generarCSVConSubcarpetasClick() {
        if (selectedDirectory == null) {
            mostrarAlerta("Error", "Seleccione una carpeta raíz primero.")
            return
        }
        val csvFile = File(selectedDirectory, "subcarpetas.csv")
        try {
            BufferedWriter(OutputStreamWriter(FileOutputStream(csvFile), Charsets.UTF_8)).use { writer ->
                writer.write("nombre_actual\n")
                selectedDirectory!!.listFiles { file -> file.isDirectory }?.forEach { folder ->
                    writer.write("${folder.name}\n")
                }
            }
            mostrarAlerta("Éxito", "CSV generado en ${csvFile.absolutePath}")
        } catch (e: Exception) {
            mostrarAlerta("Error", "No se puede generar el CSV.")
        }
    }

    /**
     * @fn cargarArchivoCSVClick
     * @brief Abre un diálogo para seleccionar un archivo CSV y carga sus columnas.
     *
     * @details Permite al usuario seleccionar un archivo CSV y carga sus encabezados
     *          en los ChoiceBox para selección de columnas.
     */
    @FXML
    private fun cargarArchivoCSVClick() {
        val fileChooser = FileChooser().apply {
            title = "Seleccionar Archivo CSV"
            extensionFilters.add(FileChooser.ExtensionFilter("Archivos CSV", "*.csv"))
        }
        selectedCsvFile = fileChooser.showOpenDialog(Stage())
        cargarCSV.text = selectedCsvFile?.name ?: "No se seleccionó ningún archivo"
        leerColumnasCSV()
    }

    /**
     * Lee la primera línea del CSV para obtener los nombres de las columnas y llenar los ChoiceBox.
     */
    private fun leerColumnasCSV() {
        try {
            BufferedReader(InputStreamReader(FileInputStream(selectedCsvFile!!), Charsets.UTF_8)).use { reader ->
                val firstLine = reader.readLine() ?: run {
                    mostrarAlerta("Error", "El archivo CSV está vacio")
                    return
                }
                val separator = detectSeparator(firstLine)
                val header = firstLine.split(separator)

                if (header.size < 2){
                    mostrarAlerta("Error", "El CSV debe tener al menos 2 columnas")
                    return
                }

                choiceColumnOldName.items.setAll(header)
                choiceColumnNewName.items.setAll(header)
                choiceColumnNewName.isDisable = false
                choiceColumnOldName.isDisable = false
                btnRenamer.isDisable = false
            }
        } catch (e: Exception) {
            mostrarAlerta("Error", "No se puede leer el archivo CSV.")
        }
    }

    /**
     * @fn detectSeparator
     * @brief Detecta el separador utilizado en un archivo CSV.
     *
     * @param line Primera línea del archivo CSV para analizar.
     * @return String con el separador detectado (",", ";" o "\t").
     */

    private fun detectSeparator(line: String): String{
        val semicolonCount = line.count { it == ';'}
        val commaCount = line.count {it == ','}
        val tabCount = line.count{it == '\t'}

        return when {
            semicolonCount > commaCount && semicolonCount > tabCount -> ";"
            tabCount > commaCount && tabCount > semicolonCount -> "\t"
            else -> "," // Por defecto
        }
    }

    /**
     * @fn renombrarClic
     * @brief Ejecuta el proceso completo de renombrado según las opciones seleccionadas.
     *
     * @details Realiza validaciones previas y ejecuta:
     *          - Renombrado de carpetas (si está habilitado y hay CSV cargado)
     *          - Renombrado de archivos (si está habilitado)
     */
    @FXML
    private  fun renombrarClic(){
        when {
            selectedDirectory == null -> {
                mostrarAlerta("Advertencia", "Seleccione la carpeta primero.")
                return
            }
            !chkRenombrarCarpetas.isSelected && !chkRenombrarArchivos.isSelected -> {
                mostrarAlerta("Advertencia", "Seleccione al menos una opción de renombrado")
                return
            }
            chkRenombrarCarpetas.isSelected && (selectedCsvFile == null ||
                    choiceColumnOldName.selectionModel.isEmpty ||
                    choiceColumnNewName.selectionModel.isEmpty) -> {
                mostrarAlerta("Advertencia", "Para renombrar carpetas selecciones el CSV y ambas columnas")
                return
            }
        }

        if (chkRenombrarCarpetas.isSelected){
            val oldNameColumn = choiceColumnOldName.selectionModel.selectedItem
            val newNameColumn = choiceColumnNewName.selectionModel.selectedItem
            cargarMapeoDeCSV(oldNameColumn, newNameColumn)
            recorrerYRenombrarCarpetas(selectedDirectory!!)
        }

        if (chkRenombrarArchivos.isSelected){
            recorrerYRenombrarArchivos(selectedDirectory!!)
        }
        mostrarAlerta("Completado", "Proceso de renombrado finalizado")
    }

    /**
     * @fn cargarMapeoDeCSV
     * @brief Carga el mapeo de nombres antiguos a nuevos desde un archivo CSV.
     *
     * @param oldNameColumn Nombre de la columna con nombres actuales.
     * @param newNameColumn Nombre de la columna con nombres nuevos.
     *
     * @throws IOException Si ocurre un error al leer el archivo CSV.
     */
    private fun cargarMapeoDeCSV(oldNameColumn: String, newNameColumn: String) {
        try {
            BufferedReader(InputStreamReader(FileInputStream(selectedCsvFile!!), Charsets.UTF_8)).use { reader ->
                val firstLine = reader.readLine() ?: return
                val separator = detectSeparator(firstLine)
                val header = firstLine.split(separator)
                val indexOld = header.indexOf(oldNameColumn)
                val indexNew = header.indexOf(newNameColumn)
                if (indexOld == -1 || indexNew == -1) return
                nameMappings.clear()
                reader.forEachLine { line ->
                    val values = line.split(separator)
                    if (values.size > indexOld && values.size > indexNew) {
                        nameMappings[values[indexOld]] = values[indexNew]
                    }
                }
            }
        } catch (e: Exception) {
            mostrarAlerta("Error", "No se puede procesar el CSV.")
        }
    }

    /**
     * @fn recorrerYRenombrarCarpetas
     * @brief Renombra carpetas según el mapeo cargado desde CSV.
     *
     * @param directory Directorio raíz donde se buscarán las carpetas a renombrar.
     *
     * @details Genera un reporte detallado en consola con los resultados.
     */
    private fun recorrerYRenombrarCarpetas(directory: File) {
        val errores = mutableListOf<String>()
        val exitos = mutableListOf<String>()

        println("Total de carpetas procesadas: ${nameMappings.size}")

        nameMappings.forEach { (oldName, newName) ->
            if (!validarNombre(newName)){
                errores.add("Nombre inválido: '$newName' - contiene caracteres prohibidos")
                return@forEach
            }
            val folder = File(directory, oldName)
            if (folder.exists() && folder.isDirectory) {
                val newFolder = File(folder.parent, newName)

                if (newFolder.exists()){
                    errores.add("Ya existe una carpeta con el nombre '$newName'")
                    return@forEach
                }

                try {
                    if (folder.renameTo(newFolder)){
                        exitos.add("${folder.name} -> $newName")
                    } else {
                        errores.add("${folder.name} -> $newName")
                    }
                }catch (e: Exception){
                    errores.add("${folder.name} -> $newName")
                }
            } else {
                errores.add("Carpeta no encontrada: '$oldName' (mapeada a '$newName')")
            }
        }

        //Mostrar resumen en consola

        val resumen = StringBuilder()
        resumen.appendLine("\n=== RESUMEN DE RENOMBRADO DE CARPETAS ===")
        resumen.appendLine("\n✅ Carpetas renombradas con éxito (${exitos.size}):")
        exitos.forEach{resumen.appendLine(it)}

        resumen.appendLine("\n❌ Errores (${errores.size}):")
        errores.forEach { resumen.appendLine(it) }

        println(resumen.toString())
        escribeLog(resumen.toString())

        // Mostrar resumen en alerta
        val mensaje = if (errores.isNotEmpty()){
           "Hubo ${errores.size} errores al renombrar carpetas.\nVer consola para detalles."
        }else {
            "Todas las carpetas se renombraron correctamente"
        }

        mostrarAlerta("Resumen", mensaje)
    }

    /**
     * @fn recorrerYRenombrarArchivos
     * @brief Renombra archivos según los sufijos predefinidos.
     *
     * @param raiz Directorio raíz donde se buscarán los archivos.
     *
     * @details Los nuevos nombres siguen el formato:
     *          "[NombreCarpetaPadre][Sufijo].[extensión]"
     */
    private fun recorrerYRenombrarArchivos(raiz: File) {
        val errores = mutableListOf<String>()
        val exitos = mutableListOf<String>()
        val archivosLargos = mutableListOf<String>()
        val archivosExistentes = mutableListOf<String>() // Nueva lista para archivos que ya existen

        println("\n=== INICIANDO RENOMBRADO DE ARCHIVOS ===")

        raiz.listFiles()?.filter { it.isDirectory }?.forEach { carpetaProyecto ->
            carpetaProyecto.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    fileSuffixes.firstOrNull { suffix ->
                        file.nameWithoutExtension.endsWith(suffix)
                    }?.let { suffix ->
                        val extension = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
                        val newFileName = "${carpetaProyecto.name}$suffix$extension"

                        if (newFileName.length > 255) {
                            archivosLargos.add("⚠ Nombre demasiado largo: '${file.name}' -> '$newFileName'")
                            return@forEach
                        }

                        val newFile = File(file.parent, newFileName)

                        try {
                            if (newFile.exists()) {
                                // Archivo ya existe - lo omitimos
                                archivosExistentes.add("↻ Archivo ya existente: '${file.name}' -> '$newFileName' (no se renombró)")
                            } else {
                                Files.move(file.toPath(), newFile.toPath())
                                exitos.add("✓ ${file.name} -> $newFileName")
                            }
                        } catch (e: IOException) {
                            errores.add("✗ Error renombrando '${file.name}': ${e.message}")
                        }
                    }
                }
        }

        // Mostrar resultados en consola
        printResults(exitos, archivosLargos, errores, archivosExistentes) // Función actualizada
    }
    /**
     * @fn printResults
     * @brief Imprime en consola un resumen detallado del renombrado de archivos.
     *
     * @param exitos Lista de archivos renombrados exitosamente.
     * @param archivosLargos Lista de archivos con nombres demasiado largos.
     * @param errores Lista de errores durante el proceso.
     * @param archivosExistentes Lista de archivos que no se renombraron por existir.
     */

    private fun printResults(
        exitos: List<String>,
        archivosLargos: List<String>,
        errores: List<String>,
        archivosExistentes: List<String>
    ) {
        println("\n=== RESULTADOS COMPLETOS DE RENOMBRADO ===")

        if (exitos.isNotEmpty()) {
            println("\n✅ ARCHIVOS RENOMBRADOS CON ÉXITO (${exitos.size}):")
            exitos.forEach { println(it) } // Mostrar TODOS sin límite
        }

        if (archivosExistentes.isNotEmpty()) {
            println("\n↻ ARCHIVOS EXISTENTES (NO RENOMBRADOS) (${archivosExistentes.size}):")
            archivosExistentes.forEach { println(it) } // Mostrar TODOS
        }

        if (archivosLargos.isNotEmpty()) {
            println("\n⚠ ARCHIVOS CON NOMBRES DEMASIADO LARGOS (${archivosLargos.size}):")
            archivosLargos.forEach { println(it) } // Mostrar TODOS
        }

        if (errores.isNotEmpty()) {
            println("\n❌ ERRORES DURANTE EL RENOMBRADO (${errores.size}):")
            errores.forEach { println(it) } // Mostrar TODOS
        }

        // Resumen en alerta (opcional, puede mantenerse igual)
        if (errores.isNotEmpty() || archivosLargos.isNotEmpty() || archivosExistentes.isNotEmpty()) {
            val mensaje = buildString {
                if (exitos.isNotEmpty()) append("✅ ${exitos.size} archivos renombrados\n")
                if (archivosExistentes.isNotEmpty()) append("↻ ${archivosExistentes.size} archivos ya existían\n")
                if (archivosLargos.isNotEmpty()) append("⚠ ${archivosLargos.size} nombres demasiado largos\n")
                if (errores.isNotEmpty()) append("❌ ${errores.size} errores\n")
                append("Ver consola para detalles completos.")
            }

            val resumen = StringBuilder()
            resumen.appendLine("\n=== RESULTADOS COMPLETOS DE RENOMBRADO ===")

            if (exitos.isNotEmpty()){
                resumen.appendLine("\n✅ ARCHIVOS RENOMBRADOS CON ÉXITO (${exitos.size}):")
                exitos.forEach{resumen.appendLine(it)}
            }

            if (archivosExistentes.isNotEmpty()) {
                resumen.appendLine("\n↻ ARCHIVOS EXISTENTES (NO RENOMBRADOS) (${archivosExistentes.size}):")
                archivosExistentes.forEach { resumen.appendLine(it) }
            }

            if (archivosLargos.isNotEmpty()) {
                resumen.appendLine("\n⚠ ARCHIVOS CON NOMBRES DEMASIADO LARGOS (${archivosLargos.size}):")
                archivosLargos.forEach { resumen.appendLine(it) }
            }

            if (errores.isNotEmpty()) {
                resumen.appendLine("\n❌ ERRORES DURANTE EL RENOMBRADO (${errores.size}):")
                errores.forEach { resumen.appendLine(it) }
            }
            println(resumen.toString())
            escribeLog(resumen.toString())
            mostrarAlerta("Resumen Completo", mensaje)
        }
    }

    /**
     * @fn mostrarAlerta
     * @brief Muestra una alerta en pantalla.
     * @param titulo Título de la alerta.
     * @param mensaje Contenido a mostrar.
     */
    private fun mostrarAlerta(titulo: String, mensaje: String) {
        Alert(Alert.AlertType.INFORMATION).apply {
            title = titulo
            contentText = mensaje
            showAndWait()
        }
    }

    /**
     * @fn validarNombre
     * @brief Valida que un nombre no contenga caracteres prohibidos.
     *
     * @param nombre Nombre a validar.
     * @return Boolean true si el nombre es válido, false si contiene caracteres prohibidos.
     *
     * @note Caracteres prohibidos: / \ : * ? " < > |
     */

    private fun validarNombre(nombre: String): Boolean{
        val caracteresProhibidos = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        return !nombre.any { it in caracteresProhibidos}
    }

    /**
     * @fn escribirLog
     * @brief Escribe un mensaje en el archivo de log en la carpeta raíz seleccionada.
     *
     * @param mensaje El mensaje a escribir en el log.
     */

    private  fun escribeLog(mensaje: String){
        try {
            if (selectedDirectory == null) return
            val logFile = File(selectedDirectory, "Renombrador.log")
            val timestamp = java.time.LocalDateTime.now()
            val encabezado = buildString {
                appendLine("=".repeat(80))
                appendLine("🕒 EJECUCIÓN DEL RENOMBRADOR: $timestamp")
                appendLine("=".repeat(80))
            }
            logFile.appendText(encabezado)
            logFile.appendText("$mensaje\n")
        } catch (e: Exception) {
            println("No se pudo escribir en el archivo de log: ${e.message}")
        }
    }


}