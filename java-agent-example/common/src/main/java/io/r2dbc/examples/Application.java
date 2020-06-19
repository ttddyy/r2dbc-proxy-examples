package io.r2dbc.examples;

import io.r2dbc.h2.H2ConnectionConfiguration;
import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.sql.DataSource;

/**
 * R2DBC proxy sample application
 */
@SpringBootApplication
@RestController
public class Application {

	public static void main(String[] args) {

		// To simply apply the agent implementation, uncomment here
//		R2dbcProxyAgent.premain(null, ByteBuddyAgent.install());

		ApplicationContext ctx = SpringApplication.run(Application.class, args);

//		ctx.getBean(ConnectionFactory.class).getMetadata();
	}

	@Autowired
	DatabaseClient databaseClient;

	@Autowired
	TransactionalOperator operator;

	@RequestMapping("/")
	Flux<?> select() {
		return this.databaseClient.execute("SELECT value FROM test;")
				.map(row -> row.get("value", Integer.class))
				.all();
	}

	@RequestMapping("/transaction")
	Mono<?> transaction() {
		return this.databaseClient.execute("INSERT INTO test VALUES (:value)")
				.bind("value", 200)
				.fetch().rowsUpdated().as(this.operator::transactional);
	}

	// TODO: find a way to manually rollback transaction
//	@RequestMapping("/rollback")
//	Mono<?> rollback() {
//		return this.databaseClient.execute("INSERT INTO test VALUES (:value)")
//				.bind("value", "ABC")  // wrong value type
//				.fetch().rowsUpdated().as(this.operator::transactional)
//				.onErrorResume(t -> Mono.just(-99));
//	}

	@RequestMapping("/slow")
	Flux<?> slow() {
		return this.databaseClient.execute("CALL SLEEP(700);").map(row -> Mono.just(-1)).all();
	}

	@Bean
	DataSource dataSource() {
		return new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.H2)
				.build();
	}

	@Bean
	CommandLineRunner bootstrap(DataSource dataSource) {
		return args -> {
			JdbcOperations jdbcOperations = new JdbcTemplate(dataSource);
			jdbcOperations.execute("DROP TABLE IF EXISTS test");
			jdbcOperations.execute("CREATE TABLE test ( value INTEGER )");
			jdbcOperations.execute("INSERT INTO test VALUES (100)");
			jdbcOperations.execute("INSERT INTO test VALUES (200)");

			// create sleep function for slow query
			jdbcOperations.execute("CREATE ALIAS SLEEP FOR \"java.lang.Thread.sleep(long)\"");
		};
	}

	@Bean
	ConnectionFactory connectionFactory() {
		H2ConnectionConfiguration h2Configuration = H2ConnectionConfiguration.builder()
				.username("sa")
				.password("")
				.inMemory("testdb")
				.build();


		ConnectionFactory connectionFactory = new H2ConnectionFactory(h2Configuration);
		return connectionFactory;
	}


	@Bean
	DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
		return DatabaseClient.create(connectionFactory);
	}

}
