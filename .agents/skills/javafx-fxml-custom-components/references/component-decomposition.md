# Component Decomposition Guide

This guide teaches how to break down monolithic FXML files into small, reusable, composable components.

## Why Decompose?

Monolithic FXML files are hard to maintain, test, and reuse. Decomposition provides:

- **Maintainability**: Smaller files are easier to understand and modify
- **Reusability**: Components can be used across multiple screens
- **Testability**: Isolated components are easier to test
- **Parallel development**: Team members can work on different components simultaneously
- **Scene Builder support**: Smaller components load faster and are easier to design

## Decomposition Strategy

### Step 1: Identify Logical Boundaries

Look for sections in your FXML that represent distinct UI concepts:

```
Home Screen
├── Greeting section (welcome message, location, date)
├── Daily Hadith section (hadith card with Arabic/English text)
├── Prayer Times section (remaining time, notifications, prayer list)
└── Favorites section (quick access to saved items)
```

Each of these should be its own component.

### Step 2: Apply the Single Responsibility Principle

Ask these questions for each section:

1. **Does this section have a clear purpose?** (e.g., "display greeting", "show prayer times")
2. **Could this be reused elsewhere?** (e.g., prayer row in multiple screens)
3. **Does it manage its own state?** (e.g., toggle buttons, form fields)
4. **Is it visually distinct?** (e.g., inside a card, panel, or bordered region)

If yes to any, extract it.

### Step 3: Create Component Hierarchy

Design a tree where:

- **Leaf components**: Simple, focused components (e.g., `PrayerRowComponent`)
- **Composite components**: Combine multiple leaf components (e.g., `PrayerTimesSection`)
- **Screen components**: Top-level layout orchestrators (e.g., `HomeScreenComponent`)

```
HomeComponent (Screen - layout only)
├── GreetingComponent (Leaf - displays greeting)
├── DailyHadithCardComponent (Leaf - displays hadith)
├── BorderPane (Layout container)
│   ├── center: VBox (Layout)
│   │   ├── RemainingToPrayerComponent (Leaf)
│   │   ├── PrayerNotificationsToggleComponent (Leaf)
│   │   └── GridPane (Layout)
│   │       └── PrayerRowComponent x5 (Leaf, repeated)
│   └── right: FavoritesCardComponent (Leaf)
```

### Step 4: Implement Bottom-Up

Start with the smallest, most focused components first:

1. **Leaf components**: `PrayerRowComponent`, `GreetingComponent`
2. **Composite components**: `PrayerTimesSection` (if needed)
3. **Screen component**: `HomeComponent` (assembles everything)

## Real-World Example: Azkar App Home Screen

### Before: Monolithic FXML (BAD)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.*?>
<?import javafx.scene.control.*?>
<?import javafx.scene.layout.*?>
<?import javafx.scene.text.*?>

