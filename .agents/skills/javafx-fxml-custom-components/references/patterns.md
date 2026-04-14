# Canonical Patterns

Use these as the default implementations unless the user has a specific architecture constraint.

## Pattern A: Tag-Based Reusable Component With `fx:root` (Primary)

Use when the component should be represented directly as a class tag in parent FXML.

### When to Use This Pattern

- Component will be reused across multiple screens
- Component has a clear, single responsibility
- Component needs a declarative FXML attribute API
- Component is 20-60 lines of FXML

### Parent FXML Usage

```xml
<?import com.example.controls.FilterInput?>
<?import javafx.scene.layout.VBox?>

<VBox xmlns:fx="http://javafx.com/fxml">
    <children>
        <FilterInput fx:id="filter" text="initial"/>
    </children>
</VBox>
```

### FXML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.control.*?>
<?import javafx.scene.layout.*?>

<fx:root type="javafx.scene.layout.VBox" xmlns:fx="http://javafx.com/fxml">
    <children>
        <TextField fx:id="textField" promptText="Type here"/>
        <Button text="Apply" onAction="#onApply"/>
    </children>
</fx:root>
```

### Java

```java
package com.example.controls;

import java.io.IOException;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class FilterInput extends VBox {
    @FXML
    private TextField textField;

    public FilterInput() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("FilterInput.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load FilterInput.fxml", e);
        }
    }

    public final String getText() {
        return textField.getText();
    }

    public final void setText(String value) {
        textField.setText(value);
    }

    @FXML
    private void onApply() {
        // Emit event, invoke callback, or update bound property.
    }
}
```

### Important

- Keep `fx:controller` out of this FXML
- Always call `setRoot` and `setController` before `load()`
- Expose a narrow public API (properties, callbacks, or simple methods)
- Ensure attributes used in parent tags map to writable setters/properties

## Pattern A2: Screen Composition With Multiple Components

Use when building a complete screen from smaller, focused components.

### Component Hierarchy

```
HomeScreen (layout orchestrator)
├── GreetingComponent
├── DailyHadithCardComponent  
├── PrayerTimesSection
│   ├── RemainingToPrayerComponent
│   ├── PrayerNotificationsToggleComponent
│   └── PrayerRowComponent (repeated)
└── FavoritesCardComponent
```

### Screen Component FXML (HomeComponent.fxml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import com.azkar.components.home.*?>
<?import javafx.geometry.Insets?>
<?import javafx.scene.control.ScrollPane?>
<?import javafx.scene.layout.*?>

<!-- Screen component: layout only, coordinates child components -->
<fx:root fitToHeight="true" fitToWidth="true" hbarPolicy="NEVER"
    styleClass="main" type="ScrollPane" vbarPolicy="NEVER"
    xmlns:fx="http://javafx.com/fxml">
    
    <VBox alignment="TOP_CENTER" spacing="20" styleClass="main-content">
        <!-- Each logical section is its own component -->
        <GreetingComponent/>
        <DailyHadithCardComponent/>
        
        <BorderPane>
            <center>
                <VBox spacing="10">
                    <RemainingToPrayerComponent/>
                    <PrayerNotificationsToggleComponent/>
                    
                    <!-- Repeated component pattern -->
                    <VBox alignment="CENTER" spacing="5" styleClass="card">
                        <GridPane hgap="10" vgap="10">
                            <columnConstraints>
                                <ColumnConstraints hgrow="SOMETIMES" 
                                    minWidth="10" prefWidth="100"/>
                            </columnConstraints>
                            
                            <PrayerRowComponent prayerName="Fajr" 
                                prayerTime="04:50 AM" prayerValue="04:50 AM"/>
                            <PrayerRowComponent prayerName="Dhuhr" 
                                prayerTime="01:20 PM" prayerValue="01:20 PM" 
                                GridPane.rowIndex="1"/>
                            <PrayerRowComponent prayerName="Asr" 
                                prayerTime="05:15 PM" prayerValue="05:15 PM" 
                                GridPane.rowIndex="2"/>
                            <PrayerRowComponent prayerName="Maghrib" 
                                prayerTime="07:45 PM" prayerValue="07:45 PM" 
                                GridPane.rowIndex="3"/>
                            <PrayerRowComponent prayerName="Isha" 
                                prayerTime="09:30 PM" prayerValue="09:30 PM" 
                                GridPane.rowIndex="4"/>
                        </GridPane>
                    </VBox>
                </VBox>
            </center>
            
            <padding>
                <Insets top="5" right="5" bottom="5" left="5"/>
            </padding>
            
            <right>
                <FavoritesCardComponent/>
            </right>
        </BorderPane>
    </VBox>
</fx:root>
```

