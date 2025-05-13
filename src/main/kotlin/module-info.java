module org.surveycolombia.renombrar {
    requires javafx.controls;
    requires javafx.fxml;
    requires kotlin.stdlib;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires java.logging;

    opens org.surveycolombia.renombrar to javafx.fxml;
    exports org.surveycolombia.renombrar;
}