<fx:root type="ScrollPane" xmlns:fx="http://javafx.com/fxml">
    <VBox spacing="20" styleClass="main-content">
        <!-- Greeting - 30 lines inline -->
        <VBox alignment="CENTER" spacing="8" styleClass="greeting-container">
            <Text text="Assalamu Alaikum" styleClass="greeting-title">
                <font><Font size="48.0" name="Inter Bold"/></font>
            </Text>
            <Text text="Prayer times for Ramallah, Palestine" styleClass="greeting-location"/>
            <Text text="14 Dhu al-Hijjah 1445 AH | 20 June 2024" styleClass="greeting-date"/>
        </VBox>
        
        <!-- Daily Hadith - 40 lines inline -->
        <VBox spacing="12" styleClass="card-lg">
            <Text text="%dailyHadith" styleClass="section-title"/>
            <HBox>
                <Separator orientation="VERTICAL" styleClass="hadith-vertical-line">
                    <padding><Insets top="5" right="10" bottom="5" left="5"/></padding>
                </Separator>
                <VBox spacing="10">
                    <Text text="%hadithExample" styleClass="hadith-ar"/>
                    <Text text="%explanationExample" styleClass="hadith-en" wrappingWidth="800"/>
                    <Text text="%sourceExample" styleClass="hadith-source"/>
                </VBox>
            </HBox>
        </VBox>
        
        <!-- Prayer Times - 80 lines inline -->
        <VBox spacing="10">
            <!-- Remaining prayer component - 20 lines -->
            <VBox styleClass="card">
                <Text text="Next prayer: Dhuhr"/>
                <Text text="Remaining: 2h 30m"/>
            </VBox>
            
            <!-- Prayer notifications - 15 lines -->
            <HBox spacing="10">
                <Label text="Enable notifications"/>
                <ToggleSwitch fx:id="togglePrayerNotifications"/>
            </HBox>
            
            <!-- Prayer rows - 45 lines -->
            <VBox styleClass="card">
                <GridPane hgap="10" vgap="10">
                    <!-- Fajr row - 9 lines -->
                    <HBox GridPane.rowIndex="0">
                        <FontIcon iconLiteral="far-sun"/>
                        <VBox>
                            <Text text="Fajr"/>
                            <Text text="04:50 AM"/>
                        </VBox>
                        <Pane HBox.hgrow="SOMETIMES"/>
                        <Text text="04:50 AM"/>
                        <ToggleButton>
                            <graphic><FontIcon iconLiteral="far-bell"/></graphic>
                        </ToggleButton>
                    </HBox>
                    <!-- Dhuhr row - 9 lines -->
                    <!-- Asr row - 9 lines -->
                    <!-- Maghrib row - 9 lines -->
                    <!-- Isha row - 9 lines -->
                </GridPane>
            </VBox>
        </VBox>
        
        <!-- Favorites - 20 lines inline -->
        <VBox styleClass="card">
            <Text text="Favorites"/>
            <ListView fx:id="favoritesList"/>
        </VBox>
    </VBox>
</fx:root>
```

**Problems:**
- 170+ lines of FXML
- Multiple responsibilities in one file
- Hard to reuse individual sections
- Difficult to test
- Slow to load in Scene Builder

### After: Decomposed Components (GOOD)

#### 1. GreetingComponent.fxml (15 lines)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.VBox?>
<?import javafx.scene.text.Font?>
<?import javafx.scene.text.Text?>

<fx:root alignment="CENTER" spacing="8" styleClass="greeting-container"
    type="VBox" xmlns:fx="http://javafx.com/fxml">
    <Text fx:id="greetingTitle" styleClass="greeting-title" text="Assalamu Alaikum">
        <font><Font size="48.0" name="Inter Bold"/></font>
    </Text>
    <Text fx:id="locationText" styleClass="greeting-location" 
        text="Prayer times for Ramallah, Palestine"/>
    <Text fx:id="dateText" styleClass="greeting-date" 
        text="14 Dhu al-Hijjah 1445 AH | 20 June 2024"/>
</fx:root>
```

#### 2. DailyHadithCardComponent.fxml (25 lines)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Separator?>
<?import javafx.scene.layout.*?>
<?import javafx.scene.text.Text?>

<fx:root spacing="12" styleClass="card-lg" type="VBox"
    stylesheets="@../../styles/home/daily_hadith_card.css"
    xmlns:fx="http://javafx.com/fxml">
    <Text styleClass="section-title" text="%dailyHadith"/>
    <HBox>
        <Separator orientation="VERTICAL" styleClass="hadith-vertical-line" HBox.hgrow="NEVER">
            <padding><Insets top="5" right="5" bottom="5" left="5"/></padding>
            <HBox.margin><Insets right="10"/></HBox.margin>
        </Separator>
        <VBox spacing="10">
            <Text fx:id="hadithArabic" styleClass="hadith-ar" text="%hadithExample"/>
            <Text fx:id="hadithEnglish" styleClass="hadith-en" text="%explanationExample" 
                wrappingWidth="800"/>
            <Text fx:id="hadithSource" styleClass="hadith-source" text="%sourceExample"/>
        </VBox>
    </HBox>
