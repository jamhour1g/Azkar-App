module Azkar {

    requires java.sql;
    requires javafx.fxml;
    requires java.logging;
    requires java.prefs;
    requires unirest.java.core;
    requires java.desktop;
    requires com.install4j.runtime;
    requires javafx.graphics;
    requires adhan;
    requires javafx.controls;
    requires com.jfoenix;
    requires javafx.media;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires org.controlsfx.controls;
    requires javafx.web;
    requires com.fasterxml.jackson.databind;
    requires flyway.core;
    requires sentry;
    requires ummalqura.calendar;
    requires jgforms;
    requires org.xerial.sqlitejdbc;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome6;
    requires jakarta.persistence;

    exports com.bayoumi to javafx.graphics;
    exports com.bayoumi.controllers.onboarding to javafx.fxml;
    exports com.bayoumi.controllers.home to javafx.fxml;
    exports com.bayoumi.controllers.home.prayertimes to javafx.fxml;
    exports com.bayoumi.controllers.components.audio to javafx.fxml;
    exports com.bayoumi.controllers.components to javafx.fxml;

    opens com.bayoumi.controllers.home to javafx.fxml;
    opens com.bayoumi.controllers.onboarding to javafx.fxml;
    opens com.bayoumi.controllers.home.prayertimes to javafx.fxml;
    opens com.bayoumi.controllers.components.audio to javafx.fxml;
    opens com.bayoumi.controllers.components to javafx.fxml;
    opens db.migration;


}