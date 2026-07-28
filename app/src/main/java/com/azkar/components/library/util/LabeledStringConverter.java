package com.azkar.components.library.util;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.util.StringConverter;

public final class LabeledStringConverter<T> extends StringConverter<T> {

    private final Function<T, String> toLabelFunction;
    private final Supplier<List<T>> itemsSupplier;
    private final T fallback;

    public LabeledStringConverter(
            Function<T, String> toLabelFunction, Supplier<List<T>> itemsSupplier, T fallback) {
        this.toLabelFunction = toLabelFunction;
        this.itemsSupplier = itemsSupplier;
        this.fallback = fallback;
    }

    public LabeledStringConverter(Function<T, String> toLabelFunction, Supplier<List<T>> itemsSupplier) {
        this(toLabelFunction, itemsSupplier, null);
    }

    @Override
    public String toString(T item) {
        return item == null ? "" : toLabelFunction.apply(item);
    }

    @Override
    public T fromString(String value) {
        return itemsSupplier.get().stream()
                .filter(item -> toLabelFunction.apply(item).equals(value))
                .findFirst()
                .orElse(fallback);
    }
}