### Screen Component Java Class

```java
package com.azkar.components.home;

import java.util.ResourceBundle;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ScrollPane;
import lombok.SneakyThrows;

/**
 * Home screen component that orchestrates multiple child components.
 * This component handles layout only - child components manage their own state.
 */
public class HomeComponent extends ScrollPane {

    @SneakyThrows
    private HomeComponent(String bundleName) {
        var bundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
            getClass().getResource("/com/azkar/components/home/home_component.fxml"), 
            bundle
        );
        fxmlLoader.setController(this);
        fxmlLoader.setRoot(this);
        fxmlLoader.load();
    }

    public HomeComponent() {
        this("com.azkar.i18n.home");
    }
}
```

### Leaf Component FXML (PrayerRowComponent.fxml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.geometry.Insets?>
<?import javafx.scene.control.ToggleButton?>
<?import javafx.scene.layout.*?>
<?import javafx.scene.text.Text?>
<?import org.kordamp.ikonli.javafx.FontIcon?>

<!-- Leaf component: focused, single responsibility -->
<fx:root alignment="CENTER" spacing="5" styleClass="prayer-row" type="HBox"
    stylesheets="@../../styles/home/prayer-row-component.css"
    xmlns:fx="http://javafx.com/fxml">
    
    <AnchorPane>
        <FontIcon iconLiteral="far-sun" iconSize="22" layoutY="30"/>
    </AnchorPane>
    
    <VBox spacing="5">
        <Text fx:id="prayerName" styleClass="prayer-name" text="PrayerName"/>
        <Text fx:id="prayerTime" styleClass="prayer-time" text="-00:00 AM"/>
    </VBox>
    
    <Pane HBox.hgrow="SOMETIMES">
        <padding>
            <Insets top="5" right="5" bottom="5" left="5"/>
        </padding>
    </Pane>
    
    <Text fx:id="prayerValue" styleClass="prayer-value" text="04:50 AM"/>
    
    <ToggleButton fx:id="notificationToggle" styleClass="icon-circle-btn">
        <graphic>
            <FontIcon iconLiteral="far-bell" iconSize="22"/>
        </graphic>
    </ToggleButton>
</fx:root>
```

### Leaf Component Java Class

```java
package com.azkar.components.home;

import java.util.ResourceBundle;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import lombok.SneakyThrows;

/**
 * Single row displaying prayer information with notification toggle.
 * Designed to be repeated in a grid or list.
 */
public class PrayerRowComponent extends HBox {

    @FXML
    private Text prayerName;

    @FXML
    private Text prayerTime;

    @FXML
    private Text prayerValue;

    @SneakyThrows
    private PrayerRowComponent(String bundleName) {
        var bundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
            getClass().getResource("/com/azkar/components/home/prayer_row_component.fxml"), 
            bundle
        );
        fxmlLoader.setController(this);
        fxmlLoader.setRoot(this);
        fxmlLoader.load();
    }

    public PrayerRowComponent() {
        this("com.azkar.i18n.home");
    }

    // Property API for FXML tag attributes
    public void setPrayerName(String prayerName) {
        this.prayerName.setText(prayerName);
    }

    public String getPrayerName() {
        return prayerName.getText();
    }

    public StringProperty prayerNameProperty() {
        return prayerName.textProperty();
    }

    public void setPrayerTime(String prayerTime) {
        this.prayerTime.setText(prayerTime);
    }

    public String getPrayerTime() {
        return prayerTime.getText();
    }

    public StringProperty prayerTimeProperty() {
        return prayerTime.textProperty();
    }

    public void setPrayerValue(String prayerValue) {
        this.prayerValue.setText(prayerValue);
    }

    public String getPrayerValue() {
        return prayerValue.getText();
    }

    public StringProperty prayerValueProperty() {
        return prayerValue.textProperty();
    }
}
```

## Pattern B: Static Composition With `fx:include` (Secondary)

Use when a parent view includes a child view with its own controller.

### Parent FXML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.*?>

<VBox xmlns:fx="http://javafx.com/fxml" fx:controller="com.example.ParentController">
    <children>
        <fx:include fx:id="toolbar" source="Toolbar.fxml"/>
    </children>
</VBox>
```

