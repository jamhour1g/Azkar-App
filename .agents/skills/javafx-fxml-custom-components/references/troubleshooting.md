# Troubleshooting Matrix

Use this quick matrix before finalizing any component wiring change.

## 1) "Controller value already specified"

- Cause: both `fx:controller` and `setController(...)` used for same load.
- Fix: keep one controller strategy only.

## 2) "Root hasn't been set" / `fx:root` load errors

- Cause: FXML uses `<fx:root>` but caller did not call `setRoot(...)` before `load()`.
- Fix: call `setRoot` and ensure root type matches class hierarchy.

## 3) `@FXML` fields are null

- Causes:
  - fx:id mismatch
  - field visibility without `@FXML`
  - controller not effective for that FXML
  - module package not opened to `javafx.fxml`
- Fix:
  - align fx:id exactly
  - add `@FXML` for non-public members
  - verify controller strategy
  - add proper `opens` entries in `module-info.java`

## 4) Handler method not found (`#method`)

- Cause: method absent/misspelled, wrong controller, or inaccessible due to visibility/module access.
- Fix: ensure method exists on effective controller, annotate `@FXML` if needed, and open package in JPMS.

## 5) Included controller is null

- Causes:
  - parent field name not `<fx:id>Controller`
  - child FXML has no effective controller
- Fix:
  - rename field to exact convention
  - ensure child has `fx:controller` or is programmatically assigned.

## 6) Works at runtime but not in Scene Builder

- Causes:
  - Scene Builder cannot resolve class/dependencies
  - invalid custom control pattern for import
- Fix:
  - import class output folder or jar with dependencies
  - verify `fx:root` control prerequisites
  - use Scene Builder analyzer and repository import options.

## 7) Custom control appears but inner nodes are missing in Scene Builder

- Causes:
  - control constructor FXML load failure
  - bad resource path
  - dependent libraries unresolved in Scene Builder environment
- Fix:
  - validate constructor load path
  - surface exceptions during load
  - ensure dependent libraries are available to Scene Builder.

## 8) Cannot retrieve controller after loading

- Cause: using static `FXMLLoader.load(...)` and expecting controller later.
- Fix: instantiate `FXMLLoader`, call `load()`, then `getController()` on the same loader.

## 9) Circular parent-child controller references

- Cause: parent and child directly own each other.
- Fix: make parent own orchestration, child expose callback/property API.

## 10) Multiple includes of same child FXML miswired

- Cause: duplicate/incorrect fx:id naming and controller field naming.
- Fix: unique fx:id per include and matching `<fx:id>Controller` fields for each include.

## 11) "Cannot set property 'x'" on custom component tag

- Cause: parent FXML uses `<MyComponent x="..."/>` but component class does not expose writable bean API for `x`.
- Fix: add matching setter/property API (for example `setX(...)`, optional `xProperty()` and getter), and ensure value type is coercible.

## 12) Custom tag class not found in parent FXML

- Causes:
  - missing `<?import com.example.components.MyComponent?>`
  - incorrect package/class name
  - class not on runtime classpath/module path
- Fix:
  - add/fix import processing instruction
  - verify package name and class visibility
  - verify runtime packaging/module path
