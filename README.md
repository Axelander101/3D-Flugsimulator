<<<<<<< HEAD
# FlightTraining

This is the readme for FlightTraining, try to keep it up to date with any information future-you will wish past-you
remembered to write down

## Project set up
This is a gradle project using JMonkey Engine and other java libraries

# Modules : 

Game module `:game` : holds `build.gradle` dependencies for the game code & should hold your code.

Desktop module `:desktop` : holds `build.gradle` for desktop dependencies & uses the `:game` module, this module can hold the desktop gui.


# Running Game : 

### Desktop : 

```gradle
./gradlew run
```


# Building Game :

### Desktop :

```bash
    $./gradlew :desktop:copyJars
```



### Distribute with a JRE

Distributing with a JRE means you'll need to provide an operating specific bundle for each OS you are
targeting (which is a disadvantage) but your end use will not have to have a JRE locally installed
(which is an advantage).

Either:

In your IDE execute the gradle task distZip (which you'll find under gradle > distributions > buildAllDistributions)

Or:

In the command line open at the root of this project enter the following command: gradlew buildAllDistributions

Then you will find a series of zip in the build/distributions folder. These zip(s) will contain your game, all the libraries to run it and an
OS specific JRE. (The same files will also be available unzipped in a folder, which may be useful if distributing via steampipe or similar).


References :



=> Gradle DSL : https://docs.gradle.org/current/dsl/index.html

=> Gradle for java : https://docs.gradle.org/current/userguide/multi_project_builds.html

=> Gradle/Groovy Udacity course by google : https://github.com/udacity/ud867/blob/master/1.11-Exercise-ConfigureFileSystemTasks/solution.gradle


=> See JMonkeyEngine Desktop Example : https://github.com/Scrappers-glitch/basic-gradle-template

=> See JMonkeyEngine RPI armhf Desktop Example : https://github.com/Scrappers-glitch/JmeCarPhysicsTestRPI



=======
# Tower-Defense
# 3D-FlugsimulatorPrototype
>>>>>>> ac710b647c48207c99690775120ee884ef61222b
