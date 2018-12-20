resolvers += Resolver.url("bintray-sbt-plugins", url("http://dl.bintray.com/sbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.15")
// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.9")

// Scala formatting: "sbt scalafmt"
// https://olafurpg.github.io/scalafmt
//addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "0.3.1")
