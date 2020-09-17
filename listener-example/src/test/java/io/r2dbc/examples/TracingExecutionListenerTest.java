package io.r2dbc.examples;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import brave.test.TestSpanHandler;
import io.r2dbc.proxy.core.ConnectionInfo;
import io.r2dbc.proxy.core.ExecutionType;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.proxy.core.ValueStore;
import io.r2dbc.proxy.test.MockConnectionInfo;
import io.r2dbc.proxy.test.MockMethodExecutionInfo;
import io.r2dbc.proxy.test.MockQueryExecutionInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.r2dbc.examples.TracingExecutionListener.CONNECTION_SPAN_KEY;
import static io.r2dbc.examples.TracingExecutionListener.TRANSACTION_SPAN_KEY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link TracingExecutionListener}.
 *
 * @author Tadaya Tsuyukubo
 */
class TracingExecutionListenerTest {

    private StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();
    private TestSpanHandler spanHandler = new TestSpanHandler();
    private Tracing tracing = Tracing.newBuilder()
            .currentTraceContext(currentTraceContext)
            .addSpanHandler(spanHandler)
            .build();

    private Tracer tracer = tracing.tracer();
    private TracingExecutionListener listener = new TracingExecutionListener(tracer);

    @AfterEach
    void afterEach() {
        Tracing.current().close();
    }

    @Test
    void query() {
        ValueStore valueStore = ValueStore.create();
        ConnectionInfo connectionInfo = MockConnectionInfo.builder()
                .connectionId("foo")
                .valueStore(valueStore)
                .build();
        QueryInfo queryInfo = new QueryInfo("SELECT 1");
        MockQueryExecutionInfo queryExecutionInfo = MockQueryExecutionInfo.builder()
                .connectionInfo(connectionInfo)
                .queryInfo(queryInfo)
                .type(ExecutionType.STATEMENT)
                .threadName("thread-name")
                .threadId(300)
                .isSuccess(true)
                .build();

        this.listener.beforeQuery(queryExecutionInfo);
        this.listener.afterQuery(queryExecutionInfo);

        assertThat(this.spanHandler.spans()).hasSize(1);
        assertThat(this.spanHandler.get(0).name()).isEqualTo("r2dbc:query");
        assertThat(this.spanHandler.get(0).tags())
                .containsEntry("queries", "SELECT 1")
                .containsEntry("threadName", "thread-name")
                .containsEntry("threadId", "300")
                .containsEntry("success", "true")
        ;
    }

    @Test
    void createConnection() {
        ValueStore valueStore = ValueStore.create();
        ConnectionInfo connectionInfo = MockConnectionInfo.builder()
                .connectionId("foo")
                .valueStore(valueStore)
                .build();
        MockMethodExecutionInfo methodExecutionInfo = MockMethodExecutionInfo.builder()
                .connectionInfo(connectionInfo)
                .threadId(10)
                .threadName("thread-name")
                .build();

        this.listener.beforeCreateOnConnectionFactory(methodExecutionInfo);
        this.listener.afterCreateOnConnectionFactory(methodExecutionInfo);


        assertThat(valueStore.get(CONNECTION_SPAN_KEY)).as("Connection span should be stored")
                .isNotNull().isInstanceOf(Span.class);

        Span span = valueStore.get(CONNECTION_SPAN_KEY, Span.class);
        span.finish();

        assertThat(this.spanHandler.spans()).hasSize(1);
        assertThat(this.spanHandler.get(0).name()).isEqualTo("r2dbc:connection");
        assertThat(this.spanHandler.get(0).tags())
                .containsEntry("connectionId", "foo")
                .containsEntry("threadNameOnCreate", "thread-name")
                .containsEntry("threadIdOnCreate", "10")
        ;
        assertThat(this.spanHandler.get(0).containsAnnotation("Connection created")).isTrue();
    }

    @Test
    void createConnectionWithError() {
        Exception error = new RuntimeException();

        ValueStore valueStore = ValueStore.create();
        ConnectionInfo connectionInfo = MockConnectionInfo.builder()
                .connectionId("foo")
                .valueStore(valueStore)
                .build();
        MockMethodExecutionInfo methodExecutionInfo = MockMethodExecutionInfo.builder()
                .connectionInfo(connectionInfo)
                .threadId(10)
                .threadName("thread-name")
                .setThrown(error)
                .build();

        this.listener.beforeCreateOnConnectionFactory(methodExecutionInfo);
        this.listener.afterCreateOnConnectionFactory(methodExecutionInfo);

        assertThat(this.spanHandler.spans()).hasSize(1);
        assertThat(this.spanHandler.get(0).name()).isEqualTo("r2dbc:connection");
        assertThat(this.spanHandler.get(0).error()).isSameAs(error);
    }

