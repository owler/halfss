name := "example-" + baseDirectory.value.getName

version := "1.0.0"

scalaVersion := "2.12.2"
val camelVersion = "2.19.1"
val akkaVersion = "2.5.4"
enablePlugins(UniversalPlugin)

makeBashScripts := Seq.empty

makeBatScripts := Seq.empty

fork := true

scalacOptions ++= Seq("-deprecation")
//unmanagedSourceDirectories in Compile += baseDirectory.value / "src" / "main" / "scala"
//unmanagedBase <<= baseDirectory { base => base / "lib" }

// resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

// resolvers += "Nexus" at "http://10.128.212.88:8481/nexus-webapp/service/local/repositories/essreleases/content"
libraryDependencies += guice
libraryDependencies += "junit" % "junit" % "4.12" % "test"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.3"

libraryDependencies += "org.joda" % "joda-convert" % "1.8"

libraryDependencies += "net.logstash.logback" % "logstash-logback-encoder" % "4.9"

libraryDependencies += "com.netaporter" %% "scala-uri" % "0.4.16"
libraryDependencies += "net.codingwell" %% "scala-guice" % "4.1.0"

//libraryDependencies += "com.typesafe.play" %% "play" % "2.6.3"
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.1" % Test
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.20.0"

import java.util.UUID

import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._

//mappings in Universal ++= contentOf("src/main/resources")

val excludeFileRegx = """(.*?)\.(properties|props|conf|dsl|txt|xml|sh)$""".r
mappings in(Compile, packageBin) := {
  (mappings in(Compile, packageBin)).value filter {
    case (file, name) => !excludeFileRegx.pattern.matcher(file.getName).matches
  }
}

lazy val pack = TaskKey[File]("pack")
lazy val zipD = TaskKey[File]("zip")
lazy val zipT = TaskKey[File]("tar")

packageOptions in(Compile, packageBin) += Package.ManifestAttributes(
  java.util.jar.Attributes.Name.CLASS_PATH ->
    ((externalDependencyClasspath in Compile).value map ("lib\\"+_.data.name) mkString(" "))
)


/*
pack <<= (clean, update, packageBin in Compile, stage) map {
  (cl, uReport, jar, dist) => {
    val af: ArtifactFilter = (a: Artifact) => a.`type` != "source" && a.`type` != "javadoc" && a.`type` != "javadocs"
    uReport.select(configuration = Set("runtime"), artifact = af) foreach {
      srcPath =>
        val destPath = dist / "lib" / srcPath.getName
        IO.copyFile(srcPath, destPath, preserveLastModified = true)
    }

    IO.copyFile(jar, dist / "lib" / jar.getName)
    dist
  }
}
*/

import java.util.UUID

import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._

pack := stage.value

mappings in Universal := {
  val j = (packageBin in Compile).value
  (mappings in Universal).value :+ (j -> ("lib/" + j.getName))
}

mappings in Universal := {
  val runtimeLibs = update.value.select(configuration = configurationFilter("runtime")) map {
    f: File => f -> ("lib/" + f.name)
  }
  runtimeLibs ++: (mappings in Universal).value
}

/*
mappings in Universal <++= (update) map {
  r: UpdateReport => r.select(configuration = Set("runtime")) map {
    f: File => f -> ("lib/" + f.name)
  }
}
*/


mappings in Universal := {
  val sh = (mappings in Universal).value filter {
    case (file, name) => name.endsWith(".sh")
  }
  val filtered = (mappings in Universal).value filter {
    case (file, name) => !name.endsWith(".sh")
  }
  val rsh = sh map {
    case (f, n) => dos2unix(taskTemporaryDirectory.value, f) -> n
  }
  filtered ++ rsh
}

def dos2unix(tempDir: File, f: File): File = {
  val tempFile = tempDir / UUID.randomUUID.toString
  IO.write(tempFile, IO.read(f).replaceAll("\r\n", "\n"))
  tempFile
}


universalArchiveOptions in(Universal, packageZipTarball) := Seq("a", "-ttar")
zipD := (packageBin in Universal).value
zipT := (packageZipTarball in Universal).value

lazy val root = (project in file(".")).enablePlugins(PlayScala, PlayNettyServer).disablePlugins(PlayLayoutPlugin, PlayAkkaHttpServer)
PlayKeys.playMonitoredFiles ++= (sourceDirectories in (Compile, TwirlKeys.compileTemplates)).value