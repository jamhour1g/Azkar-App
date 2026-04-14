# JPMS Module Rules For FXML

FXML uses reflection for controller field/method injection and event handler access.

In modular applications, ensure required packages are opened to `javafx.fxml`.

## Minimal Example

```java
module com.example.app {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.example.app.ui to javafx.fxml;
    opens com.example.app.controls to javafx.fxml;

    exports com.example.app;
}
```

## Practical Rules

1. Add `requires javafx.fxml` when using FXML loader/controller features.
2. `opens` packages containing:
   - Controllers referenced from `fx:controller`
   - FXML-backed custom control classes using `@FXML` members
3. If using multiple UI packages, open each relevant package.
4. `exports` is not a replacement for `opens` in reflective injection scenarios.

## Typical Symptom -> Cause

- `@FXML` field not injected / access errors at runtime -> package not opened.
- Handler resolution fails despite method existing -> controller package not opened.

When diagnosing module issues, verify package names in `module-info.java` exactly match runtime controller package locations.
