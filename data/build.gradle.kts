plugins {
    `java-library`
    id("azkar-common")
}

dependencies {
    // Database
    implementation(libs.com.h2database.h2)
    implementation(libs.org.hibernate.orm.hibernate.core)
    implementation(libs.org.hibernate.orm.hibernate.hikaricp)
    implementation(libs.com.zaxxer.hikari)
    implementation(libs.org.flywaydb.flyway.core)

    // Jakarta Data
    implementation(libs.jakarta.data.jakarta.data.api)

    // JPA
    compileOnly(libs.jakarta.persistence.jakarta.persistence.api)
    compileOnly(libs.jakarta.inject.jakarta.inject.api)
    compileOnly(libs.jakarta.enterprise.jakarta.enterprise.cdi.api)
    compileOnly(libs.jakarta.transaction.jakarta.transaction.api)

    implementation(project(":utils"))
    implementation(project(":domain"))

    // Hibernate annotation processor — generates Jakarta Data repository implementations
    annotationProcessor(libs.org.hibernate.orm.hibernate.processor)

}
