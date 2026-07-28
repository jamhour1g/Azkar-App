package com.azkar.components.library.ui.browse;

import com.azkar.components.library.ui.state.LibraryEmptyStateComponent;
import com.azkar.components.library.ui.state.LibraryLoadingStateComponent;
import java.io.IOException;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class LibraryBrowsePaneComponent extends VBox {

    @FXML
    private TabPane categoriesTabs;

    @FXML
    private Tab allTab;

    @FXML
    private Tab favoritesTab;

    @FXML
    private StackPane browseStack;

    @FXML
    private LibraryTableComponent tableComponent;

    @FXML
    private LibraryGridPaneComponent gridPaneComponent;

    @FXML
    private LibraryLoadingStateComponent loadingStateComponent;

    @FXML
    private LibraryEmptyStateComponent emptyStateComponent;

    private LibraryBrowsePaneComponent(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/library/library_browse_pane_component.fxml"),
                loadedBundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load library browse pane component", exception);
        }
    }

    public LibraryBrowsePaneComponent() {
        this("com.azkar.i18n.home");
    }

    public TabPane getCategoriesTabs() {
        return categoriesTabs;
    }

    public Tab getAllTab() {
        return allTab;
    }

    public Tab getFavoritesTab() {
        return favoritesTab;
    }

    public StackPane getBrowseStack() {
        return browseStack;
    }

    public LibraryTableComponent getTableComponent() {
        return tableComponent;
    }

    public LibraryGridPaneComponent getGridPaneComponent() {
        return gridPaneComponent;
    }

    public LibraryLoadingStateComponent getLoadingStateComponent() {
        return loadingStateComponent;
    }

    public LibraryEmptyStateComponent getEmptyStateComponent() {
        return emptyStateComponent;
    }
}
