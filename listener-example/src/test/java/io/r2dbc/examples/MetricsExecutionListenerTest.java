package io.r2dbc.examples;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.r2dbc.proxy.core.DefaultValueStore;
import io.r2dbc.proxy.core.ValueStore;
import io.r2dbc.proxy.test.MockMethodExecutionInfo;
import io.r2dbc.proxy.test.MockQueryExecutionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link MetricsExecutionListener}.
 *
 * @author Tadaya Tsuyukubo
 */
class MetricsExecutionListenerTest {

    private SimpleMeterRegistry registry;
    private MetricsExecutionListener listener;

    @BeforeEach
    void beforeEach() {
        this.registry = new SimpleMeterRegistry();
        this.listener = new MetricsExecutionListener(this.registry);
    }

    @Test
    void createConnection() {
        ValueStore valueStore = new DefaultValueStore();
        MockMethodExecutionInfo executionInfo = MockMethodExecutionInfo.builder().valueStore(valueStore).build();

        this.listener.beforeCreateOnConnectionFactory(executionInfo);
        this.listener.afterCreateOnConnectionFactory(executionInfo);

        List<Meter> meters = this.registry.getMeters();
        assertThat(meters).hasSize(1)
                .first()
                .isInstanceOfSatisfying(Timer.class, (timer) -> {
                    assertThat(timer.getId().getName()).isEqualTo("r2dbc.connection");
                    assertThat(timer.getId().getTags()).containsExactly(Tag.of("event", "create"));
                });
    }

    @Test
    void commit() {
        MockMethodExecutionInfo executionInfo = MockMethodExecutionInfo.empty();
        this.listener.afterCommitTransactionOnConnection(executionInfo);

        List<Meter> meters = this.registry.getMeters();
        assertThat(meters).hasSize(1)
                .first()
                .isInstanceOfSatisfying(Counter.class, (counter) -> {
                    assertThat(counter.getId().getName()).isEqualTo("r2dbc.transaction");
                    assertThat(counter.getId().getTags()).containsExactly(Tag.of("event", "commit"));
                    assertThat(counter.count()).isEqualTo(1);
                });
    }

    @Test
    void rollback() {
        MockMethodExecutionInfo executionInfo = MockMethodExecutionInfo.empty();
        this.listener.afterRollbackTransactionOnConnection(executionInfo);

        List<Meter> meters = this.registry.getMeters();
        assertThat(meters).hasSize(1)
                .first()
                .isInstanceOfSatisfying(Counter.class, (counter) -> {
                    assertThat(counter.getId().getName()).isEqualTo("r2dbc.transaction");
                    assertThat(counter.getId().getTags()).containsExactly(Tag.of("event", "rollback"));
                    assertThat(counter.count()).isEqualTo(1);
                });
    }

    @Test
    void afterExecuteOnBatch() {
        MockQueryExecutionInfo executionInfo = MockQueryExecutionInfo.empty();
        this.listener.afterExecuteOnBatch(executionInfo);

        List<Meter> meters = this.registry.getMeters();
        assertThat(meters).hasSize(1)
                .first()
                .isInstanceOfSatisfying(Counter.class, (counter) -> {
                    assertThat(counter.getId().getName()).isEqualTo("r2dbc.query");
                    assertThat(counter.count()).isEqualTo(1);
                });
    }

    @Test
    void afterExecuteOnStatement() {
        MockQueryExecutionInfo executionInfo = MockQueryExecutionInfo.empty();
        this.listener.afterExecuteOnStatement(executionInfo);

        List<Meter> meters = this.registry.getMeters();
        assertThat(meters).hasSize(1)
                .first()
                .isInstanceOfSatisfying(Counter.class, (counter) -> {
                    assertThat(counter.getId().getName()).isEqualTo("r2dbc.query");
                    assertThat(counter.count()).isEqualTo(1);
                });
    }

}
