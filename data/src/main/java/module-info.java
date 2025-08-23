@org.jspecify.annotations.NullMarked
module com.azkar.data {
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires jakarta.persistence;
    requires org.hibernate.orm.core;
    requires jakarta.transaction;
    requires com.azkar.utils;
    requires org.flywaydb.core;
    
    // Compile-time only
    requires static lombok;
    requires static org.jspecify;

    opens com.azkar.data.entity to
            org.hibernate.orm.core;
    opens com.azkar.data.converter to
            org.hibernate.orm.core;

    exports com.azkar.data.model;
    exports com.azkar.config;
    exports com.azkar.data.entity;
    exports com.azkar.data.repo;
}
