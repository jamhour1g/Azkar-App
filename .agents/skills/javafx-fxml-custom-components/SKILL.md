---
name: javafx-fxml-custom-components
description: Build and wire JavaFX FXML custom components that are used directly as class-instance tags in parent FXML (for example `<MyComponent title="..."/>`). Use this whenever users mention custom component tags, fx:root, FXMLLoader setRoot/setController, import processing instructions for custom tags, attribute-to-setter/property mapping, Scene Builder custom component import issues, nesting custom components, controller-to-controller communication between included components, or module-info.java JPMS reflection access for FXML components. Also use this when users want to convert existing FXML into reusable tag-based components, or when they're troubleshooting class-not-found, cannot-set-property, or controller conflict errors with custom FXML tags.
---

# JavaFX FXML Custom Components

Build reusable JavaFX components that are authored once and then used as first-class FXML tags. **Always prefer small, focused components over monolithic FXML files.**

## When To Use

Use this skill when the task involves any of the following:

- Building a reusable component and using it directly in parent FXML as `<CustomComponent .../>`
- Writing or fixing custom component classes that back tag-based usage
- Mapping FXML tag attributes to Java setters/properties (for example `title="..."`, `enabled="true"`)
- Wiring `fx:root` + `FXMLLoader.setRoot(...)` + `setController(...)` correctly
- Troubleshooting custom tag errors such as class-not-found, cannot-set-property, or loader/controller conflicts
- Scene Builder custom component import issues
- JPMS (`module-info.java`) reflection access for FXML-backed components
- Breaking down large FXML screens into smaller, composable components

If the user is only creating one ordinary screen with one controller and no reusable component tags, this skill is usually unnecessary.

## Core Decision

Choose one primary pattern per component:

1. **Tag-based reusable component (primary)** -> create an FXML-backed class that can be used as `<MyComponent .../>`.
2. **Include-based composition (secondary)** -> use `<fx:include>` when the goal is nesting full subviews/controllers.
3. **Runtime-dynamic composition** -> use `new FXMLLoader(...)`, call `load()`, then `getController()`.

## Component Decomposition Rules (Critical)

**Never create monolithic FXML files.** Always break down complex screens into small, reusable components.

### When to Extract a Component

Extract a separate component when a section of FXML has **any** of these characteristics:

1. **Logical grouping**: A UI section represents a distinct concept (e.g., "Greeting", "Prayer Times", "Daily Hadith")
2. **Reusability potential**: The section could be reused in other screens or contexts
3. **Complexity threshold**: A section has 15+ lines of FXML or 5+ nested levels
4. **Independent state**: A section manages its own state or has its own event handlers
5. **Visual card/container**: A section is visually bounded (e.g., inside a card, panel, or bordered region)

### Component Size Limits

- **Target**: 20-40 lines per component FXML
- **Maximum**: 60 lines per component FXML (excluding imports)
- **Nesting depth**: Maximum 5 levels of nested elements
- **Children count**: Maximum 8 direct children per container

### Composition Over Monoliths

When building a screen:

1. Identify logical sections that should be separate components
2. Create small, focused components for each section
3. Compose them in a parent "screen" component that orchestrates layout
4. The parent component should only handle layout and coordinate child components

**Example: Good Decomposition**

```
HomeScreen (parent - layout only)
├── GreetingComponent (displays user greeting)
├── DailyHadithCardComponent (shows daily hadith)
├── PrayerTimesSection (contains prayer-related components)
│   ├── RemainingToPrayerComponent
│   ├── PrayerNotificationsToggleComponent
│   └── PrayerRowComponent (repeated for each prayer)
└── FavoritesCardComponent
```

**Example: Bad Monolith**

```
HomeScreen (single FXML with 200+ lines)
├── All greeting UI inline
├── All hadith UI inline
├── All prayer times UI inline
└── All favorites UI inline
```

## Primary Workflow: Tag-Based Components

When users ask for custom FXML components, default to this workflow:

1. **Decompose first**: Identify what should be separate components before writing any FXML
2. Create component classes that extend the same base type as `<fx:root type="...">` (for example `VBox`, `HBox`, `ScrollPane`, `BorderPane`)
3. In component FXML, use `<fx:root ...>` as root and do not declare `fx:controller` there
4. In the component constructor, instantiate `FXMLLoader`, then call `setRoot(this)` and `setController(this)` before `load()`
5. Expose tag attributes through JavaBean-friendly API (`setX(...)`, optionally `xProperty()`, `getX()`/`isX()`)
6. In parent FXML, import component classes and use them directly as tags
7. If the app is modular, open FXML-reflected packages to `javafx.fxml`

## Non-Negotiable Rules