</fx:root>
```

#### 3. PrayerRowComponent.fxml (30 lines)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.geometry.Insets?>
<?import javafx.scene.control.ToggleButton?>
<?import javafx.scene.layout.*?>
<?import javafx.scene.text.Text?>
<?import org.kordamp.ikonli.javafx.FontIcon?>

<fx:root alignment="CENTER" spacing="5" styleClass="prayer-row" type="HBox"
    stylesheets="@../../styles/home/prayer-row-component.css"
    xmlns:fx="http://javafx.com/fxml">
    <AnchorPane>
        <FontIcon iconLiteral="far-sun" iconSize="22" layoutY="30"/>
    </AnchorPane>
    <VBox spacing="5">
        <Text fx:id="prayerName" styleClass="prayer-name" text="PrayerPlaceHolderName"/>
        <Text fx:id="prayerTime" styleClass="prayer-time" text="-00:00 AM"/>
    </VBox>
    <Pane HBox.hgrow="SOMETIMES">
        <padding><Insets top="5" right="5" bottom="5" left="5"/></padding>
    </Pane>
    <Text fx:id="prayerValue" styleClass="prayer-value" text="04:50 AM"/>
    <ToggleButton fx:id="notificationToggle" styleClass="icon-circle-btn">
        <graphic><FontIcon iconLiteral="far-bell" iconSize="22"/></graphic>
    </ToggleButton>
</fx:root>
```

#### 4. HomeComponent.fxml (35 lines - composition only!)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import com.azkar.components.home.*?>
<?import javafx.geometry.Insets?>
<?import javafx.scene.control.ScrollPane?>
<?import javafx.scene.layout.*?>

<fx:root fitToHeight="true" fitToWidth="true" hbarPolicy="NEVER"
    styleClass="main" type="ScrollPane" vbarPolicy="NEVER"
    stylesheets="@../../styles/home/home_component.css"
    xmlns:fx="http://javafx.com/fxml">
    <VBox alignment="TOP_CENTER" spacing="20" styleClass="main-content">
        <GreetingComponent/>
        <DailyHadithCardComponent/>
        <BorderPane>
            <center>
                <VBox spacing="10">
                    <RemainingToPrayerComponent/>
                    <PrayerNotificationsToggleComponent/>
                    <VBox alignment="CENTER" spacing="5" styleClass="card">
                        <GridPane hgap="10" vgap="10">
                            <columnConstraints>
                                <ColumnConstraints hgrow="SOMETIMES" minWidth="10" 
                                    prefWidth="100"/>
                            </columnConstraints>
                            <PrayerRowComponent prayerName="Fajr" prayerTime="04:50 AM" 
                                prayerValue="04:50 AM"/>
                            <PrayerRowComponent prayerName="Dhuhr" prayerTime="01:20 PM" 
                                prayerValue="01:20 PM" GridPane.rowIndex="1"/>
                            <PrayerRowComponent prayerName="Asr" prayerTime="05:15 PM" 
                                prayerValue="05:15 PM" GridPane.rowIndex="2"/>
                            <PrayerRowComponent prayerName="Maghrib" prayerTime="07:45 PM" 
                                prayerValue="07:45 PM" GridPane.rowIndex="3"/>
                            <PrayerRowComponent prayerName="Isha" prayerTime="09:30 PM" 
                                prayerValue="09:30 PM" GridPane.rowIndex="4"/>
                        </GridPane>
                    </VBox>
                </VBox>
            </center>
            <padding><Insets top="5" right="5" bottom="5" left="5"/></padding>
            <right>
                <FavoritesCardComponent/>
            </right>
        </BorderPane>
    </VBox>
