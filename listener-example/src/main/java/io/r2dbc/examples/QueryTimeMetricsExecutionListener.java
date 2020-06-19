package io.r2dbc.examples;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.proxy.listener.ProxyExecutionListener;
import org.springframework.util.StringUtils;

import static java.lang.String.format;

/**
 * Listener to populate micrometer metrics for query execution.
 * <p>
 * Create time metrics for query execution by type(read/write).
 * https://github.com/micrometer-metrics/micrometer/issues/635
 *
 * @author Tadaya Tsuyukubo
 */
public class QueryTimeMetricsExecutionListener implements ProxyExecutionListener {

	private MeterRegistry registry;

	private String metricNamePrefix = "r2dbc.";

	private QueryTypeDetector queryTypeDetector = new DefaultQueryTypeDetector();

	public QueryTimeMetricsExecutionListener(MeterRegistry registry) {
		this.registry = registry;
	}

	@Override
	public void afterQuery(QueryExecutionInfo queryExecutionInfo) {
		for (QueryInfo queryInfo : queryExecutionInfo.getQueries()) {
			QueryType queryType = this.queryTypeDetector.detect(queryInfo.getQuery());
			String readOrWrite = queryType == QueryType.READ ? "read" : "write";
			String metricsName = this.metricNamePrefix + "query." + readOrWrite;
			String description = format("Time to execute %s queries", readOrWrite);

			Timer timer = Timer
					.builder(metricsName)
					.description(description)
					.tags("event", "query")
					.register(this.registry);
			timer.record(queryExecutionInfo.getExecuteDuration());
		}
	}


	public void setRegistry(MeterRegistry registry) {
		this.registry = registry;
	}

	public void setMetricNamePrefix(String metricNamePrefix) {
		this.metricNamePrefix = metricNamePrefix;
	}

	public void setQueryTypeDetector(QueryTypeDetector queryTypeDetector) {
		this.queryTypeDetector = queryTypeDetector;
	}

	public enum QueryType {READ, WRITE}

	public interface QueryTypeDetector {
		QueryType detect(String query);
	}

	public static class DefaultQueryTypeDetector implements QueryTypeDetector {
		@Override
		public QueryType detect(String query) {
			return StringUtils.startsWithIgnoreCase(StringUtils.trimLeadingWhitespace(query), "SELECT") ? QueryType.READ : QueryType.WRITE;
		}
	}


}
