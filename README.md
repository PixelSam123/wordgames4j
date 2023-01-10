# wordgames4j

Alternative implementation for wordgames, a word games server for WebSockets

## Why another server implementation?

- Not feeling like finishing the Rust implementation until I wrap my head around async Rust
- Benchmarking interests (even more alternative implementations in even more languages coming soon?)

## List of games

1. `ws/anagram` Normal anagrams game. Plans:
    - [ ] Multiple language support
    - [ ] Time configuration
    - [ ] Word length configuration
    - [ ] Timer configuration

## Spinning it up

Instructions to run available from Quarkus' README below.

---

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only
> at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into
the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Pnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container
using:

```shell script
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/wordgames4j-1.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please
consult https://quarkus.io/guides/maven-tooling.

## Related Guides

- Narayana JTA - Transaction manager ([guide](https://quarkus.io/guides/transaction)): Offer JTA
  transaction support (included in Hibernate ORM)
- Hibernate Validator ([guide](https://quarkus.io/guides/validation)): Validate object properties (
  field, getter) and method parameters for your beans (REST, CDI, JPA)
- WebSockets ([guide](https://quarkus.io/guides/websockets)): WebSocket communication channel
  support
- Agroal - Database connection pool ([guide](https://quarkus.io/guides/datasource)): Pool JDBC
  database connections (included in Hibernate ORM)

## Provided Code

### RESTEasy Reactive

Easily start your Reactive RESTful Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)

### WebSockets

WebSocket communication channel starter code

[Related guide section...](https://quarkus.io/guides/websockets)