### Parent Controller

```java
package com.example;

import javafx.fxml.FXML;
import javafx.scene.layout.HBox;

public class ParentController {
    @FXML
    private HBox toolbar;

    @FXML
    private ToolbarController toolbarController;

    @FXML
    private void initialize() {
        toolbarController.setMode("compact");
    }
}
```

### Important

- Parent include id is `toolbar` -> injected controller field must be `toolbarController`
- Child FXML should define `fx:controller` unless controller is set programmatically

## Pattern C: Detached Include In `fx:define` (Dialog-Like Graph)

Use when the included view should not become part of the parent scene graph.

### Parent FXML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.*?>

<VBox xmlns:fx="http://javafx.com/fxml" fx:controller="com.example.MainController">
    <fx:define>
        <fx:include fx:id="settingsDialog" source="SettingsDialog.fxml"/>
    </fx:define>
    <children>
        <Button text="Open Settings" onAction="#openSettings"/>
    </children>
</VBox>
```

### Parent Controller

```java
package com.example;

import javafx.fxml.FXML;
import javafx.stage.Window;

public class MainController {
    @FXML
    private Window settingsDialog;

    @FXML
    private SettingsDialogController settingsDialogController;

    @FXML
    private void openSettings() {
        settingsDialogController.show(settingsDialog);
    }
}
```

## Pattern D: Dynamic Load + Controller Access

Use when the child is selected at runtime or instantiated conditionally.

```java
FXMLLoader loader = new FXMLLoader(getClass().getResource("InspectorPanel.fxml"));
Node childRoot = loader.load();
InspectorPanelController childController = loader.getController();

childController.setOnSelectionChanged(item -> this.onInspectorSelection(item));
container.getChildren().add(childRoot);
```

### Important

- Use `FXMLLoader` instance API when you need `getController()`
- `getController()` is valid after `load()`

## Pattern E: Controller Factory (DI)

Use when controllers need constructor dependencies.

```java
FXMLLoader loader = new FXMLLoader(getClass().getResource("Dashboard.fxml"));
loader.setControllerFactory(type -> {
    if (type == DashboardController.class) {
        return new DashboardController(service, eventBus);
    }
    try {
        return type.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
});

Parent root = loader.load();
```

### Important

- Do not also call `setController(...)` for the same load unless you intentionally bypass factory creation for that document
- Keep one clear controller instantiation strategy per FXML

## Pattern F: Repeated Component In Grid/List

Use when the same component type appears multiple times with different data.

### Parent FXML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import com.example.components.PrayerRow?>
<?import javafx.scene.layout.*?>

<VBox xmlns:fx="http://javafx.com/fxml" spacing="10">
    <GridPane hgap="10" vgap="10">
        <PrayerRow prayerName="Fajr" prayerTime="04:50 AM" prayerValue="04:50 AM"/>
        <PrayerRow prayerName="Dhuhr" prayerTime="01:20 PM" prayerValue="01:20 PM" 
            GridPane.rowIndex="1"/>
        <PrayerRow prayerName="Asr" prayerTime="05:15 PM" prayerValue="05:15 PM" 
            GridPane.rowIndex="2"/>
        <PrayerRow prayerName="Maghrib" prayerTime="07:45 PM" prayerValue="07:45 PM" 
            GridPane.rowIndex="3"/>
        <PrayerRow prayerName="Isha" prayerTime="09:30 PM" prayerValue="09:30 PM" 
            GridPane.rowIndex="4"/>
    </GridPane>
</VBox>
```

### Benefits

- DRY: No duplicated markup
- Maintainable: Change once, affects all instances
- Testable: Test component in isolation
- Declarative: Clear data flow via attributes
