# Nested Controllers And Cross-Controller Wiring

This file covers how to correctly reference controllers inside other controllers when using custom views.

Use this only when the task is include-based composition (`<fx:include>`). For tag-based custom components (`<MyComponent .../>`), start with `references/tag-components.md`.

## Include Injection Convention (Critical)

For this include:

```xml
<fx:include fx:id="profileCard" source="ProfileCard.fxml"/>
```

the parent controller can inject:

- `profileCard` (included root object)
- `profileCardController` (included controller)

### Parent Controller Example

```java
public class AccountPageController {
    @FXML
    private VBox profileCard;

    @FXML
    private ProfileCardController profileCardController;

    @FXML
    private void initialize() {
        profileCardController.setDisplayName("Ada Lovelace");
    }
}
```

The controller field name must be exactly `<fx:id>Controller`.

## Multiple Includes Of Same Child Type

If you include the same FXML multiple times, each include must have a unique `fx:id`, and each controller field follows the same naming rule.

```xml
<fx:include fx:id="shippingAddress" source="AddressCard.fxml"/>
<fx:include fx:id="billingAddress" source="AddressCard.fxml"/>
```

```java
public class CheckoutController {
    @FXML private VBox shippingAddress;
    @FXML private AddressCardController shippingAddressController;

    @FXML private VBox billingAddress;
    @FXML private AddressCardController billingAddressController;
}
```

## Parent -> Child Communication (Recommended)

Prefer explicit child APIs.

```java
profileCardController.setUser(user);
profileCardController.setEditable(false);
```

Good patterns:

- Expose minimal methods/properties on child controller.
- Parent orchestrates sibling coordination.
- Keep child controller reusable and context-light.

## Child -> Parent Communication (Recommended)

Use callback or observable-based contracts, not hard parent references.

```java
public class ProfileCardController {
    private Consumer<User> onUserEdited;

    public void setOnUserEdited(Consumer<User> onUserEdited) {
        this.onUserEdited = onUserEdited;
    }

    @FXML
    private void save() {
        if (onUserEdited != null) {
            onUserEdited.accept(buildUserFromFields());
        }
    }
}
```

```java
profileCardController.setOnUserEdited(updated -> viewModel.updateUser(updated));
```

Avoid:

- `childController.setParentController(this)` as the default approach.
- Circular dependencies between parent and child controllers.

## Detached Child Graph (`fx:define`)

Use `fx:define` when the child should be referenced but not attached into parent scene graph.

```xml
<fx:define>
    <fx:include fx:id="preferencesDialog" source="PreferencesDialog.fxml"/>
</fx:define>
```

Then use `preferencesDialogController` to prepare/show it.

## Dynamic Loading Pattern

For runtime-selected children:

```java
FXMLLoader loader = new FXMLLoader(getClass().getResource("AuditPane.fxml"));
Node pane = loader.load();
AuditPaneController controller = loader.getController();
controller.bind(viewModel.auditItemsProperty());
```

## High-Value Pitfalls

1. `childController` is null -> field name mismatch (must match `<fx:id>Controller`).
2. `childController` is null -> child FXML has no effective controller.
3. Runtime failure in modules -> package not opened to `javafx.fxml`.
4. Calling child APIs too early -> do it in `initialize()` or after `load()`.
5. Parent and child tightly coupled -> switch to callback or interface contract.
