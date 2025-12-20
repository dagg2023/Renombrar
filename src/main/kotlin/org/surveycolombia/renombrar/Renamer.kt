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
 * @file Rename.kt
 * @brief Punto de entrada de la aplicación JavaFX.
 */

package org.surveycolombia.renombrar

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Stage

/**
 * @class Rename
 * @brief Clase principal que inicia la aplicación JavaFX.
 *
 * Configura:
 * - La ventana principal
 * - La escena con el FXML
 * - El icono de la aplicación
 */

class Rename : Application() {
    /**
     * @fn start
     * @brief Método principal de JavaFX.
     * @param stage Ventana principal proporcionada por JavaFX.
     */

    override fun start(stage: Stage) {
        val fxmlLoader = FXMLLoader(Rename::class.java.getResource("renamer_view.fxml"))
        val scene = Scene(fxmlLoader.load(), 600.0, 750.0)

        stage.title = "[ R E N O M B R A R ]"
        val icon = Image(javaClass.getResourceAsStream("/app_icon.png"))
        stage.icons.add(icon)
        stage.scene = scene
        stage.show()
    }
}

/**
 * @fn main
 * @brief Punto de entrada de la aplicación.
 */

fun main() {
    Application.launch(Rename::class.java)
}