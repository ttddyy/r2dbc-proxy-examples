package io.r2dbc.examples.agent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import io.r2dbc.proxy.callback.BatchCallbackHandler;
import io.r2dbc.proxy.callback.CallbackHandler;
import io.r2dbc.proxy.callback.ConnectionCallbackHandler;
import io.r2dbc.proxy.callback.ConnectionFactoryCallbackHandler;
import io.r2dbc.proxy.callback.ProxyConfig;
import io.r2dbc.proxy.callback.ProxyFactory;
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
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import static net.bytebuddy.implementation.MethodDelegation.to;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;

/**
 * {@link ProxyFactory} implementation with {@link ByteBuddy}.
 *
 * Instead of using JDK Dynamic Proxy, use Byte Buddy to create proxies.
 *
 * https://github.com/r2dbc/r2dbc-spi/issues/9
 *
 * @author Tadaya Tsuyukubo
 */
public class ByteBuddyProxyFactory implements ProxyFactory {

	private ProxyConfig proxyConfig;

	private Constructor<ConnectionFactory> connectionFactoryProxyConstructor;

	private Constructor<Connection> connectionProxyConstructor;

	private Constructor<Batch> batchProxyConstructor;

	private Constructor<Statement> statementProxyConstructor;

	private Constructor<Result> resultProxyConstructor;

	public ByteBuddyProxyFactory(ProxyConfig proxyConfig) {

		this.proxyConfig = proxyConfig;

		ByteBuddy byteBuddy = new ByteBuddy();

		// generate proxy classes
		Class<ConnectionFactory> connectionFactoryProxyClass = createProxyClass(byteBuddy, ConnectionFactory.class);
		Class<Connection> connectionProxyClass = createProxyClass(byteBuddy, Connection.class);
		Class<Batch> batchProxyClass = createProxyClass(byteBuddy, Batch.class);
		Class<Statement> statementProxyClass = createProxyClass(byteBuddy, Statement.class);
		Class<Result> resultProxyClass = createProxyClass(byteBuddy, Result.class);

		// retrieve constructor from generated proxy classes
		this.connectionFactoryProxyConstructor = findConstructor(connectionFactoryProxyClass);
		this.connectionProxyConstructor = findConstructor(connectionProxyClass);
		this.batchProxyConstructor = findConstructor(batchProxyClass);
		this.statementProxyConstructor = findConstructor(statementProxyClass);
		this.resultProxyConstructor = findConstructor(resultProxyClass);

	}

	@SuppressWarnings("unchecked")
	private <T> Constructor<T> findConstructor(Class<T> proxyClass) {
		// currently each callback handler defines only one constructor, so shortcut the search
		return (Constructor<T>) proxyClass.getDeclaredConstructors()[0];
	}

	@SuppressWarnings("unchecked")
	private <T> Class<T> createProxyClass(ByteBuddy byteBuddy, Class<T> interfaceType) {
		return (Class<T>) byteBuddy
				.subclass(CallbackHandlerProxy.class)
				.implement(interfaceType)
				.method(isDeclaredBy(interfaceType))
				.intercept(to(CallbackHandlerInterceptor.class))
				.make()
				.load(interfaceType.getClassLoader())
				.getLoaded();
	}

	/**
	 * Base proxy class.
	 *
	 * ByteBuddy subclass this and add corresponding interface.
	 */
	public static class CallbackHandlerProxy {
		private CallbackHandler callbackHandler;

		public CallbackHandlerProxy(CallbackHandler callbackHandler) {
			this.callbackHandler = callbackHandler;
		}

		public Object invoke(Method method, Object[] args) throws Throwable {
			return callbackHandler.invoke(this, method, args);
		}
	}

	/**
	 * Interceptor for proxy of {@link CallbackHandlerProxy}.
	 *
	 * Simply delegates the invocation of proxy instance to the callback handler instance.
	 */
	public static class CallbackHandlerInterceptor {
		@RuntimeType
		public static Object intercept(@AllArguments Object[] args, @Origin Method method, @This CallbackHandlerProxy callbackHandler) throws Throwable {
			return callbackHandler.invoke(method, args);  // delegate to callback handler logic
		}
	}

	@Override
	public ConnectionFactory wrapConnectionFactory(ConnectionFactory connectionFactory) {
		ConnectionFactoryCallbackHandler handler = new ConnectionFactoryCallbackHandler(connectionFactory, this.proxyConfig);
		return instantiate(this.connectionFactoryProxyConstructor, handler);
	}

	@Override
	public Connection wrapConnection(Connection connection, ConnectionInfo connectionInfo) {
		ConnectionCallbackHandler handler = new ConnectionCallbackHandler(connection, connectionInfo, this.proxyConfig);
		return instantiate(this.connectionProxyConstructor, handler);
	}

	@Override
	public Batch wrapBatch(Batch batch, ConnectionInfo connectionInfo) {
		BatchCallbackHandler handler = new BatchCallbackHandler(batch, connectionInfo, this.proxyConfig);
		return instantiate(this.batchProxyConstructor, handler);
	}

	@Override
	public Statement wrapStatement(Statement statement, StatementInfo statementInfo, ConnectionInfo connectionInfo) {
		StatementCallbackHandler handler = new StatementCallbackHandler(statement, statementInfo, connectionInfo, this.proxyConfig);
		return instantiate(this.statementProxyConstructor, handler);
	}

	@Override
	public Result wrapResult(Result result, QueryExecutionInfo queryExecutionInfo) {
		ResultCallbackHandler handler = new ResultCallbackHandler(result, queryExecutionInfo, this.proxyConfig);
		return instantiate(this.resultProxyConstructor, handler);
	}

	private <T> T instantiate(Constructor<T> constructor, Object... args) {
		try {
			return constructor.newInstance(args);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to create an instance", e);
		}
	}
}
