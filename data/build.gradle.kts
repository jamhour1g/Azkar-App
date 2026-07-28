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
  implementation(libs.com.google.code.gson.gson)
  implementation(libs.org.jboss.logging.jboss.logging)
  implementation(libs.jakarta.xml.bind.jakarta.xml.bind.api)
  implementation(libs.org.glassfish.jaxb.jaxb.runtime)
  implementation(libs.com.fasterxml.classmate.classmate)
  implementation(libs.net.bytebuddy.byte.buddy)

  // Jakarta Data
  implementation(libs.jakarta.data.jakarta.data.api)

  // JPA
  implementation(libs.jakarta.persistence.jakarta.persistence.api)
  implementation(libs.jakarta.inject.jakarta.inject.api)
  implementation(libs.jakarta.enterprise.jakarta.enterprise.cdi.api)
  implementation(libs.jakarta.transaction.jakarta.transaction.api)

  // Hibernate-generated Jakarta Data repository impls reference CDI types at runtime
  testRuntimeOnly(libs.jakarta.enterprise.jakarta.enterprise.cdi.api)

  implementation(project(":domain"))

  // Hibernate annotation processor — generates Jakarta Data repository implementations
  annotationProcessor(libs.org.hibernate.orm.hibernate.processor)
}
