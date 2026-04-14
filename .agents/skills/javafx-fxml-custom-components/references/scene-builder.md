# Scene Builder With Custom FXML Controls

Use this guide when custom controls must be visible and usable in Scene Builder.

## Modern Workflow (Gluon Scene Builder)

Prefer Library Manager workflows over manual hacks.

1. Open Scene Builder Library Manager.
2. Import control from one of these sources:
   - **Add root folder with `*.class` files** (best for active development loop)
   - **Add Library/FXML from file system** (jar/fxml import)
   - **Search repositories / add from repository** (Maven/Nexus artifacts)
3. Select controls to import into the Custom section.
4. If import fails or looks incomplete, run custom control analysis from Library Manager tooling.

## Required Shape For `fx:root` Controls

For FXML-backed reusable controls:

- Root element in control FXML must be `<fx:root ...>`.
- Control FXML should not declare `fx:controller`.
- Java class extends the same type declared by `<fx:root type="...">`.
- Constructor loads FXML with `setRoot(this)` and `setController(this)` before `load()`.

If these are violated, Scene Builder often cannot instantiate/import the control reliably.

## Dependency Handling

Scene Builder must resolve the control class and its dependencies.

Recommended order:

1. Import from class output folder while iterating.
2. Ensure dependent jars are also available (repository import is easiest when published).
3. For packaged artifacts, import jar that contains control classes and required runtime deps in classpath.

Symptoms of dependency issues:

- Control does not appear in import candidates.
- Control appears but fails to render or loads as empty.
- Import works until a third-party control inside your custom control is used.

## If Control Does Not Show In Custom Palette

Check these first:

1. Control class visibility: public/protected, non-abstract.
2. FXML resource path inside class loader context is correct.
3. `fx:root` + no `fx:controller` for reusable control pattern.
4. Constructor wiring uses `setRoot`, `setController`, then `load()`.
5. Dependencies are resolvable by Scene Builder.

## If Control Appears But Children Are Missing

Most likely causes:

- Control FXML failed to load in constructor (exception hidden/rewrapped).
- Wrong resource path for child FXML/resources.
- `@FXML` fields/handlers mismatched.

Fix by logging/propagating load errors and validating fx:id mappings.

## Required Output Structure For Troubleshooting Answers

When responding to users who ask for Scene Builder troubleshooting:

1. Provide a numbered checklist (minimum 6 steps).
2. Provide a separate "Likely root causes" section.
3. Provide a separate "When SceneBuilder.cfg is appropriate" section.
4. In that section, explicitly mark `SceneBuilder.cfg` classpath edits as legacy/last-resort.

If any of these sections are missing, the response is incomplete.

## Legacy Fallback: `SceneBuilder.cfg` Classpath Hack

Older guidance suggests editing `SceneBuilder.cfg` `app.classpath=...`.

Use this only as a last resort when modern Library Manager paths are insufficient.

Caveats:

- Environment-specific and brittle across upgrades.
- Harder to share across team members.
- Can still leave controls outside normal Custom palette workflows.

Prefer first-class Library Manager import modes whenever possible.
