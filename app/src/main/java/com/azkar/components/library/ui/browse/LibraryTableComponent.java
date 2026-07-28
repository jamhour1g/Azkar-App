package com.azkar.components.library.ui.browse;

import com.azkar.components.library.model.LibraryRemembranceRow;
import java.io.IOException;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import lombok.Getter;

@Getter
public class LibraryTableComponent extends TableView<LibraryRemembranceRow> {

    @FXML
    private TableColumn<LibraryRemembranceRow, String> categoryColumn;

    @FXML
    private TableColumn<LibraryRemembranceRow, String> arabicColumn;

    @FXML
    private TableColumn<LibraryRemembranceRow, String> englishColumn;

    @FXML
    private TableColumn<LibraryRemembranceRow, String> sourceColumn;

    @FXML
    private TableColumn<LibraryRemembranceRow, LibraryRemembranceRow> actionsColumn;

    private LibraryTableComponent(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/library/library_table_component.fxml"), loadedBundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load library table component", exception);
        }
    }

    public LibraryTableComponent() {
        this("com.azkar.i18n.home");
    }
}
