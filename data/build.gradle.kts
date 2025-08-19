plugins {
    id("azkar-common")
}

dependencies {
    // Database
    implementation(libs.org.xerial.sqlite.jdbc)
    implementation(libs.org.hibernate.orm.hibernate.core)
    implementation(libs.org.hibernate.orm.hibernate.community.dialects)
    implementation(libs.org.flywaydb.flyway.core)

    // JPA
    compileOnly(libs.jakarta.persistence.jakarta.persistence.api)
    compileOnly(libs.jakarta.inject.jakarta.inject.api)
    compileOnly(libs.jakarta.enterprise.jakarta.enterprise.cdi.api)
    compileOnly(libs.jakarta.transaction.jakarta.transaction.api)

}