package io.r2dbc.examples;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provide endpoints using R2DBC SPIs.
 *
 * @author Tadaya Tsuyukubo
 */
@RestController
@RequestMapping(path = "/spi")
public class R2dbcSpiController {

    private final ConnectionFactory connectionFactory;

    public R2dbcSpiController(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @GetMapping
    String hi() {
        return "Hi from " + getClass().getSimpleName();
    }

    // Perform a query without transaction
    @GetMapping("/simple")
    Flux<Integer> simple() {
        return Flux.usingWhen(this.connectionFactory.create(), connection -> {
            String query = "SELECT value FROM test";
            Flux<Result> execute = Flux.from(connection.createStatement(query).execute());
            Function<Result, Flux<Integer>> mapper = (result) -> Flux.from(result.map((row, rowMetadata) -> row.get("value", Integer.class)));
            return execute.flatMap(mapper);
        }, Connection::close, (c, err) -> c.close(), Connection::close);
    }

    @GetMapping("/multi-queries")
    Flux<Integer> multiQueries() {
        return Flux.usingWhen(this.connectionFactory.create(), connection -> {
            String queries = "SELECT value FROM test; SELECT value FROM test";
            Flux<Result> execute = Flux.from(connection.createStatement(queries).execute());
            Function<Result, Flux<Integer>> mapper = (result) -> Flux.from(result.map((row, rowMetadata) -> row.get("value", Integer.class)));
            return execute.flatMap(mapper);
        }, Connection::close, (c, err) -> c.close(), Connection::close);
    }

    // Perform an update query with transaction
    @GetMapping("/tx")
    Mono<Integer> tx() {
        return Mono.usingWhen(this.connectionFactory.create(), connection -> {
            String query = "INSERT INTO test VALUES ($1)";
            Flux<Result> execute = Flux.from(connection.createStatement(query).bind("$1", "100").execute());
            Function<Result, Mono<Integer>> mapper = (result) -> Mono.from(result.getRowsUpdated());
            Flux<Integer> action = execute.flatMap(mapper);

            return Flux.concat(
                    Mono.from(connection.beginTransaction()).then(Mono.empty()),
                    action,
                    Mono.from(connection.commitTransaction()).then(Mono.empty())
            ).collect(Collectors.summingInt(i -> i));
        }, Connection::close, (c, err) -> c.close(), Connection::close);
    }

    @GetMapping("/tx-with-queries")
    Mono<Integer> txWithQueries() {
        return Mono.usingWhen(this.connectionFactory.create(), connection -> {
            String query = "INSERT INTO test VALUES ($1)";
            Function<Result, Mono<Integer>> mapper = (result) -> Mono.from(result.getRowsUpdated());
            Flux<Integer> execute1 = Flux.from(connection.createStatement(query).bind("$1", "100").execute()).flatMap(mapper);
            Flux<Integer> execute2 = Flux.from(connection.createStatement(query).bind("$1", "200").execute()).flatMap(mapper);

            // add up num of updated rows
            Mono<Integer> action = Flux.concat(execute1, execute2).collect(Collectors.summingInt(value -> value));

            return Flux.concat(
                    Mono.from(connection.beginTransaction()).then(Mono.empty()),
                    action,
                    Mono.from(connection.commitTransaction()).then(Mono.empty())
            ).collect(Collectors.summingInt(i -> i));
        }, Connection::close, (c, err) -> c.close(), Connection::close);
    }


    // Multiple Tx on single connection
    @GetMapping("/multi-tx")
    Flux<Integer> multipleTx() {
        return Flux.usingWhen(this.connectionFactory.create(), connection -> {
            String query = "INSERT INTO test VALUES ($1)";
            Function<Result, Mono<Integer>> mapper = (result) -> Mono.from(result.getRowsUpdated());
            Flux<Integer> execute1 = Flux.from(connection.createStatement(query).bind("$1", "100").execute()).flatMap(mapper);
            Flux<Integer> execute2 = Flux.from(connection.createStatement(query).bind("$1", "200").execute()).flatMap(mapper);

            return Flux.concat(
                    Mono.from(connection.beginTransaction()).then(Mono.empty()),
                    execute1,
                    Mono.from(connection.commitTransaction()).then(Mono.empty()),
                    Mono.from(connection.beginTransaction()).then(Mono.empty()),
                    execute2,
                    Mono.from(connection.commitTransaction()).then(Mono.empty())
            );
        }, Connection::close, (c, err) -> c.close(), Connection::close);
    }

    // Explicit rollback
    @GetMapping("/rollback")
    Mono<Integer> rollback() {
        return Mono.usingWhen(this.connectionFactory.create(), connection -> {
            String query = "INSERT INTO test VALUES ($1)";
            Function<Result, Mono<Integer>> mapper = (result) -> Mono.from(result.getRowsUpdated());
            Flux<Integer> execute = Flux.from(connection.createStatement(query).bind("$1", "100").execute()).flatMap(mapper);

            return Flux.concat(
                    Mono.from(connection.beginTransaction()).then(Mono.empty()),
                    execute,
                    Mono.from(connection.rollbackTransaction()).then(Mono.empty())
            ).collect(Collectors.summingInt(i -> i));
        }, Connection::close, (c, err) -> c.close(), Connection::close);
    }

    // Batch
    @GetMapping("/batch")
    Flux<Integer> batch() {
        return Flux.usingWhen(this.connectionFactory.create(), connection -> {
            String query1 = "INSERT INTO test VALUES (50)";
            String query2 = "INSERT INTO test VALUES (70)";
            Function<Result, Mono<Integer>> mapper = (result) -> Mono.from(result.getRowsUpdated());
            return Flux.from(connection.createBatch().add(query1).add(query2).execute()).flatMap(mapper);
        }, Connection::close, (c, err) -> c.close(), Connection::close);
    }

    // Error
    @GetMapping("/error")
    Flux<Integer> error() {
        return Flux.usingWhen(this.connectionFactory.create(), connection -> {
            String query = "SELECT SOMETHING_WRONG();";
            Flux<Result> execute = Flux.from(connection.createStatement(query).execute());
            Function<Result, Flux<Integer>> mapper = (result) -> Flux.from(result.map((row, rowMetadata) -> row.get(0, Integer.class)));
            return execute.flatMap(mapper);
        }, Connection::close, (c, err) -> c.close(), Connection::close);
    }

    // Error with recovery
    @GetMapping("/error-recovery")
    Flux<?> errorRecovery() {
        return Flux.usingWhen(this.connectionFactory.create(), connection -> {
            String query = "SELECT SOMETHING_WRONG();";
            Flux<Result> execute = Flux.from(connection.createStatement(query).execute());
            Function<Result, Flux<Integer>> mapper = (result) -> Flux.from(result.map((row, rowMetadata) -> row.get(0, Integer.class)));
            return execute.flatMap(mapper).onErrorReturn(-1);
        }, Connection::close, (c, err) -> c.close(), Connection::close);
    }

    // Error with Tx
    @GetMapping("/error-tx")
    Mono<Integer> errorTx() {
        return Mono.usingWhen(this.connectionFactory.create(), connection -> {
            String query = "INSERT INTO test VALUES ($1)";
            Flux<Result> execute = Flux.from(connection.createStatement(query).bind("$1", "ABC").execute());
            Function<Result, Mono<Integer>> mapper = (result) -> Mono.from(result.getRowsUpdated());
            Flux<Integer> action = execute.flatMap(mapper);

            return Flux.concat(
                    Mono.from(connection.beginTransaction()).then(),
                    action,
                    Mono.from(connection.commitTransaction()).then()
            ).onErrorResume(err ->
                    Mono.from(connection.rollbackTransaction()).thenReturn(-1)
            ).collect(Collectors.summingInt(i -> (int) i));
        }, Connection::close, (c, err) -> c.close(), Connection::close);
    }

}
