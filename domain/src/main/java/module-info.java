@org.jspecify.annotations.NullMarked module com.azkar.domain {
    requires static org.jspecify;
    requires static lombok;

    exports com.azkar.domain.model;
    exports com.azkar.domain.model.impl;
    exports com.azkar.domain.repo;
    exports com.azkar.domain.exception;
    exports com.azkar.domain.service;
}
