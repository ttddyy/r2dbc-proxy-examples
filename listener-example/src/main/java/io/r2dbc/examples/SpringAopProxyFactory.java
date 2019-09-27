package io.r2dbc.examples;

import io.r2dbc.proxy.callback.BatchCallbackHandler;
import io.r2dbc.proxy.callback.CallbackHandler;
import io.r2dbc.proxy.callback.ConnectionCallbackHandler;
import io.r2dbc.proxy.callback.ConnectionFactoryCallbackHandler;
import io.r2dbc.proxy.callback.ProxyConfig;
import io.r2dbc.proxy.callback.ResultCallbackHandler;
import io.r2dbc.proxy.callback.StatementCallbackHandler;
import io.r2dbc.proxy.core.ConnectionInfo;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.StatementInfo;
import io.r2dbc.spi.Batch;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import io.r2dbc.spi.Wrapped;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.framework.ProxyFactory;

/**
 * {@link io.r2dbc.proxy.callback.ProxyFactory} implementation that uses spring's {@link ProxyFactory} to create proxy.
 *
 * @author Tadaya Tsuyukubo
 */
public class SpringAopProxyFactory implements io.r2dbc.proxy.callback.ProxyFactory {

	private ProxyConfig proxyConfig;

	public SpringAopProxyFactory(ProxyConfig proxyConfig) {
		this.proxyConfig = proxyConfig;
	}

	/**
	 * Interceptor for proxy.
	 *
	 * Delegate the invocation to the provided {@link CallbackHandler}.
	 */
	private static class ProxyInterceptor implements MethodInterceptor {
		CallbackHandler callbackHandler;

		public ProxyInterceptor(CallbackHandler callbackHandler) {
			this.callbackHandler = callbackHandler;
		}

		@Override
		public Object invoke(MethodInvocation methodInvocation) throws Throwable {
			return this.callbackHandler.invoke(methodInvocation.getThis(), methodInvocation.getMethod(), methodInvocation.getArguments());
		}
	}

	private <T> T createProxy(CallbackHandler callbackHandler, Object target, Class<T> proxyInterface) {
		ProxyInterceptor interceptor = new ProxyInterceptor(callbackHandler);

		// NOTE: This ProxyFactory will use jdk dynamic proxy.
		// This is because we try to make a proxy on interface, and spring's ProxyFactory
		// uses JdkDynamicAopProxy for it.
		// See logic detail on "DefaultAopProxyFactory#createAopProxy"
		// We could put the actual object and instruct cglib to subclass it; however,
		// r2dbc implementations(in this case, H2 driver implementation classes) are
		// final classes and cglib cannot subclass final classes.
		// Since this implementation is to demonstrate applying different proxy mechanism,
		// it is ok to use jdk dynamic proxy.

		ProxyFactory proxyFactory = new ProxyFactory(target);
		proxyFactory.addAdvice(interceptor);
		proxyFactory.addInterface(proxyInterface);
		proxyFactory.addInterface(Wrapped.class);  // add this to all proxies
		T proxy = proxyInterface.cast(proxyFactory.getProxy());

		return proxy;
	}

	@Override
	public ConnectionFactory wrapConnectionFactory(ConnectionFactory connectionFactory) {
		ConnectionFactoryCallbackHandler handler = new ConnectionFactoryCallbackHandler(connectionFactory, this.proxyConfig);
		return createProxy(handler, connectionFactory, ConnectionFactory.class);
	}

	@Override
	public Connection wrapConnection(Connection connection, ConnectionInfo connectionInfo) {
		ConnectionCallbackHandler handler = new ConnectionCallbackHandler(connection, connectionInfo, this.proxyConfig);
		return createProxy(handler, connection, Connection.class);
	}

	@Override
	public Batch wrapBatch(Batch batch, ConnectionInfo connectionInfo) {
		BatchCallbackHandler handler = new BatchCallbackHandler(batch, connectionInfo, this.proxyConfig);
		return createProxy(handler, batch, Batch.class);
	}

	@Override
	public Statement wrapStatement(Statement statement, StatementInfo statementInfo, ConnectionInfo connectionInfo) {
		StatementCallbackHandler handler = new StatementCallbackHandler(statement, statementInfo, connectionInfo, this.proxyConfig);
		return createProxy(handler, statement, Statement.class);
	}

	@Override
	public Result wrapResult(Result result, QueryExecutionInfo queryExecutionInfo) {
		ResultCallbackHandler handler = new ResultCallbackHandler(result, queryExecutionInfo, this.proxyConfig);
		return createProxy(handler, result, Result.class);
	}

}
