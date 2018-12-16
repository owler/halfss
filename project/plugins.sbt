resolvers += Resolver.url("bintray-sbt-plugins", url("http://dl.bintray.com/sbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.0-M8")
// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.3")

// Scala formatting: "sbt scalafmt"
// https://olafurpg.github.io/scalafmt
//addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "0.3.1")