    @Test
    void closeConnection() {
        Span span = this.tracer.nextSpan().kind(Span.Kind.CLIENT).start();

        ValueStore valueStore = ValueStore.create();
        valueStore.put(CONNECTION_SPAN_KEY, span);
        ConnectionInfo connectionInfo = MockConnectionInfo.builder()
                .connectionId("foo")
                .commitCount(10)
                .rollbackCount(20)
                .transactionCount(30)
                .valueStore(valueStore)
                .build();
        MockMethodExecutionInfo methodExecutionInfo = MockMethodExecutionInfo.builder()
                .connectionInfo(connectionInfo)
                .threadName("thread-name")
                .threadId(300)
                .build();

        this.listener.afterCloseOnConnection(methodExecutionInfo);

        assertThat(this.spanHandler.spans()).hasSize(1);
        assertThat(this.spanHandler.get(0).tags())
                .containsEntry("connectionId", "foo")
                .containsEntry("threadNameOnClose", "thread-name")
                .containsEntry("threadIdOnClose", "300")
                .containsEntry("commitCount", "10")
                .containsEntry("rollbackCount", "20")
                .containsEntry("transactionCount", "30")
        ;
    }

    @Test
    void closeConnectionWithError() {
        Span span = this.tracer.nextSpan().kind(Span.Kind.CLIENT).start();

        Exception error = new RuntimeException();

        ValueStore valueStore = ValueStore.create();
        valueStore.put(CONNECTION_SPAN_KEY, span);
        ConnectionInfo connectionInfo = MockConnectionInfo.builder()
                .connectionId("foo")
                .valueStore(valueStore)
                .build();
        MockMethodExecutionInfo methodExecutionInfo = MockMethodExecutionInfo.builder()
                .connectionInfo(connectionInfo)
                .threadName("thread-name")
                .setThrown(error)
                .build();

        this.listener.afterCloseOnConnection(methodExecutionInfo);

        assertThat(this.spanHandler.spans()).hasSize(1);
        assertThat(this.spanHandler.get(0).error()).isSameAs(error);
    }

    @Test
    void beginTransaction() {
        ValueStore valueStore = ValueStore.create();
        ConnectionInfo connectionInfo = MockConnectionInfo.builder()
                .connectionId("foo")
                .valueStore(valueStore)
                .build();
        MockMethodExecutionInfo methodExecutionInfo = MockMethodExecutionInfo.builder()
                .connectionInfo(connectionInfo)
                .build();

        this.listener.beforeBeginTransactionOnConnection(methodExecutionInfo);

        Span span = valueStore.get(TRANSACTION_SPAN_KEY, Span.class);
        assertThat(span).as("transaction span should be stored").isNotNull();

        assertThat(this.spanHandler.spans()).as("Span is not finished yet").isEmpty();

        span.finish();
        assertThat(this.spanHandler.get(0).name()).isEqualTo("r2dbc:transaction");
    }

    @Test
    void transactionCommit() {
        Span connSpan = this.tracer.nextSpan().kind(Span.Kind.CLIENT).start();
        Span txSpan = this.tracer.nextSpan().kind(Span.Kind.CLIENT).start();

        ValueStore valueStore = ValueStore.create();
        valueStore.put(CONNECTION_SPAN_KEY, connSpan);
        valueStore.put(TRANSACTION_SPAN_KEY, txSpan);
        ConnectionInfo connectionInfo = MockConnectionInfo.builder()
                .connectionId("foo")
                .valueStore(valueStore)
                .build();
        MockMethodExecutionInfo methodExecutionInfo = MockMethodExecutionInfo.builder()
                .connectionInfo(connectionInfo)
                .threadId(10)
                .threadName("thread-name")
                .build();

        this.listener.afterCommitTransactionOnConnection(methodExecutionInfo);

        // check txSpan
        assertThat(this.spanHandler.spans()).hasSize(1);
        assertThat(this.spanHandler.get(0).tags())
                .containsEntry("connectionId", "foo")
                .containsEntry("threadName", "thread-name")
                .containsEntry("threadId", "10")
        ;
        assertThat(this.spanHandler.get(0).containsAnnotation("Commit")).isTrue();

        // check connSpan
        this.spanHandler.clear();
        connSpan.finish();
        assertThat(this.spanHandler.spans()).hasSize(1);
        assertThat(this.spanHandler.get(0).containsAnnotation("Transaction commit")).isTrue();
    }

    @Test
    void transactionRollback() {
        Span connSpan = this.tracer.nextSpan().kind(Span.Kind.CLIENT).start();
        Span txSpan = this.tracer.nextSpan().kind(Span.Kind.CLIENT).start();

        ValueStore valueStore = ValueStore.create();
        valueStore.put(CONNECTION_SPAN_KEY, connSpan);
        valueStore.put(TRANSACTION_SPAN_KEY, txSpan);
        ConnectionInfo connectionInfo = MockConnectionInfo.builder()
                .connectionId("foo")
                .valueStore(valueStore)
                .build();
        MockMethodExecutionInfo methodExecutionInfo = MockMethodExecutionInfo.builder()
                .connectionInfo(connectionInfo)
                .threadId(10)
                .threadName("thread-name")
                .build();

        this.listener.afterRollbackTransactionOnConnection(methodExecutionInfo);

        // check txSpan
        assertThat(this.spanHandler.spans()).hasSize(1);
        assertThat(this.spanHandler.get(0).tags())
                .containsEntry("connectionId", "foo")
                .containsEntry("threadName", "thread-name")
                .containsEntry("threadId", "10")
        ;
        assertThat(this.spanHandler.get(0).containsAnnotation("Rollback")).isTrue();

        // check connSpan
        this.spanHandler.clear();
        connSpan.finish();
        assertThat(this.spanHandler.spans()).hasSize(1);
        assertThat(this.spanHandler.get(0).containsAnnotation("Transaction rollback")).isTrue();
    }

}
