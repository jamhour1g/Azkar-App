plugins {
    `java-library`
    id("azkar-common")
}

dependencies {
    // Database
    implementation(libs.org.xerial.sqlite.jdbc)
    implementation(libs.org.hibernate.orm.hibernate.core)
    implementation(libs.org.hibernate.orm.hibernate.community.dialects)
    implementation(libs.org.hibernate.orm.hibernate.hikaricp)
    implementation(libs.com.zaxxer.hikari)
    implementation(libs.org.flywaydb.flyway.core)

    // JPA
    compileOnly(libs.jakarta.persistence.jakarta.persistence.api)
    compileOnly(libs.jakarta.inject.jakarta.inject.api)
    compileOnly(libs.jakarta.enterprise.jakarta.enterprise.cdi.api)
    compileOnly(libs.jakarta.transaction.jakarta.transaction.api)

    implementation(project(":utils"))
    implementation(project(":domain"))

    // TODO: find out a way to make it work with nullaway and spotbugs.
//    annotationProcessor("org.hibernate.orm:hibernate-processor:${libs.versions.org.hibernate.get()}")

}