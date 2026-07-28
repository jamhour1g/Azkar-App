@org.jspecify.annotations.NullMarked
module com.azkar.app {
    requires com.azkar.data;
    requires com.azkar.domain;
    requires java.prefs;
    requires java.desktop;
    requires javafx.fxml;
    requires javafx.controls;
    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires static lombok;
    requires static org.jspecify;
    requires org.slf4j;
    requires jdk.jshell;

    opens com.azkar.controllers to
            javafx.fxml;
    opens com.azkar.components to
            javafx.fxml;
    opens com.azkar.components.home to
            javafx.fxml;
    opens com.azkar.components.library to
            javafx.fxml;
    opens com.azkar.components.library.ui.toolbar to
            javafx.fxml;
    opens com.azkar.components.library.ui.browse to
            javafx.fxml;
    opens com.azkar.components.library.ui.detail to
            javafx.fxml;
    opens com.azkar.components.library.ui.card to
            javafx.fxml;

    exports com.azkar to
            javafx.graphics;
    exports com.azkar.components.library.ui.toolbar to
            javafx.fxml;
    exports com.azkar.components.library.ui.browse to
            javafx.fxml;
    exports com.azkar.components.library.ui.state to
            javafx.fxml;
    exports com.azkar.components.library.ui.detail to
            javafx.fxml;
}