</fx:root>
```

**Benefits:**
- Each component is 15-35 lines (easy to understand)
- Components are reusable (e.g., `PrayerRowComponent` used 5 times)
- Each component can be tested in isolation
- Scene Builder loads each component quickly
- Team members can work on different components simultaneously

## Decomposition Checklist

Use this checklist to verify your decomposition:

- [ ] Each component has a single, clear responsibility
- [ ] Component FXML files are under 60 lines (excluding imports)
- [ ] Nesting depth is 5 levels or less
- [ ] Components are named after their purpose (e.g., `GreetingComponent`, not `VBox1`)
- [ ] Parent components only handle layout, not business logic
- [ ] Child components expose clear attribute APIs (setters/properties)
- [ ] Components can be reused in other contexts
- [ ] Each component has its own CSS file (when styling is complex)

## Common Decomposition Patterns

### 1. List Item Pattern

When you have repeated items in a list, extract each item as a component:

```xml
<!-- BAD: Inline repeated items -->
<VBox>
    <!-- Item 1: 15 lines -->
    <!-- Item 2: 15 lines -->
    <!-- Item 3: 15 lines -->
</VBox>

<!-- GOOD: Reusable component -->
<VBox>
    <ListItemComponent title="Item 1" subtitle="..."/>
    <ListItemComponent title="Item 2" subtitle="..." GridPane.rowIndex="1"/>
    <ListItemComponent title="Item 3" subtitle="..." GridPane.rowIndex="2"/>
</VBox>
```

### 2. Card Pattern

When content is visually bounded by a card/panel, extract it:

```xml
<!-- BAD: All card content inline -->
<VBox styleClass="card">
    <!-- 40 lines of card content -->
</VBox>

<!-- GOOD: Extracted card component -->
<InfoCardComponent title="..." content="..." icon="..."/>
```

### 3. Form Section Pattern

Group related form fields into components:

```xml
<!-- BAD: All form fields inline -->
<VBox>
    <!-- Personal info fields (30 lines) -->
    <!-- Address fields (40 lines) -->
    <!-- Payment fields (50 lines) -->
</VBox>

<!-- GOOD: Form section components -->
<VBox>
    <PersonalInfoSection/>
    <AddressSection/>
    <PaymentSection/>
</VBox>
```

### 4. Toolbar/Actions Pattern

Extract action buttons/toolbars:

```xml
<!-- BAD: All buttons inline -->
<HBox>
    <Button text="Save" onAction="#save"/>
    <Button text="Delete" onAction="#delete"/>
    <Button text="Cancel" onAction="#cancel"/>
    <Button text="Export" onAction="#export"/>
</HBox>

<!-- GOOD: Action bar component -->
<ActionBar onSave="#save" onDelete="#delete" onCancel="#cancel" onExport="#export"/>
```

## Refactoring Strategy

When converting an existing monolithic FXML:

1. **Identify boundaries**: Mark logical sections with comments
2. **Extract leaf components**: Start with the smallest, most independent sections
3. **Test each extraction**: Verify the component works before moving to the next
4. **Update parent FXML**: Replace inline UI with component tags
5. **Clean up**: Remove unused imports, consolidate styles

## Component API Design

Good components expose declarative APIs:

```java
// GOOD: Clear, declarative API
public class PrayerRowComponent extends HBox {
    public void setPrayerName(String name) { ... }
    public String getPrayerName() { ... }
    public StringProperty prayerNameProperty() { ... }
    
    public void setPrayerTime(String time) { ... }
    public String getPrayerTime() { ... }
    public StringProperty prayerTimeProperty() { ... }
}

// Usage in FXML:
<PrayerRowComponent prayerName="Fajr" prayerTime="04:50 AM"/>
```

Avoid components that require imperative setup:

```java
// BAD: Requires imperative setup
public class PrayerRowComponent extends HBox {
    // No setters - must call methods after instantiation
    public void initializeWithPrayer(Prayer prayer) { ... }
}

// Usage requires controller code:
// In parent controller:
prayerRow.initializeWithPrayer(fajrPrayer);
```
