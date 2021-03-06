# [r2dbc-proxy][r2dbc-proxy] example with Java Agent.

This sample project contains following implementations.

`Application.java` is a simple spring-boot web application which uses R2DBC to access
in-memory H2 database.

`R2dbcProxyAgent` is the java agent implementation that instruments the attached application's
`ConnectionFactory`, and make it participate to `r2dbc-proxy` framework.
Instrumentation uses [Byte Buddy][byte-buddy].

When `ConnectionFactory` is instrumented, it prints out method interactions with R2DBC
SPI and query executions to the application console.

`ByteBuddyProxyFactory` is a `ProxyFactory` implementation that uses [Byte Buddy][byte-buddy]
 to create proxy objects. Usage of this class is optional.


## Modules

### common

This module contains implementation for both spring-boot application(`Application`) and
java agent(`R2dbcProxyAgent`) classes.

### package-application

This module generates executable spring-boot jar using `Application` class from common module.
Generated jar file does NOT contain any agent related classes.

This module does NOT contain any implementation. Simply `pom.xml` is used to generate jar file.

### package-agent

This module generates java agent jar file using `R2dbcProxyAgent` class from common module.
The generated jar contains agent implementation and unpacked related libraries.

This module does NOT contain any implementation. Simply `pom.xml` is used to generate jar file.


## Build

```shell
./mvnw packagae
```

This generates following files:

Application: `package-application/target/examples-application-1.0-SNAPSHOT.jar`  
_(This is a spring-boot executable jar file.)_

Agent: `package-agent/target/examples-agent-1.0-SNAPSHOT-jar-with-dependencies.jar`

**NOTE**
Currently, it is depending on SNAPSHOT version of r2dbc-proxy. This is because some changesets
after M7 are needed to run the agent with ByteBuddy. Once M8 is released, SNAPSHOT dependency
should be updated.

## Run

### Command line

#### Run application only

```shell
java -jar package-application/target/examples-application-1.0-SNAPSHOT.jar
```

#### Run application with java agent

```shell
java -javaagent:package-agent/target/examples-agent-1.0-SNAPSHOT-jar-with-dependencies.jar \
     -jar package-application/target/examples-application-1.0-SNAPSHOT.jar
```

### From IDE

Run `Application` class.

Once agent jar file has generated by command line, specify the following parameter to
the "VM Options":

`-javaagent:package-agent/target/examples-agent-1.0-SNAPSHOT-jar-with-dependencies.jar`


## Endpoint

```shell
curl -i localhost:8080/
```

Please reference `Application` to see what URLs are mapped.

----

[r2dbc-proxy]: https://github.com/r2dbc/r2dbc-proxy
[byte-buddy]: https://bytebuddy.net/