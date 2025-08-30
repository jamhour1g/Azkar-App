@org.jspecify.annotations.NullMarked module com.azkar.data {
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires jakarta.persistence;
    requires jakarta.transaction;
    requires org.hibernate.orm.core;
    requires org.flywaydb.core;
    requires com.azkar.utils;
    requires com.azkar.domain;

    // Compile-time only
    requires static lombok;
    requires static org.jspecify;

    opens com.azkar.data.converter to org.hibernate.orm.core;
    opens com.azkar.data.entity to org.hibernate.orm.core;
    opens com.azkar.data.entity.view to org.hibernate.orm.core;

    exports com.azkar.data.repo.jpa;
}
