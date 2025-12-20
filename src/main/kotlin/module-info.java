module org.surveycolombia.renombrar {
    requires javafx.controls;
    requires javafx.fxml;
    requires kotlin.stdlib;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires java.logging;
    requires org.json;   // 👈 aquí

    opens org.surveycolombia.renombrar to javafx.fxml;
    opens org.surveycolombia.renombrar.services to javafx.fxml; // 👈 si usas FXML en services
    exports org.surveycolombia.renombrar;
}