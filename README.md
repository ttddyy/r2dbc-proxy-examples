# r2dbc-proxy-samples

[r2dbc-proxy][r2dbc-proxy] sample projects.

## Samples

- Tracing with Sleuth
- Metrics with Micrometer (and log slow query)

## Tracing with Sleuth

**[TracingExecutionListener](./src/main/java/io/r2dbc/examples/TracingExecutionListener.java)** :
_Instrument R2DBC interaction to create tracing spans_. 


### Sample tracing images

Tracing query

![Tracing query](images/zipkin-tracing-query.png)

Tracing transaction

![Tracing transaction](images/zipkin-tracing-transaction.png)

Tracing transaction rollback

![Tracing transaction rollback](images/zipkin-tracing-rollback.png)

Connection Span

![Connection span](images/zipkin-span-connection.png)

Transaction Span

![Transaction span](images/zipkin-span-transaction.png)

Quey Span (Single Query)

![Query span](images/zipkin-span-query.png)

Quey Span (Batch Query)

![Query batch span](images/zipkin-span-batch-query.png)


## Metrics with Micrometer (and log slow query)

**[MetricsExecutionListener](./src/main/java/io/r2dbc/examples/MetricsExecutionListener.java)** :
_Populates following metrics:_

- Time took to create a connection
- Commit and rollback counts
- Executed query count
- Slow query count

Metrics are accessible via JMX and metrics endpoint(`/actuator/metrics`).

Also, logs slow queries that took more than 500ms.


## Sample metrics images

*JMX entries:*

![JMX entries](images/metrics-jmx-entries.png)

*Connection metrics on JMX:*

![JMX Connection](images/metrics-jmx-connection.png)

*Query metrics on JMX:*

![JMX Query](images/metrics-jmx-query.png)

*Connection metrics on actuator (`/actuator/metrics/r2dbc.connection`):*

![Actuator Connection](images/metrics-actuator-connection.png)

*Transaction metrics on actuator (`/actuator/metrics/r2dbc.transaction`):*

![Actuator Transaction](images/metrics-actuator-transaction.png)

*Slow query log:*
![Slow query log](images/metrics-slow-query-log.png)

----

# How to run

Start zipkin
```shell
> docker run -d -p 9411:9411 openzipkin/zipkin
```

Start `Application`

Access endpoints
```shell
> curl localhost:8080
> curl localhost:8080/batch
> curl localhost:8080/transaction
> curl localhost:8080/rollback
> curl localhost:8080/slow
```

Metrics actuator endpoint

```shell
> curl localhost:8080/actuator/metrics
```

----

[r2dbc-proxy]: https://github.com/r2dbc/r2dbc-proxy
[LifeCycleListener]: https://github.com/r2dbc/r2dbc-proxy/blob/master/src/main/java/io/r2dbc/proxy/listener/LifeCycleListener.java
