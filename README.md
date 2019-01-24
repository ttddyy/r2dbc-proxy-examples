# r2dbc-proxy-samples

[r2dbc-proxy][r2dbc-proxy] sample projects.

## Samples

### Tracing with Sleuth

* [dsp-r2dbc-tracing-sleuth](./dsp-r2dbc-tracing-sleuth)

### Metrics with Micrometer (and log slow query)

* [dsp-r2dbc-metrics-micrometer](./dsp-r2dbc-metrics-micrometer)


## Tracing with Sleuth

### Implementation

**[TracingExecutionListener](./src/main/java/net/ttddyy/TracingExecutionListener.java)**

An implementation of [`LifeCycleListener`][LifeCycleListener] which creates tracing spans.


### Sample tracing images

Tracing query

![Tracing query](images/tracing-query.png)

Tracing transaction

![Tracing transaction](images/tracing-transaction.png)

Tracing transaction rollback

![Tracing transaction rollback](images/tracing-rollback.png)

Connection Span

![Connection span](images/span-connection.png)

Transaction Span

![Transaction span](images/span-transaction.png)

Quey Span (Single Query)

![Query span](images/span-query.png)

Quey Span (Batch Query)

![Query batch span](images/span-batch-query.png)



## Metrics with Micrometer (and log slow query)

----

[r2dbc-proxy]: https://github.com/r2dbc/r2dbc-proxy