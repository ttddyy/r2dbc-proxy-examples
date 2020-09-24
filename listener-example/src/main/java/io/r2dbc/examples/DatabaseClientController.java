package io.r2dbc.examples;

import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Provides endpoints using {@link DatabaseClient}.
 *
 * @author Tadaya Tsuyukubo
 */
@RestController
@RequestMapping(path = "/spring")
public class DatabaseClientController {

    private final DatabaseClient databaseClient;
    private final TransactionalOperator operator;

    public DatabaseClientController(DatabaseClient databaseClient, TransactionalOperator operator) {
        this.databaseClient = databaseClient;
        this.operator = operator;
    }

    @GetMapping
    String hi() {
        return "Hi from " + getClass().getSimpleName();
    }

    // Perform a query without transaction
    @GetMapping("/simple")
    Flux<Integer> simple() {
        return this.databaseClient.execute("SELECT value FROM test;")
                .map(row -> row.get("value", Integer.class))
                .all();
    }

    @GetMapping("/first")
    Mono<Integer> first() {
        return this.databaseClient.execute("SELECT value FROM test;")
                .map(row -> row.get("value", Integer.class))
                .first();
    }

    @GetMapping("/one")
    Mono<Integer> one() {
        return this.databaseClient.execute("SELECT value FROM test WHERE value=99;")
                .map(row -> row.get("value", Integer.class))
                .one();
    }


    // Perform an update query with transaction
    @GetMapping("/tx")
    Mono<Integer> tx() {
        return this.databaseClient.execute("INSERT INTO test VALUES (:value)")
                .bind("value", 100)
                .fetch().rowsUpdated().as(this.operator::transactional);
    }

    // Single transaction with multiple queries
    @RequestMapping("/tx-with-queries")
    Flux<Integer> txWithQueries() {
        Mono<Integer> execute1 = this.databaseClient.execute("INSERT INTO test VALUES (:value)")
                .bind("value", 100)
                .fetch().rowsUpdated();
        Mono<Integer> execute2 = this.databaseClient.execute("INSERT INTO test VALUES (:value)")
                .bind("value", 200)
                .fetch().rowsUpdated();
        return Flux.concat(execute1, execute2).as(this.operator::transactional);
    }


    // Multiple Tx on single connection
//    @GetMapping("/multi-tx")
//    Flux<Integer> multipleTx() {
//        Mono<Integer> execute1 = this.databaseClient.execute("INSERT INTO test VALUES (:value)")
//                .bind("value", 100)
//                .fetch().rowsUpdated();
//        Mono<Integer> execute2 = this.databaseClient.execute("INSERT INTO test VALUES (:value)")
//                .bind("value", 200)
//                .fetch().rowsUpdated();
//
//        Mono<Integer> action1 = execute1.as(this.operator::transactional);
//        Mono<Integer> action2 = execute2.as(this.operator::transactional);
//        return Flux.concat(action1, action2);
//    }

    // Explicit rollback
    @GetMapping("/rollback")
    Flux<Integer> rollback() {
        Mono<Integer> execute = this.databaseClient.execute("INSERT INTO test VALUES (:value)")
                .bind("value", 100)
                .fetch().rowsUpdated();
        return this.operator.execute(status -> {
            status.setRollbackOnly();
            return execute;
        }).ofType(Integer.class);
    }

    // Batch

    // Error
    @GetMapping("/error")
    Flux<?> error() {
        return this.databaseClient.execute("SELECT SOMETHING_WRONG();").map(row -> row.get(0)).all();
    }

    // Error with recovery
    @GetMapping("/error-recovery")
    Flux<?> errorRecovery() {
        return this.databaseClient.execute("SELECT SOMETHING_WRONG();")
                .map(row -> row.get(0))
                .all()
                .onErrorReturn("recovered");
    }

    // Error with transaction
    @GetMapping("/error-tx")
    Flux<?> errorTx() {
        return this.databaseClient.execute("SELECT SOMETHING_WRONG();")
                .map(row -> row.get(0))
                .all()
                .as(this.operator::transactional);
    }

}