1. For tag-based reusable components, use `<fx:root ...>` in the component FXML, not a concrete root tag.
2. For `fx:root` components, do not set `fx:controller` in that FXML. The component class sets itself as controller.
3. Do not mix `fx:controller` and `setController(...)` for the same FXML document.
4. Call `setRoot(...)` and `setController(...)` before `load()`.
5. Tag classes should provide a public no-arg constructor for normal FXML instantiation.
6. Attributes used in tag form (for example `<MyComponent title="..."/>`) must map to writable JavaBean properties on the component class.
7. `@FXML` annotate non-public injected fields and handler methods.
8. In modular apps, open controller/custom-component packages to `javafx.fxml`.
9. **ALWAYS decompose complex screens into small, reusable components.**
10. **NEVER create monolithic FXML files with 100+ lines of UI.**
11. **Parent/screen components should only handle layout, not business logic.**

## Anti-Patterns (Avoid These)

### ❌ Monolithic Screen FXML

```xml
<!-- BAD: 200+ line FXML with all UI inline -->
<VBox fx:controller="com.example.HomeController">
    <!-- 50 lines of greeting UI -->
    <!-- 80 lines of hadith UI -->
    <!-- 70 lines of prayer times UI -->
</VBox>
```

### ✅ Composed Screen FXML

```xml
<!-- GOOD: Clean composition with reusable components -->
<fx:root type="VBox" xmlns:fx="http://javafx.com/fxml">
    <GreetingComponent />
    <DailyHadithCardComponent />
    <PrayerTimesSection />
    <FavoritesCardComponent />
</fx:root>
```

### ❌ Deep Nesting

```xml
<!-- BAD: 8+ levels of nesting, hard to maintain -->
<VBox>
    <HBox>
        <VBox>
            <BorderPane>
                <VBox>
                    <HBox>
                        <VBox>
                            <!-- Where am I? -->
                        </VBox>
                    </HBox>
                </VBox>
            </BorderPane>
        </VBox>
    </HBox>
</VBox>
```

### ✅ Flat, Component-Based Structure

```xml
<!-- GOOD: Flat structure with meaningful components -->
<VBox>
    <UserProfileCard />
    <SettingsPanel />
    <ActionButtons />
</VBox>
```

## Implementation Workflow

1. **Decompose the screen**: Identify logical components before writing code
2. Determine if the user needs tag-based components, include-based composition, or dynamic loading
3. For tag-based components, follow `references/tag-components.md` first
4. For decomposition guidance, follow `references/component-decomposition.md`
5. Use `references/patterns.md` for canonical templates
6. Use `references/nested-controllers.md` only when includes/nested controllers are required
7. If Scene Builder is involved, apply `references/scene-builder.md`
8. If modules are involved, apply `references/module-info.md`
9. Run a troubleshooting pass using `references/troubleshooting.md` before finalizing

## Quick Wiring Checklist

- Parent FXML imports the component class and uses it as an uppercase class tag
- `fx:root` type matches the component class superclass
- Constructor load order is `setRoot` -> `setController` -> `load`
- Tag attributes map to available setters/properties on the component class
- Event handlers (`#method`) resolve on the effective controller
- No controller conflicts (`fx:controller` vs `setController`/`setControllerFactory`)
- **Each component is focused and under 60 lines of FXML**
- **Screen is composed of multiple small components, not one monolith**

## Output Expectations

When applying this skill, produce:

1. **Multiple small component FXML files** (not one large file)
2. Correct component FXML + component class wiring for tag-based usage
3. Parent FXML import + custom tag usage snippet showing composition
4. Attribute mapping guidance (what setter/property methods are required)
5. Minimal public API for component consumers
6. Include/nested-controller wiring only when the user explicitly needs include-based composition
7. Scene Builder and module guidance when relevant
8. **Explicit explanation of component boundaries** (why each component was extracted)

## Response Completeness Guardrails

When the request asks for troubleshooting/checklists, do not return a heading-only or partial answer.

Always include all of the following sections:

1. Numbered troubleshooting steps (at least 6 steps, ordered by likelihood/impact)
2. A short list of likely root causes
3. A clear recommendation on whether a workaround is primary or last resort

For Scene Builder troubleshooting specifically, explicitly state that `SceneBuilder.cfg` classpath edits are a legacy/last-resort fallback after modern Library Manager import paths and dependency checks.

Before finalizing a checklist-style response, run a quick self-check:

- Did I include numbered steps?
- Did I list probable causes?
- Did I include fallback guidance and scope?

## Reference Files

- `references/tag-components.md` - Primary guide for custom components represented as FXML tags
- `references/component-decomposition.md` - **How to break down monolithic FXML into small components**
- `references/patterns.md` - Canonical templates for tag-based `fx:root`, includes, dynamic loading, and DI factories
- `references/nested-controllers.md` - Exact include-controller naming and parent/child communication patterns
- `references/scene-builder.md` - Modern Gluon workflow for custom control imports plus dependency/classpath handling
- `references/module-info.md` - JPMS rules for FXML reflection access
- `references/troubleshooting.md` - Symptom -> cause -> fix matrix
