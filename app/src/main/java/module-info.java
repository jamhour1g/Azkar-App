module com.azkar.app {
    requires javafx.fxml;
    requires javafx.controls;
    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires static lombok;

    opens com.azkar.controllers to
            javafx.fxml;
    opens com.azkar.components to
            javafx.fxml;
    opens com.azkar.components.home to
            javafx.fxml;
    opens com.azkar.components.library to
            javafx.fxml;

    exports com.azkar to
            javafx.graphics;
}
