@org.jspecify.annotations.NullMarked
module com.azkar.data {
    requires jakarta.cdi;
    requires com.google.gson;
    requires org.jboss.logging;
    requires jakarta.xml.bind;
    requires com.fasterxml.classmate;
    requires com.zaxxer.hikari;
    requires net.bytebuddy;
    requires java.sql;
    requires com.h2database;
    requires jakarta.persistence;
    requires jakarta.transaction;
    requires jakarta.data;
    requires org.hibernate.orm.core;
    requires org.flywaydb.core;
    requires org.slf4j;
    requires org.glassfish.jaxb.runtime;
    requires com.azkar.domain;

    // Compile-time only
    requires static lombok;
    requires static org.jspecify;

    opens com.azkar.data.entity to
            org.hibernate.orm.core;
    opens com.azkar.data.repo.jpa to
            org.hibernate.orm.core;

    exports com.azkar.data.config;
    exports com.azkar.data.repo.jpa;
    exports com.azkar.data.transaction;
}
