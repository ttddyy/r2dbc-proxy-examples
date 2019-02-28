# r2dbc-proxy-examples

[r2dbc-proxy][r2dbc-proxy] sample projects.

## listener-example

This example provides sample listener implementations.
Currently, there are listeners that integrate with [Spring Cloud Sleuth][spring-cloud-sleuth]
and [Micrometer][micrometer].

Also, this example uses `ProxyFactory` implementation with [Spring's ProxyFactory framework][spring-proxyfactory]
to create proxies. (optional)

## java-agent-example

This example provides sample implementation to apply [r2dbc-proxy][r2dbc-proxy]
using Java Agent.

With Java Agent, application does NOT need to know or aware of [r2dbc-proxy][r2dbc-proxy].
So, it is a non-intrusive way to integrate the [r2dbc-proxy][r2dbc-proxy] framework to
the application.

Also, this uses `ProxyFactory` implementation with [Byte Buddy][byte-buddy] which
performs byte code manipulation to create proxies. (optional)

----
[r2dbc-proxy]: https://github.com/r2dbc/r2dbc-proxy
[spring-cloud-sleuth]: https://spring.io/projects/spring-cloud-sleuth
[micrometer]: http://micrometer.io/
[byte-buddy]: https://bytebuddy.net
[spring-proxyfactory]: https://docs.spring.io/spring/docs/current/spring-framework-reference/core.html#aop-prog
