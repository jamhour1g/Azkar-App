package com.azkar.components.library;

import com.azkar.components.library.model.LibrarySnapshot;
import com.azkar.components.library.service.LibraryDataService;
import com.azkar.data.config.DomainServiceContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javafx.application.Platform;

public final class LibraryDataCoordinator {

    @FunctionalInterface
    public interface ContextOperation {
        void run(DomainServiceContext context);
    }

    @FunctionalInterface
    public interface SnapshotConsumer {
        void accept(LibrarySnapshot snapshot);
    }

    private final LibraryDataService dataService;
    private final ExecutorService dataExecutor;
    private final BooleanSupplier closedSupplier;
    private final AtomicLong loadVersion = new AtomicLong();

    private CompletableFuture<?> inFlightLoad;
    private CompletableFuture<?> inFlightMutation;

    LibraryDataCoordinator(LibraryDataService dataService, ExecutorService dataExecutor, BooleanSupplier closedSupplier) {
        this.dataService = dataService;
        this.dataExecutor = dataExecutor;
        this.closedSupplier = closedSupplier;
    }

    void loadAsync(Runnable beforeLoad, SnapshotConsumer onSuccess, Consumer<Throwable> onFailure) {
        if (closedSupplier.getAsBoolean()) {
            return;
        }

        long requestedVersion = loadVersion.incrementAndGet();
        beforeLoad.run();

        try {
            CompletableFuture<LibrarySnapshot> loadTask = CompletableFuture.supplyAsync(dataService::fetchSnapshot, dataExecutor);
            inFlightLoad = loadTask.whenComplete((snapshot, throwable) -> Platform.runLater(() -> {
                if (closedSupplier.getAsBoolean() || requestedVersion != loadVersion.get()) {
                    return;
                }

                Throwable failure = resolveFailure(throwable);
                if (failure != null) {
                    onFailure.accept(failure);
                    return;
                }

                onSuccess.accept(snapshot);
            }));
        } catch (RejectedExecutionException rejectedExecutionException) {
            if (!closedSupplier.getAsBoolean()) {
                onFailure.accept(rejectedExecutionException);
            }
        }
    }

    void runMutation(
            ContextOperation operation,
            Runnable beforeMutation,
            Runnable onSuccess,
            Consumer<Throwable> onFailure) {
        if (closedSupplier.getAsBoolean()) {
            return;
        }

        beforeMutation.run();

        try {
            CompletableFuture<Void> mutationTask = CompletableFuture.runAsync(
                    () -> {
                        try (var context = new DomainServiceContext()) {
                            operation.run(context);
                        }
                    },
                    dataExecutor);
            inFlightMutation = mutationTask.whenComplete((result, throwable) -> Platform.runLater(() -> {
                if (closedSupplier.getAsBoolean()) {
                    return;
                }

                Throwable failure = resolveFailure(throwable);
                if (failure != null) {
                    onFailure.accept(failure);
                    return;
                }

                onSuccess.run();
            }));
        } catch (RejectedExecutionException rejectedExecutionException) {
            if (!closedSupplier.getAsBoolean()) {
                onFailure.accept(rejectedExecutionException);
            }
        }
    }

    void shutdownNow() {
        loadVersion.incrementAndGet();

        if (inFlightLoad != null) {
            inFlightLoad.cancel(true);
        }
        if (inFlightMutation != null) {
            inFlightMutation.cancel(true);
        }

        dataExecutor.shutdownNow();
    }

    private static Throwable resolveFailure(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }
}
