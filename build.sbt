import _root_.caliban.tools.Codegen

inThisBuild(
  Seq(
    scalaVersion   := "3.6.2",
    version        := "0.1.0-SNAPSHOT",
    organization   := "com.avantstay",
    scalafmtConfig := file(".scalafmt.conf"),
  )
)
//unmanagedBase := baseDirectory.value / "lib"

lazy val core = (project in file("core"))
  .enablePlugins(CalibanPlugin)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "org.typelevel"         %% "cats-effect"           % "3.6.3",
      "org.tpolecat"          %% "doobie-core"           % "1.0.0-RC10",
      "org.tpolecat"          %% "doobie-postgres"       % "1.0.0-RC10",
      "org.tpolecat"          %% "doobie-hikari"         % "1.0.0-RC10",
      "org.tpolecat"          %% "doobie-postgres-circe" % "1.0.0-RC10",
      "com.github.fd4s"       %% "fs2-kafka"             % "3.9.0",
      "is.cir"                %% "ciris"                 % "3.9.0",
      "io.circe"              %% "circe-core"            % "0.14.14",
      "io.circe"              %% "circe-generic"         % "0.14.14",
      "io.circe"              %% "circe-parser"          % "0.14.14",
      "org.typelevel"         %% "log4cats-slf4j"        % "2.7.1",
      "com.github.ghostdogpr" %% "caliban"               % "2.11.1",
      "com.github.ghostdogpr" %% "caliban-http4s"        % "2.11.1",
      "com.github.ghostdogpr" %% "caliban-tapir"         % "2.11.1",
      "com.github.ghostdogpr" %% "caliban-cats"          % "2.11.1",
    ),
    Compile / caliban / calibanSettings ++= Seq(
      calibanSetting(file("core/src/main/graphql/schema.graphql"))(
        _.genType(Codegen.GenType.Schema)
          .clientName("Generated")
          .abstractEffectType(true)
          .packageName("com.booking.core.graphql")
      )
    ),
  )

lazy val api = (project in file("api"))
  .settings(
    name                     := "api",
    libraryDependencies ++= Seq(
      "org.typelevel"         %% "cats-effect"                     % "3.6.3",
      "org.typelevel"         %% "cats-core"                       % "2.13.0",
      "com.github.ghostdogpr" %% "caliban"                         % "2.11.1",
      "com.github.ghostdogpr" %% "caliban-http4s"                  % "2.11.1",
      "com.github.ghostdogpr" %% "caliban-tapir"                   % "2.11.1",
      "com.github.ghostdogpr" %% "caliban-cats"                    % "2.11.1",
      "is.cir"                %% "ciris"                           % "3.9.0",
      "org.http4s"            %% "http4s-ember-server"             % "0.23.30",
      "org.http4s"            %% "http4s-dsl"                      % "0.23.30",
      "org.tpolecat"          %% "doobie-hikari"                   % "1.0.0-RC10",
      "org.typelevel"         %% "log4cats-slf4j"                  % "2.7.1",
      "ch.qos.logback"         % "logback-classic"                 % "1.5.18",
      "org.flywaydb"           % "flyway-core"                     % "11.11.1",
      "org.flywaydb"           % "flyway-database-postgresql"      % "11.11.1",
      "org.postgresql"         % "postgresql"                      % "42.7.7",
      "com.github.fd4s"       %% "fs2-kafka"                       % "3.9.0",
      "com.disneystreaming"   %% "weaver-cats"                     % "0.8.4"      % Test,
      "com.disneystreaming"   %% "weaver-scalacheck"               % "0.8.4"      % Test,
      "com.disneystreaming"   %% "weaver-discipline"               % "0.8.4"      % Test,
      "org.scalacheck"        %% "scalacheck"                      % "1.18.1"     % Test,
      "org.typelevel"         %% "cats-laws"                       % "2.13.0"     % Test,
      "dev.optics"            %% "monocle-law"                     % "3.3.0"      % Test,
      // test DB integration
      "org.tpolecat"          %% "doobie-core"                     % "1.0.0-RC10" % Test,
      "org.tpolecat"          %% "doobie-postgres"                 % "1.0.0-RC10" % Test,
      "org.tpolecat"          %% "doobie-hikari"                   % "1.0.0-RC10" % Test,
      "org.flywaydb"           % "flyway-core"                     % "11.11.1"    % Test,
      "com.dimafeng"          %% "testcontainers-scala"            % "0.43.0"     % Test,
      "com.dimafeng"          %% "testcontainers-scala-postgresql" % "0.43.0"     % Test,
      "io.circe"              %% "circe-core"                      % "0.14.14"    % Test,
      "io.circe"              %% "circe-parser"                    % "0.14.14"    % Test,
    ),
    // --- test framework & runtime knobs ---
    Test / testFrameworks    := Seq(new TestFramework("weaver.framework.CatsEffect")),
    Test / fork              := true, // Testcontainers
    Test / parallelExecution := false // fix flakiness issues with PG container
  )
  .dependsOn(core)

lazy val consumer = (project in file("consumer"))
  .settings(
    name := "consumer",
    libraryDependencies ++= Seq(
      "org.typelevel"   %% "cats-effect"     % "3.6.3",
      "com.github.fd4s" %% "fs2-kafka"       % "3.9.0",
      "org.tpolecat"    %% "doobie-hikari"   % "1.0.0-RC10",
      "org.typelevel"   %% "log4cats-slf4j"  % "2.7.1",
      "org.tpolecat"    %% "doobie-core"     % "1.0.0-RC10",
      "org.tpolecat"    %% "doobie-hikari"   % "1.0.0-RC10",
      "org.tpolecat"    %% "doobie-postgres" % "1.0.0-RC10",
      "io.circe"        %% "circe-core"      % "0.14.14",
      "io.circe"        %% "circe-parser"    % "0.14.14",
      "is.cir"          %% "ciris"           % "3.9.0",
      "ch.qos.logback"   % "logback-classic" % "1.5.18"
    )
  )
  .dependsOn(core)
