# wordgames4j

~~Alternative~~ implementation for wordgames, a word games server for WebSockets  
I'm aware that this is basically my primary implementation now. The Rust server will be re-explored later.

## Why move this server to Java?

- Not feeling like finishing the Rust implementation until I wrap my head around async Rust
- Benchmarking interests (even more alternative implementations in even more languages coming soon?)

## APIs used

Random word API at https://random-word-api.herokuapp.com

## List of games

1. `ws/anagram` Normal anagrams game. Features:
  - [x] Multiple language support
  - [x] Time configuration
  - [x] Word length configuration
  - [x] Timer configuration

## How to play?

Connect to `wss://your-server-address/ws/anagram/{room ID}` and type `/help`!  
Room ID can be any string.

## Frontends

- [PixelSam123/wordgames-client](https://github.com/PixelSam123/wordgames-client)
- [pixelsam123.github.io/minigames](https://pixelsam123.github.io/minigames)

## Spinning it up

Instructions to run available from Quarkus' README below.

---

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./gradlew quarkusDev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only
> at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:

```shell script
./gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into
the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew build -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./gradlew build -Dquarkus.package.type=native
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container
using:

```shell script
./gradlew build -Dquarkus.package.type=native -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/wordgames4j-1.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please
consult https://quarkus.io/guides/gradle-tooling.
