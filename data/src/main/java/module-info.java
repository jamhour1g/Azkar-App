module com.azkar.data {
    requires java.sql;
    requires java.logging;
    requires java.prefs;
    requires java.desktop;
    requires org.xerial.sqlitejdbc;
    requires jakarta.persistence;
    requires org.jspecify;
    requires org.hibernate.orm.core;
    requires jakarta.transaction;
    requires jakarta.inject;
    requires jakarta.cdi;
    requires static lombok;
    requires java.rmi;
    requires Azkar;
    requires flyway.core;

    opens com.azkar.data.entity to
            org.hibernate.orm.core;

    exports com.azkar.data.converter to
            org.hibernate.orm.core;
    exports com.azkar.data.model;
}
