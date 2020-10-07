package io.r2dbc.examples;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.proxy.test.MockQueryExecutionInfo;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Test for {@link QueryTimeMetricsExecutionListener}.
 *
 * @author Tadaya Tsuyukubo
 */
class QueryTimeMetricsExecutionListenerTest {

    @ParameterizedTest
    @ArgumentsSource(MetricsArgumentsProvider.class)
    void metrics(String query, String meterName) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        QueryTimeMetricsExecutionListener listener = new QueryTimeMetricsExecutionListener(registry);

        QueryInfo queryInfo = new QueryInfo(query);
        QueryExecutionInfo queryExecutionInfo = new MockQueryExecutionInfo.Builder()
                .queryInfo(queryInfo)
                .executeDuration(Duration.ofSeconds(10))
                .build();

        listener.afterQuery(queryExecutionInfo);

        Timer select = registry.get(meterName).timer();
        assertThat(select.count()).isEqualTo(1);
        assertThat(select.totalTime(TimeUnit.SECONDS)).isEqualTo(10);
    }

    private static class MetricsArgumentsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    // query, meter name
                    arguments("SELECT ...", "r2dbc.query.select"),
                    arguments("INSERT ...", "r2dbc.query.insert"),
                    arguments("UPDATE ...", "r2dbc.query.update"),
                    arguments("DELETE ...", "r2dbc.query.delete"),
                    arguments("UPSERT ...", "r2dbc.query.other")
            );
        }
    }
}
