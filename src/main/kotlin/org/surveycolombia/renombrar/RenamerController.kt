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
 * Data class para contener los resultados del proceso de renombrado
 */

data class ResultadoRenombrado(
    val exitos: MutableList<String> = mutableListOf(),
    val errores: MutableList<String> = mutableListOf(),
    val omitidos: MutableList<String> = mutableListOf(),
    val archivosLargos: MutableList<String> = mutableListOf(),
    val archivosExistentes: MutableList<String> = mutableListOf()
)

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
        //val soloCarpetas = !chkRenombrarArchivos.isSelected && chkRenombrarCarpetas.isSelected

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
     * @fn leerColumnasCSV
     * @brief Lee la primera línea del archivo CSV seleccionado para extraer los nombres de columnas.
     *
     * @details
     * - Detecta el separador utilizado (coma, punto y coma o tabulación).
     * - Llena los ChoiceBox `choiceColumnOldName` y `choiceColumnNewName` con los nombres de las columnas.
     * - Habilita los ChoiceBox y el botón de renombrado si el archivo es válido.
     *
     * @warning Muestra una alerta si el archivo no es válido o tiene menos de dos columnas.
     */

    private fun leerColumnasCSV() {
        val (header, _) = leerEncabezadoYSeparador(selectedCsvFile) ?: run {
            mostrarAlerta("Error", "Error al leer el archivo CSV")
            return
        }

        if (header.size < 2) {
            mostrarAlerta("Error", "El CSV debe tener al menos 2 columnas")
            return
        }

        choiceColumnOldName.items.setAll(header)
        choiceColumnNewName.items.setAll(header)
        choiceColumnNewName.isDisable = false
        choiceColumnOldName.isDisable = false
        btnRenamer.isDisable = false
    }

    /**
     * @fn leerEncabezadoYSeparador
     * @brief Lee la primera línea del archivo CSV para extraer los encabezados y detectar el separador.
     *
     * @param archivo Archivo CSV a procesar.
     * @return Par con una lista de encabezados y el separador detectado, o `null` si falla.
     *
     * @details
     * - Usa UTF-8 para la lectura del archivo.
     * - Utiliza la función `detectSeparator` para determinar si se usa coma, punto y coma o tabulador.
     */


    private fun leerEncabezadoYSeparador(archivo: File?): Pair<List<String>, String>? {
        if (archivo == null) return null
        return try {
            BufferedReader(InputStreamReader(FileInputStream(archivo), Charsets.UTF_8)).use { reader ->
                val firstLine = reader.readLine() ?: return null
                val separator = detectSeparator(firstLine)
                Pair(firstLine.split(separator), separator)
            }
        } catch (e: Exception) {
            null
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
    private  fun cargarMapeoDeCSV(oldNameColumn: String, newNameColumn: String) {
        val (header, separator) = leerEncabezadoYSeparador(selectedCsvFile) ?: return
        val indexOld = header.indexOf(oldNameColumn)
        val indexNew = header.indexOf(newNameColumn)
        if (indexOld == -1 || indexNew == -1) return

        nameMappings.clear()
        try {
            BufferedReader(InputStreamReader(FileInputStream(selectedCsvFile!!), Charsets.UTF_8)).use { reader ->
                reader.readLine() // Saltar encabezado
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

        val raizNombre = selectedDirectory?.name
        if (nameMappings.any{it.key == raizNombre || it.value == raizNombre}){
            mostrarAlerta("Advertencia", "El CSV contiene referencias al nombre de la carpeta raiz '$raizNombre' .\nEvita renombrala pra prevenir errores.")
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
        val resultado = ResultadoRenombrado(
            exitos = mutableListOf(),
            errores = mutableListOf(),
            omitidos = mutableListOf()
        )

        val raizNombre = directory.name //Nombre actual de la carpeta raiz

        nameMappings.forEach { (oldName, newName) ->
            val folder = File(directory, oldName)
            val target = File(folder.parent, newName)

            if(target.canonicalFile == directory.canonicalFile){
                resultado.omitidos.add("Omitido por seguridad: intento de sobrescribir la carpeta raíz con '$newName'")
                return@forEach
            }

            when {
                newName.isBlank() -> resultado.omitidos.add("Sin nombre nuevo: '$oldName' (omitido)")
                !validarNombre(newName) -> resultado.errores.add("Nombre inválido: '$newName'")
                else -> {
                    val folder = File(directory, oldName)
                    when {
                        !folder.exists() || !folder.isDirectory ->
                            resultado.errores.add("Carpeta no encontrada: '$oldName'")
                        File(folder.parent, newName).exists() ->
                            resultado.errores.add("Ya existe: '$newName'")
                        folder.renameTo(File(folder.parent, newName)) ->
                            resultado.exitos.add("${folder.name} -> $newName")
                        else ->
                            resultado.errores.add("Error renombrando: '${folder.name}'")
                    }
                }
            }
        }

        generarResumenYLog(resultado, "RESUMEN DE RENOMBRADO DE CARPETAS")
    }

    /**
     * @fn generarResumenYLog
     * @brief Genera un resumen del proceso de renombrado y lo muestra en consola y en pantalla.
     *
     * @param resultado Objeto ResultadoRenombrado que contiene listas de éxitos, errores, omitidos, etc.
     * @param titulo Título descriptivo para identificar el tipo de resumen (archivos o carpetas).
     *
     * @details
     * - Imprime el resumen completo en consola.
     * - Guarda el resumen en un archivo de log en la carpeta seleccionada.
     * - Muestra un resumen resumido en una alerta gráfica.
     */

    private fun generarResumenYLog(resultado: ResultadoRenombrado, titulo: String) {
        val resumen = buildString {
            appendLine("\n=== $titulo ===")
            with(resultado) {
                listOf(
                    "✅ Éxitos" to exitos,
                    "❌ Errores" to errores,
                    "⚠ Omitidos" to omitidos,
                    "📏 Archivos largos" to archivosLargos,
                    "🔄 Archivos existentes" to archivosExistentes
                ).forEach { (tituloSeccion, lista) ->
                    if (lista.isNotEmpty()) {
                        appendLine("\n$tituloSeccion (${lista.size}):")
                        lista.forEach { appendLine(it) }
                    }
                }
            }
        }

        println(resumen)
        escribeLog(resumen)

        val mensajeAlerta = buildString {
            with(resultado) {
                append("RESUMEN ($titulo):\n")
                if (exitos.isNotEmpty()) append("✅ ${exitos.size} exitos\n")
                if (errores.isNotEmpty()) append("❌ ${errores.size} errores\n")
                if (omitidos.isNotEmpty()) append("⚠ ${omitidos.size} omitidos\n")
                if (archivosLargos.isNotEmpty()) append("📏 ${archivosLargos.size} archivos largos\n")
                if (archivosExistentes.isNotEmpty()) append("🔄 ${archivosExistentes.size} archivos existentes\n")
            }
            append("\nVer consola para detalles completos.")
        }

        // Mostrar alerta solo si hay contenido
        if (mensajeAlerta.isNotBlank()) {
            mostrarAlerta("Resumen del proceso", mensajeAlerta)
        }
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
        val resultado = ResultadoRenombrado(
            exitos = mutableListOf(),
            errores = mutableListOf(),
            omitidos = mutableListOf(),
            archivosLargos = mutableListOf(),
            archivosExistentes = mutableListOf()
        )

        raiz.listFiles()?.filter { it.isDirectory }?.forEach { carpetaProyecto ->
            carpetaProyecto.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    fileSuffixes.firstOrNull { suffix ->
                        file.nameWithoutExtension.endsWith(suffix)
                    }?.let { suffix ->
                        val extension = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
                        val newFileName = "${carpetaProyecto.name}$suffix$extension"

                        when {
                            newFileName.length > 255 ->
                                resultado.archivosLargos.add("Nombre largo: '${file.name}'")
                            newFileName.isBlank() ->
                                resultado.omitidos.add("Sin nombre nuevo: '${file.name}'")
                            else -> {
                                val newFile = File(file.parent, newFileName)
                                when {
                                    newFile.exists() ->
                                        resultado.archivosExistentes.add("Ya existe: '$newFileName'")
                                    else -> try {
                                        Files.move(file.toPath(), newFile.toPath())
                                        resultado.exitos.add("${file.name} -> $newFileName")
                                    } catch (e: IOException) {
                                        resultado.errores.add("Error: '${file.name}'")
                                    }
                                }
                            }
                        }
                    }
                }
        }

        generarResumenYLog(resultado, "RESUMEN DE RENOMBRADO DE ARCHIVOS")
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

    private fun validarNombre(nombre: String): Boolean {
        val caracteresProhibidos = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        return !nombre.any { it in caracteresProhibidos }
    }

    /**
     * @fn escribirLog
     * @brief Escribe un mensaje en el archivo de log en la carpeta raíz seleccionada.
     *
     * @param mensaje El mensaje a escribir en el log.
     */

    private fun escribeLog(mensaje: String) {
        try {
            selectedDirectory?.let {
                val logFile = File(it, "Renombrador.log")
                val timestamp = java.time.LocalDateTime.now()
                val encabezado = """
                    |${"=".repeat(80)}
                    |🕒 EJECUCIÓN DEL RENOMBRADOR: $timestamp
                    |${"=".repeat(80)}
                """.trimMargin()
                logFile.appendText("$encabezado\n$mensaje\n")
            }
        } catch (e: Exception) {
            println("Error escribiendo log: ${e.message}")
        }
    }

}