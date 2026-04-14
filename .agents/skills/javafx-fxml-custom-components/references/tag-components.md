# Tag-Based Custom Components (Primary)

This guide is for reusable FXML-backed components used directly as tags in parent FXML.

Example target usage:

```xml
<?import com.example.components.SearchBar?>
<?import javafx.scene.layout.BorderPane?>

<BorderPane xmlns:fx="http://javafx.com/fxml">
    <top>
        <SearchBar fx:id="searchBar" queryText="Initial value"/>
    </top>
</BorderPane>
```

## What Makes A Component Usable As A Tag

1. Component class is a public class extending a JavaFX Node subtype (`VBox`, `HBox`, `ScrollPane`, etc.)
2. Component class has a public no-arg constructor (FXML instantiation requirement)
3. Component FXML uses `<fx:root type="...">` with a type matching the class superclass
4. Constructor wires loader in this order: `setRoot(this)`, `setController(this)`, `load()`
5. Parent FXML imports component class and uses uppercase class tag

## Component Design Principles

### Single Responsibility

Each component should have ONE clear purpose:

- ❌ BAD: `HomeScreenComponent` - handles greeting, hadith, prayer times, favorites
- ✅ GOOD: `GreetingComponent`, `DailyHadithCardComponent`, `PrayerTimesComponent`, `FavoritesCardComponent`

### Size Constraints

- **FXML lines**: 20-40 lines target, 60 lines maximum (excluding imports)
- **Nesting depth**: 5 levels maximum
- **Direct children**: 8 maximum per container
- **Event handlers**: If you have 5+ handlers, consider splitting

### Attribute-Focused API

Design components with clear, declarative attributes:

```xml
<!-- GOOD: Clear, declarative API -->
<PrayerRowComponent prayerName="Fajr" prayerTime="04:50 AM" prayerValue="04:50 AM"/>

<!-- BAD: Opaque, requires imperative code -->
<PrayerRowComponent fx:id="fajrRow"/>
<!-- Then in controller: fajrRow.setName("Fajr"); fajrRow.setTime("04:50 AM"); -->
```

## Canonical Component Template

### Component FXML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.TextField?>

<fx:root type="javafx.scene.layout.HBox" xmlns:fx="http://javafx.com/fxml" spacing="8">
    <children>
        <TextField fx:id="queryField" promptText="Search..."/>
        <Button text="Go" onAction="#onSearch"/>
    </children>
</fx:root>
```

### Component Class

```java
package com.example.components;

import java.io.IOException;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public class SearchBar extends HBox {
    @FXML
    private TextField queryField;

    public SearchBar() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("SearchBar.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load SearchBar.fxml", e);
        }
    }

    @FXML
    private void onSearch() {
        // trigger callback or event
    }

    public String getQueryText() {
        return queryField.getText();
    }

    public void setQueryText(String value) {
        queryField.setText(value);
    }
}
```

## Attribute Mapping Rules For Tag Usage

When parent FXML sets attributes on a custom tag:

```xml
<SearchBar queryText="hello"/>
```

FXML resolves `queryText` to component property APIs using bean conventions:

- preferred: `setQueryText(String)` (+ optional `getQueryText()`)
- optional property API: `queryTextProperty()`

For booleans:

- `enabled="true"` maps to `setEnabled(boolean)` (or existing inherited writable property)

If no writable property API exists, load fails with a cannot-set-property style error.

## Parent FXML Import Rules

### Option A: explicit imports

```xml
<?import com.example.components.SearchBar?>
<?import com.example.components.ProfileCard?>
```

### Option B: wildcard import

```xml
<?import com.example.components.*?>
```

Use explicit imports when you want tighter clarity; wildcard import is fine for many components in one package.

## Component Composition Example

### Screen Composition (GOOD)

This example shows a properly decomposed screen with small, focused components:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import com.azkar.components.home.*?>
<?import javafx.scene.layout.*?>

<fx:root type="VBox" xmlns:fx="http://javafx.com/fxml" spacing="20">
    <!-- Each logical section is its own component -->
    <GreetingComponent />
    
    <DailyHadithCardComponent />
    
    <BorderPane>
        <center>
            <VBox spacing="10">
                <RemainingToPrayerComponent />
                <PrayerNotificationsToggleComponent />
                <PrayerTimesGridComponent />
            </VBox>
        </center>
        <right>
            <FavoritesCardComponent />
        </right>
    </BorderPane>
</fx:root>
```

Each of these components is:
- Under 40 lines of FXML
- Focused on one responsibility
- Reusable in other contexts
- Testable in isolation

### Monolith Anti-Pattern (BAD)

Avoid this pattern - a single FXML with everything inline:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.*?>
<?import javafx.scene.control.*?>
<?import javafx.scene.layout.*?>
<?import javafx.scene.text.*?>

<!-- BAD: 150+ lines, multiple responsibilities, hard to maintain -->
<fx:root type="ScrollPane" xmlns:fx="http://javafx.com/fxml">
    <VBox spacing="20">
        <!-- All greeting UI inline (30 lines) -->
        <VBox alignment="CENTER" spacing="8" styleClass="greeting-container">
            <Text text="Assalamu Alaikum" styleClass="greeting-title"/>
            <Text text="Prayer times for..." styleClass="greeting-location"/>
            <Text text="14 Dhu al-Hijjah..." styleClass="greeting-date"/>
        </VBox>
        
        <!-- All hadith UI inline (40 lines) -->
        <VBox spacing="12" styleClass="card-lg">
            <Text text="Daily Hadith"/>
            <HBox>
                <Separator orientation="VERTICAL"/>
                <VBox spacing="10">
                    <Text text="Arabic text..."/>
                    <Text text="English translation..."/>
                    <Text text="Source..."/>
                </VBox>
            </HBox>
        </VBox>
        
        <!-- All prayer times UI inline (60 lines) -->
        <!-- All favorites UI inline (20 lines) -->
    </VBox>
</fx:root>
```

## When To Use `fx:include` Instead Of Tags

Use tags for reusable component classes.

Use `<fx:include>` when you want to load a separate FXML document as a subview with include-controller injection in a parent controller.

## High-Value Failure Modes

1. `Root hasn't been set` -> forgot `setRoot(this)` before `load()`
2. `Controller value already specified` -> used both `fx:controller` and `setController(...)`
3. `Cannot set property 'x'` -> missing setter/property API for tag attribute
4. Class tag not resolved -> missing `<?import ...?>` or wrong package/class name
5. `@FXML` fields null -> fx:id mismatch or module access (`opens ... to javafx.fxml`) missing
