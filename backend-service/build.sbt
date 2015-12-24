name := "backend-service"

version := "1.0-SNAPSHOT"

resolvers += "jboss-releases" at "https://repository.jboss.org/nexus/content/repositories/releases"

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
  cache,
  "mysql" % "mysql-connector-java" % "5.1.22",
  "org.springframework" % "spring-context" % "4.1.1.RELEASE",
  "org.springframework" % "spring-jdbc" % "4.1.1.RELEASE",
  "org.mockito" % "mockito-core" % "1.9.5",
  "org.quartz-scheduler" % "quartz" % "2.2.1",
  "org.quartz-scheduler" % "quartz-jobs" % "2.2.1",
  "org.slf4j" % "slf4j-api" % "1.6.6",
  "org.jasypt" % "jasypt" % "1.9.2",
  "org.drools" % "drools-core" % "6.3.0.Final",
  "org.drools" % "drools-compiler" % "6.3.0.Final",
  "javax.mail" % "javax.mail-api" % "1.5.5",
  "com.sun.mail" % "javax.mail" % "1.5.5"
)

play.Project.playJavaSettings
