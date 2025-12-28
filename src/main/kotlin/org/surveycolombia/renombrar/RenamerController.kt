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
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Stage
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject
import org.surveycolombia.renombrar.services.CreateStructureService

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
    @FXML private lateinit var treeViewEstructura: TreeView<String>
    @FXML private lateinit var jsonSection: VBox
    @FXML private lateinit var titledPaneEstructura: TitledPane

    @FXML lateinit var rbRootSingle: RadioButton
    @FXML lateinit var rbRootMultiple: RadioButton
    @FXML lateinit var rbRootByNumber: RadioButton
    @FXML lateinit var rbRootByCsv: RadioButton
    @FXML lateinit var folderNumberSection: HBox
    @FXML lateinit var folderNameSection: HBox
    @FXML lateinit var txtRootBaseName: TextField
    @FXML lateinit var spRootCount: Spinner<Int>
    @FXML lateinit var rootSourceSection: HBox
    @FXML lateinit var btnLoadRootCsv: Button
    @FXML lateinit var lblRootCsv: Label



    // === Variables de estado ===
    private var selectedDirectory: File? = null
    private var selectedCsvFile: File? = null
    private var selectedStructureJson: File? = null
    private var estructuraJsonData: Any? = null // Puede ser JSONObject o JSONArray
    private var estructuraDefaultJson: Any? = null // JSON por defecto desde resources
    private val nameMappings = mutableMapOf<String, String>()

    private var selectedRootCsvFile: File? = null

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
        // Cargar JSON por defecto desde resources
        cargarJsonPorDefectoDesdeResources()

        // Configuración inicial
        btnCargarCSV.isDisable = true
        btnLoadRootCsv.isDisable = true
        choiceColumnNewName.isDisable = true
        choiceColumnOldName.isDisable = true
        btnGenerarCSV.isDisable = true
        btnRenamer.isDisable = false
        chkRenombrarArchivos.isSelected = false
        chkRenombrarCarpetas.isSelected = true

        // Configurar ToggleGroup para los RadioButtons de estructura
        val estructuraToggle = ToggleGroup()
        rbEstructuraDefault.toggleGroup = estructuraToggle
        rbEstructuraCustom.toggleGroup = estructuraToggle
        rbEstructuraDefault.isSelected = true

        // Configurar TreeView inicial
        treeViewEstructura.root = TreeItem("Estructura de Carpetas")
        treeViewEstructura.isShowRoot = true

        // Configurar listener para el CheckBox principal de estructura
        chkUsarEstructura.selectedProperty().addListener { _, _, isSelected ->
            // Expandir o colapsar el TitledPane según el estado del checkbox
            titledPaneEstructura.isExpanded = isSelected

            if (isSelected) {
                actualizarControlesEstructura(true)
                if (rbEstructuraDefault.isSelected) {
                    cargarEstructuraPorDefecto()
                }
            } else {
                treeViewEstructura.root.children.clear()
                actualizarControlesEstructura(false)
            }
            updateUIState()  // 🔹 ACTUALIZAR UI CUANDO CAMBIA EL CHECKBOX
        }

        // Configurar listeners para RadioButtons
        estructuraToggle.selectedToggleProperty().addListener { _, _, newToggle ->
            if (!chkUsarEstructura.isSelected) return@addListener

            when (newToggle) {
                rbEstructuraDefault -> {
                    jsonSection.isVisible = false
                    jsonSection.isManaged = false
                    btnCargarJson.isDisable = true
                    cargarEstructuraPorDefecto()
                }
                rbEstructuraCustom -> {
                    jsonSection.isVisible = true
                    jsonSection.isManaged = true
                    btnCargarJson.isDisable = false

                    // 🔹 LIMPIAR árbol al cambiar a estructura personalizada
                    treeViewEstructura.root.children.clear()
                }
            }
            updateUIState()  // 🔹 ACTUALIZAR UI CUANDO CAMBIAN LOS RADIOBUTTONS
        }

        // Actualizar controles iniciales
        actualizarControlesEstructura(chkUsarEstructura.isSelected)

        // Listeners para actualizar estado de UI
        val checkListener = { _: ObservableValue<out Boolean>, _: Boolean, _: Boolean ->
            updateUIState()
        }
        chkRenombrarCarpetas.selectedProperty().addListener(checkListener)
        chkRenombrarArchivos.selectedProperty().addListener(checkListener)

        // 🔹 Estado inicial: una sola carpeta
        activarModoUnaCarpeta()

        // 🔹 Spinner 1..100 por defecto
        spRootCount.valueFactory =
            SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1)

        // 🔹 Valor por defecto del nombre base
        txtRootBaseName.text = "carpeta"

        // 🔹 Listeners
        configurarListeners()

        // 🔹 Cargar estructura por defecto al iniciar la app
        if (chkUsarEstructura.isSelected && rbEstructuraDefault.isSelected) {
            cargarEstructuraPorDefecto()
        }

        // 🔹 Actualizar UI después de inicializar todo
        updateUIState()
    }

    private fun cargarJsonPorDefectoDesdeResources() {
        try {
            // Intentar cargar el JSON por defecto desde resources
            val inputStream = javaClass.getResourceAsStream("estructura_por_defecto.json")
            if (inputStream != null) {
                val jsonContent = inputStream.bufferedReader().use { it.readText().trim() }
                // Intentar cargar como JSONArray (formato con corchetes [])
                try {
                    estructuraDefaultJson = JSONArray(jsonContent)
                    escribeLog("JSON por defecto cargado desde resources (Array)")
                } catch (eArray: Exception) {
                    // Si falla, intentar como JSONObject
                    try {
                        estructuraDefaultJson = JSONObject(jsonContent)
                        escribeLog("JSON por defecto cargado desde resources (Object)")
                    } catch (eObject: Exception) {
                        escribeLog("ERROR: No se pudo cargar JSON por defecto desde resources")
                    }
                }
            } else {
                // Si no existe el archivo en resources, crear uno por defecto
                crearJsonPorDefecto()
            }
        } catch (e: Exception) {
            escribeLog("ERROR cargando JSON por defecto: ${e.message}")
            crearJsonPorDefecto()
        }
    }

    private fun crearJsonPorDefecto() {
        // Crear JSON por defecto básico si no existe el archivo
        val jsonPorDefecto = JSONArray()
        val proyecto1 = JSONObject().apply {
            put("name", "01_colin")
        }
        val proyecto2 = JSONObject().apply {
            put("name", "02_doc_sop")
        }
        val proyecto3 = JSONObject().apply {
            put("name", "03_img")
        }

        val proyecto4 = JSONObject().apply {
            put("name", "04_lpp")
            val children = JSONArray()
            val child1 = JSONObject().apply { put("name", "01_report_cal_lpp") }
            val child2 = JSONObject().apply {
                put("name", "02_postproc")
                val subChildren = JSONArray()
                val subChild1 = JSONObject().apply {
                    put("name", "base")
                    val baseChildren = JSONArray()
                    (1..8).forEach { i ->
                        val baseChild = JSONObject().apply {
                            put("name", "$i. ${when(i) {
                                1 -> "CRUDOS RED MAGNA ECO"
                                2 -> "CRUDOS BASE"
                                3 -> "COORD BASE"
                                4 -> "EFEMERIDES"
                                5 -> "POST_XX_XX_XXXX"
                                6 -> "REPORTE_POST"
                                7 -> "TABLAS DE COORDENADAS"
                                8 -> "SHAPE"
                                else -> ""
                            }}")
                        }
                        baseChildren.put(baseChild)
                    }
                    put("children", baseChildren)
                }
                val subChild2 = JSONObject().apply {
                    put("name", "pto_GPS")
                    val ptoChildren = JSONArray()
                    (1..8).forEach { i ->
                        val ptoChild = JSONObject().apply {
                            put("name", "$i. ${when(i) {
                                1 -> "CRUDOS BASE"
                                2 -> "CRUDOS ROVER"
                                3 -> "COORD BASE"
                                4 -> "EFEMERIDES"
                                5 -> "POST_XX_XX_XXXX"
                                6 -> "REPORTE_POST_XX_XX_XXXX"
                                7 -> "TABLAS DE COORDENADAS"
                                8 -> "SHAPE"
                                else -> ""
                            }}")
                        }
                        ptoChildren.put(ptoChild)
                    }
                    put("children", ptoChildren)
                }
                subChildren.put(subChild1)
                subChildren.put(subChild2)
                put("children", subChildren)
            }
            val child3 = JSONObject().apply { put("name", "03_img_vert") }
            val child4 = JSONObject().apply {
                put("name", "04_arch_GNSS")
                val gnssChildren = JSONArray()
                val gnssChild1 = JSONObject().apply { put("name", "base") }
                val gnssChild2 = JSONObject().apply { put("name", "pto_gps") }
                gnssChildren.put(gnssChild1)
                gnssChildren.put(gnssChild2)
                put("children", gnssChildren)
            }
            children.put(child1)
            children.put(child2)
            children.put(child3)
            children.put(child4)
            put("children", children)
        }

        jsonPorDefecto.put(proyecto1)
        jsonPorDefecto.put(proyecto2)
        jsonPorDefecto.put(proyecto3)
        jsonPorDefecto.put(proyecto4)

        estructuraDefaultJson = jsonPorDefecto
        escribeLog("JSON por defecto creado en memoria")
    }

    private fun actualizarControlesEstructura(habilitado: Boolean) {
        // Habilitar/deshabilitar controles relacionados con estructura
        rbEstructuraDefault.isDisable = !habilitado
        rbEstructuraCustom.isDisable = !habilitado
        btnCrearEstructura.isDisable = !habilitado || selectedDirectory == null
        treeViewEstructura.isDisable = !habilitado
        btnCargarJson.isDisable = !habilitado || !rbEstructuraCustom.isSelected
        jsonSection.isVisible = habilitado && rbEstructuraCustom.isSelected
        jsonSection.isManaged = habilitado && rbEstructuraCustom.isSelected

        // Si no está habilitado, limpiar el treeview
        if (!habilitado) {
            treeViewEstructura.root.children.clear()
        }

        // 🔹 ACTUALIZAR UI CUANDO CAMBIAN LOS CONTROLES DE ESTRUCTURA
        updateUIState()
    }

    private fun cargarEstructuraPorDefecto() {
        if (estructuraDefaultJson != null) {
            cargarEstructuraDesdeJson(estructuraDefaultJson!!)
        } else {
            // Estructura de respaldo si no hay JSON
            val estructuraPorDefecto = mapOf(
                "Proyectos" to listOf("Documentos", "Imágenes", "Planos"),
                "Administrativo" to listOf("Contratos", "Facturas", "Presupuestos"),
                "Técnico" to listOf("Estudios", "Informes", "Memorias")
            )
            actualizarTreeView(estructuraPorDefecto)
        }
    }

    private fun actualizarTreeView(estructura: Map<String, List<String>>) {
        val root = TreeItem<String>("Estructura de Carpetas")

        estructura.forEach { (carpetaPrincipal, subcarpetas) ->
            val itemPrincipal = TreeItem<String>("📁 $carpetaPrincipal")
            subcarpetas.forEach { subcarpeta ->
                itemPrincipal.children.add(TreeItem<String>("   └─ $subcarpeta"))
            }
            root.children.add(itemPrincipal)
        }

        treeViewEstructura.root = root
        treeViewEstructura.isShowRoot = true
    }

    private fun updateUIState() {
        val soloArchivos = chkRenombrarArchivos.isSelected && !chkRenombrarCarpetas.isSelected

        // Ocultar/mostrar sección CSV cuando solo se renombran archivos
        csvSection.isVisible = !soloArchivos
        csvSection.isManaged = !soloArchivos

        // Habilitar/deshabilitar botones según estado
        btnGenerarCSV.isDisable = selectedDirectory == null
        btnCargarCSV.isDisable = selectedDirectory == null || soloArchivos

        // Actualizar botón crear estructura (depende de si hay carpeta seleccionada)
        btnCrearEstructura.isDisable =
            !chkUsarEstructura.isSelected || selectedDirectory == null

        // Botón ejecutar siempre activo
        btnRenamer.isDisable = false

        // 🔹 CONDICIÓN CORREGIDA PARA EL BOTÓN DE CARGAR CSV DE ESTRUCTURA
        val usandoCsvParaEstructura =
            chkUsarEstructura.isSelected &&
                    rbRootMultiple.isSelected &&
                    rbRootByCsv.isSelected

        btnLoadRootCsv.isDisable =
            selectedDirectory == null || !usandoCsvParaEstructura

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
        actualizarControlesEstructura(chkUsarEstructura.isSelected)
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
            try {
                val jsonContent = selectedStructureJson!!.readText().trim()

                // Intentar cargar como JSONArray (formato con corchetes [])
                try {
                    estructuraJsonData = JSONArray(jsonContent)
                    cargarEstructuraDesdeJson(estructuraJsonData!!)
                    escribeLog("JSON Array cargado: ${selectedStructureJson!!.name}")
                    mostrarAlerta("Éxito", "Estructura JSON cargada correctamente.")
                } catch (eArray: Exception) {
                    // Si falla, intentar como JSONObject (formato con llaves {})
                    try {
                        estructuraJsonData = JSONObject(jsonContent)
                        cargarEstructuraDesdeJson(estructuraJsonData!!)
                        escribeLog("JSON Object cargado: ${selectedStructureJson!!.name}")
                        mostrarAlerta("Éxito", "Estructura JSON cargada correctamente.")
                    } catch (eObject: Exception) {
                        throw IllegalArgumentException("No es un JSON válido (ni array ni objeto)")
                    }
                }
            } catch (e: Exception) {
                mostrarAlerta("Error", "No se pudo cargar el archivo JSON: ${e.message}", Alert.AlertType.ERROR)
                escribeLog("ERROR cargando JSON: ${e.message}")
            }
        }
    }

    private fun cargarEstructuraDesdeJson(jsonData: Any) {
        val root = TreeItem<String>("Estructura")

        try {
            when (jsonData) {
                is JSONArray -> {
                    // Formato de array JSON: [{...}, {...}]
                    procesarArrayJSON(jsonData, root)
                }
                is JSONObject -> {
                    // Formato de objeto JSON: {"estructura": [...]} o {"carpetas": [...]}
                    if (jsonData.has("estructura")) {
                        val estructura = jsonData.getJSONArray("estructura")
                        procesarArrayJSON(estructura, root)
                    } else if (jsonData.has("carpetas")) {
                        val carpetas = jsonData.getJSONArray("carpetas")
                        procesarArrayJSON(carpetas, root)
                    } else {
                        // Formato alternativo: objeto con propiedades directas
                        val itemTree = TreeItem<String>("📁 Estructura JSON")
                        procesarNodoJSON(jsonData, itemTree)
                        root.children.add(itemTree)
                    }
                }
            }

            treeViewEstructura.root = root
            treeViewEstructura.isShowRoot = true
        } catch (e: Exception) {
            throw IOException("Error al parsear JSON: ${e.message}")
        }
    }

    private fun procesarArrayJSON(jsonArray: JSONArray, parentTreeItem: TreeItem<String>) {
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.get(i)
            if (item is JSONObject) {
                procesarNodoJSON(item, parentTreeItem)
            } else if (item is String) {
                parentTreeItem.children.add(TreeItem<String>("📁 $item"))
            }
        }
    }

    private fun procesarNodoJSON(jsonObject: JSONObject, parentTreeItem: TreeItem<String>) {
        try {
            // Intenta obtener el nombre de diferentes formas posibles
            val nombre = when {
                jsonObject.has("name") -> jsonObject.getString("name")
                jsonObject.has("nombre") -> jsonObject.getString("nombre")
                jsonObject.has("folder") -> jsonObject.getString("folder")
                jsonObject.has("carpeta") -> jsonObject.getString("carpeta")
                else -> "Sin nombre"
            }

            val itemTree = TreeItem<String>("📁 $nombre")

            // Procesar hijos recursivamente
            if (jsonObject.has("children") && jsonObject.get("children") is JSONArray) {
                val children = jsonObject.getJSONArray("children")
                procesarArrayJSON(children, itemTree)
            } else if (jsonObject.has("subcarpetas") && jsonObject.get("subcarpetas") is JSONArray) {
                val subcarpetas = jsonObject.getJSONArray("subcarpetas")
                procesarArrayJSON(subcarpetas, itemTree)
            } else if (jsonObject.has("hijos") && jsonObject.get("hijos") is JSONArray) {
                val hijos = jsonObject.getJSONArray("hijos")
                procesarArrayJSON(hijos, itemTree)
            }

            parentTreeItem.children.add(itemTree)
        } catch (e: Exception) {
            println("Error procesando nodo JSON: ${e.message}")
        }
    }

    @FXML
    private fun crearEstructuraClick() {
        val raiz = selectedDirectory ?: run {
            mostrarAlerta("Advertencia", "Seleccione la carpeta raíz primero.")
            return
        }

        if (!chkUsarEstructura.isSelected) {
            mostrarAlerta("Advertencia", "Active la opción de estructura.")
            return
        }

        try {
            // Verificar si estamos en modo múltiple con CSV
            if (rbRootMultiple.isSelected && rbRootByCsv.isSelected && selectedRootCsvFile == null) {
                mostrarAlerta("Error", "Debe cargar un archivo CSV para la estructura de carpetas.")
                return
            }

            // 🔹 Obtener nombres de carpetas
            val nombres = obtenerNombresCarpetasSegundoNivel()

            // 🔹 Validar nombres vacíos o inválidos
            val nombresValidos = nombres.filter { it.isNotBlank() && validarNombre(it) }
            if (nombresValidos.isEmpty()) {
                mostrarAlerta("Error", "No se encontraron nombres válidos para crear carpetas.")
                return
            }

            // 🔹 Verificar duplicados en la lista y carpetas existentes
            val (nombresUnicos, nombresExistentes) = verificarDuplicadosYExistentes(nombresValidos, raiz)

            if (nombresUnicos.isEmpty()) {
                mostrarAlerta("Error", "Todos los nombres son duplicados o inválidos.")
                return
            }

            // 🔹 Mostrar advertencia si hay carpetas existentes
            if (nombresExistentes.isNotEmpty()) {
                val mensajeExistente = buildString {
                    appendLine("Las siguientes carpetas ya existen en la ubicación seleccionada:")
                    nombresExistentes.take(5).forEach { appendLine("  • $it") }
                    if (nombresExistentes.size > 5) {
                        appendLine("  ... y ${nombresExistentes.size - 5} más")
                    }
                    appendLine("\nEstas carpetas NO serán sobrescritas.")
                }
                mostrarAlerta("Advertencia - Carpetas Existentes", mensajeExistente, Alert.AlertType.WARNING)
            }

            // 🔹 Archivo JSON activo
            val estructuraFile = if (rbEstructuraDefault.isSelected) {
                crearArchivoTemporalEstructuraDefault()
            } else {
                selectedStructureJson ?: run {
                    mostrarAlerta("Error", "No hay estructura JSON definida.")
                    return
                }
            }

            // 🔹 Log de inicio
            escribeLog("=== INICIO CREACIÓN DE ESTRUCTURA ===")
            escribeLog("Carpeta raíz: ${raiz.absolutePath}")
            escribeLog("Modo: ${if (rbRootSingle.isSelected) "Una carpeta" else "Múltiples carpetas"}")
            escribeLog("Nombres a procesar: ${nombresUnicos.size}")
            escribeLog("Carpetas existentes que se omitirán: ${nombresExistentes.size}")

            if (rbRootSingle.isSelected) {
                // ===== UNA SOLA CARPETA =====
                escribeLog("Creando estructura en carpeta raíz...")
                CreateStructureService.create(raiz, estructuraFile)

            } else {
                // ===== VARIAS CARPETAS =====
                var creadas = 0
                var omitidas = 0

                escribeLog("Nombres de carpetas únicos a crear: ${nombresUnicos.joinToString(", ")}")

                nombresUnicos.forEachIndexed { index, nombre ->
                    val carpeta = File(raiz, nombre)
                    escribeLog("Procesando carpeta ${index + 1}/${nombresUnicos.size}: $nombre")

                    // Verificar si ya existe antes de intentar crearla
                    if (carpeta.exists()) {
                        escribeLog("ℹ Carpeta ya existe, omitiendo: $nombre")
                        omitidas++
                    } else if (carpeta.mkdirs()) {
                        escribeLog("✓ Carpeta creada: $nombre")
                        try {
                            CreateStructureService.create(carpeta, estructuraFile)
                            creadas++
                        } catch (e: Exception) {
                            escribeLog("✗ Error creando estructura en carpeta '$nombre': ${e.message}")
                        }
                    } else {
                        escribeLog("✗ No se pudo crear la carpeta: $nombre")
                        throw IOException("No se pudo crear la carpeta: $nombre")
                    }
                }

                // Resumen
                escribeLog("=== RESUMEN CREACIÓN ===")
                escribeLog("Carpetas creadas: $creadas")
                escribeLog("Carpetas omitidas (ya existían): $omitidas")
                escribeLog("Total procesado: ${nombresUnicos.size}")
            }

            mostrarAlerta("Éxito", "Estructura creada correctamente.")
            escribeLog("=== FIN CREACIÓN DE ESTRUCTURA ===")

        } catch (e: Exception) {
            val mensajeError = e.message ?: "Error desconocido al crear estructura"
            mostrarAlerta("Error", mensajeError, Alert.AlertType.ERROR)
            escribeLog("ERROR creando estructura: $mensajeError")
            escribeLog("=== FIN CREACIÓN DE ESTRUCTURA CON ERRORES ===")
        }
    }

    private fun obtenerNombresCarpetasSegundoNivel(): List<String> {
        return when {
            rbRootByNumber.isSelected -> {
                val base = txtRootBaseName.text.trim().ifEmpty { "Carpeta" }
                (1..spRootCount.value).map { "${base}_$it" }
            }

            rbRootByCsv.isSelected -> {
                if (selectedRootCsvFile == null) {
                    throw IllegalStateException("Debe cargar primero el CSV de estructura de carpetas")
                }
                leerNombresDesdeCsv()
            }

            else -> emptyList()
        }
    }

    private fun leerNombresDesdeCsv(): List<String> {
        val archivo = selectedRootCsvFile
            ?: throw IllegalStateException("Debe cargar el CSV de estructura.")

        val nombres = mutableListOf<String>()
        val nombresSet = mutableSetOf<String>()
        val nombresDuplicados = mutableListOf<String>()

        archivo.bufferedReader(Charsets.UTF_8).use { reader ->
            // Leer primera línea para detectar separador
            val primeraLinea = reader.readLine() ?: return emptyList()
            val separador = detectSeparator(primeraLinea)

            // Procesar la primera línea si tiene datos
            if (primeraLinea.isNotEmpty()) {
                try {
                    val columnas = splitCsvLine(primeraLinea, separador).map { it.trim().trim('"', '\'') }
                    val nombre = columnas.firstOrNull()
                    if (!nombre.isNullOrEmpty() && validarNombre(nombre)) {
                        nombres.add(nombre)
                        nombresSet.add(nombre)
                    }
                } catch (e: Exception) {
                    // Intentar método simple como fallback
                    try {
                        val columnas = primeraLinea.split(separador).map { it.trim().trim('"', '\'') }
                        val nombre = columnas.firstOrNull()
                        if (!nombre.isNullOrEmpty() && validarNombre(nombre)) {
                            nombres.add(nombre)
                            nombresSet.add(nombre)
                        }
                    } catch (e2: Exception) {
                        escribeLog("ERROR procesando primera línea del CSV: ${e2.message}")
                    }
                }
            }

            // Procesar el resto de líneas
            var lineaActual: String?
            var lineaNumero = 1
            while (reader.readLine().also { lineaActual = it } != null) {
                lineaNumero++
                val line = lineaActual!!.trim()
                if (line.isEmpty()) {
                    continue
                }

                try {
                    val columnas = splitCsvLine(line, separador).map { it.trim().trim('"', '\'') }
                    val nombre = columnas.firstOrNull()

                    if (!nombre.isNullOrEmpty()) {
                        if (!validarNombre(nombre)) {
                            escribeLog("ADVERTENCIA línea $lineaNumero: Nombre inválido '$nombre' - se omitirá")
                        } else if (nombresSet.contains(nombre)) {
                            nombresDuplicados.add(nombre)
                            escribeLog("ADVERTENCIA: Nombre duplicado en CSV (línea $lineaNumero): '$nombre'")
                        } else {
                            nombres.add(nombre)
                            nombresSet.add(nombre)
                        }
                    }
                } catch (e: Exception) {
                    // Fallback: intentar con split simple
                    try {
                        val columnas = line.split(separador).map { it.trim().trim('"', '\'') }
                        val nombre = columnas.firstOrNull()

                        if (!nombre.isNullOrEmpty() && validarNombre(nombre)) {
                            if (nombresSet.contains(nombre)) {
                                nombresDuplicados.add(nombre)
                                escribeLog("ADVERTENCIA: Nombre duplicado en CSV (línea $lineaNumero): '$nombre'")
                            } else {
                                nombres.add(nombre)
                                nombresSet.add(nombre)
                            }
                        }
                    } catch (e2: Exception) {
                        escribeLog("ERROR procesando línea $lineaNumero del CSV: ${e2.message}")
                    }
                }
            }
        }

        if (nombres.isEmpty()) {
            throw IllegalStateException("El CSV de estructura no contiene nombres válidos.")
        }

        // Si hay duplicados, mostrar alerta
        if (nombresDuplicados.isNotEmpty()) {
            val mensaje = buildString {
                appendLine("Se encontraron nombres duplicados en el CSV:")
                nombresDuplicados.take(10).forEach { appendLine("  • $it") }
                if (nombresDuplicados.size > 10) {
                    appendLine("  ... y ${nombresDuplicados.size - 10} más")
                }
                appendLine("\nSe procesarán únicamente las primeras ocurrencias.")
            }
            mostrarAlerta("Advertencia - Nombres Duplicados", mensaje, Alert.AlertType.WARNING)
        }

        return nombres
    }


    private fun crearArchivoTemporalEstructuraDefault(): File {

        val temp = File.createTempFile("estructura_default", ".json")
        temp.deleteOnExit()

        temp.writeText(
            when (estructuraDefaultJson) {
                is JSONArray -> (estructuraDefaultJson as JSONArray).toString(2)
                is JSONObject -> (estructuraDefaultJson as JSONObject).toString(2)
                else -> throw IllegalStateException("JSON por defecto inválido")
            }
        )

        return temp
    }





    private fun crearEstructuraPorDefecto(directorio: File): Boolean {
        return try {
            if (estructuraDefaultJson != null) {
                crearEstructuraDesdeJson(directorio, estructuraDefaultJson!!)
            } else {
                false
            }
        } catch (e: Exception) {
            mostrarAlerta("Error", "Error al crear estructura: ${e.message}", Alert.AlertType.ERROR)
            false
        }
    }

    private fun crearEstructuraDesdeJson(directorio: File, jsonData: Any): Boolean {
        return try {
            when (jsonData) {
                is JSONArray -> {
                    crearEstructuraDesdeArray(directorio, jsonData)
                }

                is JSONObject -> {
                    // Lógica existente para JSONObject
                    if (jsonData.has("estructura")) {
                        val estructura = jsonData.getJSONArray("estructura")
                        crearEstructuraDesdeArray(directorio, estructura)
                    } else if (jsonData.has("carpetas")) {
                        val carpetas = jsonData.getJSONArray("carpetas")
                        crearEstructuraDesdeArray(directorio, carpetas)
                    } else {
                        // Asumimos que es un nodo con "name" y "children"
                        crearNodoDesdeJSON(directorio, jsonData)
                    }
                }

                else -> {
                    escribeLog("ERROR: Tipo de JSON no soportado")
                    false
                }
            }
        } catch (e: Exception) {
            mostrarAlerta("Error", "Error al crear estructura desde JSON: ${e.message}", Alert.AlertType.ERROR)
            false
        } as Boolean
    }

    private fun crearEstructuraDesdeArray(directorio: File, jsonArray: JSONArray): Boolean {
        try {
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.get(i)
                when (item) {
                    is JSONObject -> {
                        crearNodoDesdeJSON(directorio, item)
                    }
                    is String -> {
                        val dirCarpeta = File(directorio, item)
                        if (dirCarpeta.mkdir()) {
                            escribeLog("✓ Carpeta creada: $item")
                        } else {
                            escribeLog("ℹ Carpeta ya existe: $item")
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            throw IOException("Error al crear estructura desde array: ${e.message}")
        }
    }

    private fun crearNodoDesdeJSON(directorio: File, jsonObject: JSONObject) {
        try {
            // Intenta obtener el nombre de diferentes formas posibles
            val nombreCarpeta = when {
                jsonObject.has("name") -> jsonObject.getString("name")
                jsonObject.has("nombre") -> jsonObject.getString("nombre")
                jsonObject.has("folder") -> jsonObject.getString("folder")
                jsonObject.has("carpeta") -> jsonObject.getString("carpeta")
                else -> "carpeta_${System.currentTimeMillis()}"
            }

            val dirCarpeta = File(directorio, nombreCarpeta)

            if (dirCarpeta.mkdir()) {
                escribeLog("✓ Carpeta creada: $nombreCarpeta")

                // Crear hijos recursivamente
                if (jsonObject.has("children") && jsonObject.get("children") is JSONArray) {
                    val children = jsonObject.getJSONArray("children")
                    crearEstructuraDesdeArray(dirCarpeta, children)
                } else if (jsonObject.has("subcarpetas") && jsonObject.get("subcarpetas") is JSONArray) {
                    val subcarpetas = jsonObject.getJSONArray("subcarpetas")
                    crearEstructuraDesdeArray(dirCarpeta, subcarpetas)
                } else if (jsonObject.has("hijos") && jsonObject.get("hijos") is JSONArray) {
                    val hijos = jsonObject.getJSONArray("hijos")
                    crearEstructuraDesdeArray(dirCarpeta, hijos)
                }
            } else {
                escribeLog("ℹ Carpeta ya existe: $nombreCarpeta")
                // Si la carpeta ya existe, aún así procesar los hijos si los tiene
                if (jsonObject.has("children") && jsonObject.get("children") is JSONArray) {
                    val children = jsonObject.getJSONArray("children")
                    crearEstructuraDesdeArray(dirCarpeta, children)
                }
            }
        } catch (e: Exception) {
            println("Error creando nodo desde JSON: ${e.message}")
        }
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
                val columnas = splitCsvLine(line, sep)
                Pair(columnas.map { h -> h.trim().trim('"', '\'') }, sep)
            }
        } catch (e: Exception) {
            // Intentar fallback con detección simple
            try {
                archivo.bufferedReader(Charsets.UTF_8).use {
                    val line = it.readLine() ?: return null
                    val sep = detectSeparator(line)
                    val columnas = line.split(sep).map { h -> h.trim().trim('"', '\'') }
                    Pair(columnas, sep)
                }
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun detectSeparator(line: String): String {
        // Contar ocurrencias de cada separador
        val semicolon = line.count { it == ';' }
        val comma = line.count { it == ',' }
        val tab = line.count { it == '\t' }

        // Si hay más punto y coma que coma, usar punto y coma
        if (semicolon > comma && semicolon > tab) {
            return ";"
        }
        // Si hay más tabuladores, usar tabulador
        else if (tab > comma && tab > semicolon) {
            return "\t"
        }
        // Si hay igualdad entre punto y coma y coma, verificar contexto
        else if (semicolon == comma && semicolon > 0) {
            // Si hay comas dentro de comillas, usar punto y coma
            if (line.contains("\"")) {
                val hasCommaInQuotes = line.contains("\".*?,".toRegex())
                val hasSemicolonInQuotes = line.contains("\".*?;".toRegex())

                if (hasCommaInQuotes && !hasSemicolonInQuotes) {
                    return ";"
                } else if (hasSemicolonInQuotes && !hasCommaInQuotes) {
                    return ","
                }
            }
            // Por defecto usar punto y coma (más común en algunos formatos)
            return ";"
        }
        // Por defecto usar coma
        else {
            return ","
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
            mostrarAlerta("Error", "Columnas no encontradas en el CSV.\nColumnas disponibles: ${header.joinToString(", ")}")
            escribeLog("ERROR: Columnas no encontradas en CSV. Buscadas: '$oldNameColumn', '$newNameColumn'. Encontradas: ${header.joinToString(", ")}")
            return
        }

        nameMappings.clear()
        var lineCount = 0
        var errorCount = 0

        selectedCsvFile!!.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                lineCount++

                try {
                    // Usar el método robusto para dividir la línea
                    val valores = splitCsvLine(line, sep).map {
                        it.trim().trim('"', '\'')
                    }

                    if (valores.size > maxOf(indexOld, indexNew)) {
                        val oldName = valores[indexOld]
                        val newName = valores[indexNew]

                        // Validar que ambos nombres no estén vacíos
                        if (oldName.isNotBlank() && newName.isNotBlank()) {
                            nameMappings[oldName] = newName
                        } else {
                            escribeLog("ADVERTENCIA: Línea $lineCount tiene nombres vacíos - Antiguo: '$oldName', Nuevo: '$newName'")
                        }
                    } else {
                        escribeLog("ADVERTENCIA: Línea $lineCount tiene menos columnas de las esperadas. Esperadas al menos ${maxOf(indexOld, indexNew) + 1}, encontradas: ${valores.size}")
                        errorCount++
                    }
                } catch (e: Exception) {
                    escribeLog("ERROR en línea $lineCount: ${e.message}")
                    errorCount++
                }
            }
        }

        escribeLog("Total de líneas procesadas en CSV: $lineCount")
        escribeLog("Total de mapeos cargados: ${nameMappings.size}")

        if (errorCount > 0) {
            mostrarAlerta("Advertencia",
                "Se encontraron $errorCount errores al procesar el CSV.\n" +
                        "Verifique el formato del archivo (puede usar coma ',' o punto y coma ';' como separador).\n" +
                        "Total de mapeos exitosos: ${nameMappings.size}")
        }
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
                    escribeLog("ERROR [${index + 1}]: Error al renombrado - $oldName")
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

    private fun mostrarAlerta(titulo: String, mensaje: String, tipo: Alert.AlertType = Alert.AlertType.INFORMATION) {
        Alert(tipo).apply {
            title = titulo
            contentText = mensaje
            showAndWait()
        }
        escribeLog("ALERTA [$tipo]: $titulo - $mensaje")
    }

    private fun validarNombre(nombre: String): Boolean {
        if (nombre.isBlank()) return false

        // Caracteres prohibidos en nombres de archivos/carpetas
        val caracteresProhibidos = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

        // Validar longitud (Windows tiene límite de 260 caracteres para ruta completa)
        if (nombre.length > 255) {
            escribeLog("ADVERTENCIA: Nombre demasiado largo (>255): '$nombre'")
            return false
        }

        // No permitir nombres reservados de Windows
        val nombresReservados = listOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        )

        val nombreUpper = nombre.uppercase()
        if (nombresReservados.contains(nombreUpper)) {
            escribeLog("ADVERTENCIA: Nombre reservado del sistema: '$nombre'")
            return false
        }

        // No permitir que termine con punto o espacio
        if (nombre.endsWith(".") || nombre.endsWith(" ")) {
            escribeLog("ADVERTENCIA: Nombre termina con punto o espacio: '$nombre'")
            return false
        }

        // Validar caracteres individuales
        return !nombre.any { it in caracteresProhibidos }
    }

    private fun configurarListeners() {

        // Modo UNA vs VARIAS carpetas
        rbRootSingle.selectedProperty().addListener { _, _, selected ->
            if (selected) {
                activarModoUnaCarpeta()
            }
            updateUIState()  // 🔹 Asegurar actualización de UI
        }

        rbRootMultiple.selectedProperty().addListener { _, _, selected ->
            if (selected) {
                activarModoVariasCarpetas()
            }
            updateUIState()  // 🔹 Asegurar actualización de UI
        }

        // Origen por NÚMERO
        rbRootByNumber.selectedProperty().addListener { _, _, selected ->
            if (selected) {
                mostrarSeccionNumero()
            }
            updateUIState()  // 🔹 Asegurar actualización de UI
        }

        // Origen por CSV
        rbRootByCsv.selectedProperty().addListener { _, _, selected ->
            if (selected) {
                mostrarSeccionCsv()
            }
            updateUIState()  // 🔹 Asegurar actualización de UI
        }

        // 🔹 Listener para cambios en el spinner y campo de texto
        spRootCount.valueProperty().addListener { _, _, _ -> updateUIState() }
        txtRootBaseName.textProperty().addListener { _, _, _ -> updateUIState() }
    }


    private fun activarModoUnaCarpeta() {

        // 🔹 Ocultar origen (Número / CSV)
        rootSourceSection.isVisible = false
        rootSourceSection.isManaged = false

        // 🔹 Ocultar secciones dependientes
        folderNumberSection.isVisible = false
        folderNumberSection.isManaged = false

        folderNameSection.isVisible = false
        folderNameSection.isManaged = false

        // 🔹 Limpiar archivo CSV seleccionado
        selectedRootCsvFile = null
    }


    private fun activarModoVariasCarpetas(){

        // 🔹 Mostrar selector de origen (Número / CSV)
        rootSourceSection.isVisible = true
        rootSourceSection.isManaged = true

        // 🔹 Estado por defecto
        rbRootByNumber.isSelected = true
        txtRootBaseName.text = "Carpeta"

        mostrarSeccionNumero()
    }


    private fun mostrarSeccionNumero(){
        folderNumberSection.isVisible = true
        folderNumberSection.isManaged = true

        folderNameSection.isVisible = false
        folderNameSection.isManaged = false

        updateUIState()
    }

    private fun mostrarSeccionCsv() {
        folderNumberSection.isVisible = false
        folderNumberSection.isManaged = false

        folderNameSection.isVisible = true
        folderNameSection.isManaged = true

        updateUIState()
    }

    @FXML
    private fun cargarRootCsvClick() {
        val stage = btnRenamer.scene.window as Stage
        val chooser = FileChooser().apply {
            title = "Seleccionar CSV de nombres de carpetas"
            extensionFilters.add(
                FileChooser.ExtensionFilter("Archivos CSV", "*.csv")
            )
        }

        selectedRootCsvFile = chooser.showOpenDialog(stage)

        if (selectedRootCsvFile != null) {
            try {
                // Leer y mostrar los nombres del CSV
                val nombres = leerNombresDesdeCsv()
                if (nombres.isNotEmpty()) {
                    // 🔹 Validar nombres
                    val nombresValidos = nombres.filter { it.isNotBlank() && validarNombre(it) }
                    val nombresInvalidos = nombres.filterNot { it.isNotBlank() && validarNombre(it) }

                    // 🔹 Actualizar el Label
                    lblRootCsv.text = selectedRootCsvFile!!.name

                    val mensaje = buildString {
                        appendLine("CSV cargado correctamente:")
                        appendLine("Archivo: ${selectedRootCsvFile!!.name}")
                        appendLine("Total de nombres leídos: ${nombres.size}")
                        appendLine("Nombres válidos: ${nombresValidos.size}")

                        if (nombresInvalidos.isNotEmpty()) {
                            appendLine("Nombres inválidos: ${nombresInvalidos.size}")
                            appendLine("\nPrimeros 3 nombres inválidos:")
                            nombresInvalidos.take(3).forEach { appendLine("  • $it") }
                            if (nombresInvalidos.size > 3) {
                                appendLine("  ... y ${nombresInvalidos.size - 3} más")
                            }
                        }

                        appendLine("\nPrimeros 5 nombres válidos:")
                        nombresValidos.take(5).forEachIndexed { index, nombre ->
                            appendLine("${index + 1}. $nombre")
                        }
                        if (nombresValidos.size > 5) {
                            appendLine("... y ${nombresValidos.size - 5} más")
                        }

                        if (nombresInvalidos.isNotEmpty()) {
                            appendLine("\n⚠ Los nombres inválidos serán omitidos al crear la estructura.")
                        }
                    }
                    mostrarAlerta("CSV Cargado", mensaje)
                    escribeLog("CSV de estructura cargado: ${selectedRootCsvFile!!.name}")
                    escribeLog("Nombres válidos encontrados: ${nombresValidos.joinToString(", ")}")
                } else {
                    mostrarAlerta("Advertencia", "El CSV no contiene nombres válidos en la primera columna.")
                    escribeLog("ADVERTENCIA: CSV vacío o sin nombres válidos")
                    selectedRootCsvFile = null
                    // 🔹 Limpiar el Label si no hay nombres válidos
                    lblRootCsv.text = "No se ha cargado archivo"
                }
            } catch (e: Exception) {
                mostrarAlerta("Error", "Error al leer el CSV: ${e.message}")
                escribeLog("ERROR cargando CSV de estructura: ${e.message}")
                selectedRootCsvFile = null
                // 🔹 Limpiar el Label si hay error
                lblRootCsv.text = "No se ha cargado archivo"
            }
        } else {
            escribeLog("Operación cancelada: No se seleccionó archivo CSV")
            // 🔹 Limpiar el Label si se cancela
            lblRootCsv.text = "No se ha cargado archivo"
        }

        // 🔹 Actualizar UI después de cargar el CSV
        updateUIState()
    }

    private fun verificarDuplicadosYExistentes(nombres: List<String>, carpetaRaiz: File): Pair<List<String>, List<String>> {
        val nombresUnicos = mutableListOf<String>()
        val nombresDuplicados = mutableListOf<String>()
        val nombresExistentes = mutableListOf<String>()
        val nombresVistos = mutableSetOf<String>()

        for (nombre in nombres) {
            // Verificar si el nombre ya existe en la carpeta raíz
            val carpeta = File(carpetaRaiz, nombre)
            if (carpeta.exists()) {
                nombresExistentes.add(nombre)
                escribeLog("ADVERTENCIA: La carpeta '$nombre' ya existe en la carpeta raíz")
            }

            // Verificar duplicados en la lista de nombres
            if (nombresVistos.contains(nombre)) {
                nombresDuplicados.add(nombre)
                escribeLog("ADVERTENCIA: Nombre duplicado encontrado: '$nombre'")
            } else {
                nombresVistos.add(nombre)
                nombresUnicos.add(nombre)
            }
        }

        return Pair(nombresUnicos, nombresExistentes)
    }

    private fun splitCsvLine(line: String, separator: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var quoteChar: Char? = null

        var i = 0
        while (i < line.length) {
            val c = line[i]

            // Manejar comillas
            if ((c == '"' || c == '\'') && (i == 0 || line[i-1] != '\\')) {
                if (inQuotes) {
                    if (quoteChar == c) {
                        // Verificar si es una comilla escapada (dos comillas seguidas)
                        if (i + 1 < line.length && line[i+1] == c) {
                            current.append(c)
                            i++ // Saltar la comilla adicional
                        } else {
                            inQuotes = false
                            quoteChar = null
                        }
                    } else {
                        current.append(c)
                    }
                } else {
                    inQuotes = true
                    quoteChar = c
                }
            }
            // Manejar separador
            else if (!inQuotes && i + separator.length <= line.length &&
                line.substring(i, i + separator.length) == separator) {
                result.add(current.toString())
                current = StringBuilder()
                i += separator.length - 1 // -1 porque el for incrementará i
            }
            // Manejar caracter normal
            else {
                current.append(c)
            }
            i++
        }

        result.add(current.toString())
        return result
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