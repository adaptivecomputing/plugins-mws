This multi-project build contains plugin projects to be used as reference implementations of the plugin
framework in Adaptive Computing's Moab Web Services.  Some of these plugins are bundled and used in Moab Web Services.
This project utilizes the [Moab SDK Gradle Plugin|http://github.com/adaptivecomputing/plugins-gradle/tree/master/moab-sdk].
More information, including references to all available tasks, may be found at that link.

# Introduction to the Moab SDK

The Moab SDK (which the moab-sdk Gradle plugin enables) is utilized internally by
[Adaptive Computing|http://adaptivecomputing.com] and in this open-source project.  This project may be referred to
as an example of how to organize and build plugins for Moab Web Services.  A few notes may help to introduce how
Gradle and the SDK work and how a user interacts with each.

* Gradle is a build system based on Groovy, just as Grails is a web framework based on Spring and Groovy
* Build properties and tasks are controlled through the build.gradle (and included plugins) and gradle.properties file in the top-level directory (root project), and are overridden by the build.gradle and gradle.properties in each sub-project's directory (ie native). The gradle.properties may also be overridden globally by placing the same file in ~/.gradle/.
* The gradlew wrapper script automatically downloads and uses the latest gradle version available - **you do not need to install gradle to develop MWS plugins if you are using a repository that is already configured**.
* The SDK acts on MWS plugin projects, which are actually gradle subprojects of a root gradle build.  Each project or module is represented by a sub-directory, such as native, reports, etc.
* Tasks may be run as follows (examples given for the native project located in the plugins-mws project described below):
```
./gradlew native:build # compiles, tests, and creates a JAR in native/build/libs for the native module (plugin project)
./gradlew native:jar # compiles and creates a JAR (without testing) in native/build/libs for the native module (plugin project)
./gradlew jar # compiles and creates a JAR (without testing) in each project's build directory
cd native && ../gradlew jar # compiles and creates a JAR (without testing) for the native project only - the gradle command is aware of which directory you are in
```

# Quickstart

```
git clone https://github.com/adaptivecomputing/plugins-mws.git
cd plugins-mws
./gradlew tasks
```

This will download all dependencies and install Gradle and the MWS plugin project on your system.