package io.r2dbc.examples.agent;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import io.r2dbc.proxy.callback.ConnectionFactoryCallbackHandler;
import io.r2dbc.proxy.callback.ProxyConfig;
import io.r2dbc.proxy.core.MethodExecutionInfo;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.listener.LifeCycleExecutionListener;
import io.r2dbc.proxy.listener.LifeCycleListener;
import io.r2dbc.proxy.listener.ProxyExecutionListener;
import io.r2dbc.proxy.support.MethodExecutionInfoFormatter;
import io.r2dbc.proxy.support.QueryExecutionInfoFormatter;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import static java.lang.String.format;
import static net.bytebuddy.implementation.MethodDelegation.to;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Sample Java Agent.
 *
 * This agent instruments {@link ConnectionFactory} and make the target application
 * participate to the r2dbc-proxy framework.
 *
 * @author Tadaya Tsuyukubo
 */
public class R2dbcProxyAgent {

	private static ProxyConfig proxyConfig = new ProxyConfig();

	static {
		configureProxyConfig(proxyConfig);
	}

	/**
	 * Configure the given {@link ProxyConfig}.
	 *
	 * @param proxyConfig {@link ProxyConfig} to configure
	 */
	private static void configureProxyConfig(ProxyConfig proxyConfig) {

		// as an example, printing out any method interactions and executed query.

		QueryExecutionInfoFormatter queryFormatter = QueryExecutionInfoFormatter.showAll();
		MethodExecutionInfoFormatter formatter = MethodExecutionInfoFormatter.withDefault();

		proxyConfig.addListener(new ProxyExecutionListener() {
			@Override
			public void beforeMethod(MethodExecutionInfo executionInfo) {
				System.out.println("Before >> " + formatter.format(executionInfo));
			}

			@Override
			public void afterMethod(MethodExecutionInfo executionInfo) {
				System.out.println("After  >> " + formatter.format(executionInfo));
			}

			@Override
			public void afterQuery(QueryExecutionInfo execInfo) {
				System.out.println(queryFormatter.format(execInfo));
			}
		});

		// To add LifeCycleListener, it needs to be wrapped by LifeCycleExecutionListener
		proxyConfig.addListener(LifeCycleExecutionListener.of(new LifeCycleListener() {
			@Override
			public void afterCreateOnConnectionFactory(MethodExecutionInfo methodExecutionInfo) {
				String msg = format(">> Connection acquired. took=%sms", methodExecutionInfo.getExecuteDuration().toMillis());
				System.out.println(msg);
			}
		}));

		// Optional: use ByteBuddy to create proxies
		proxyConfig.setProxyFactoryFactory(ByteBuddyProxyFactory::new);

	}


	public static void premain(String arg, Instrumentation inst) {

		System.out.println("\n\n\n");
		System.out.println("*****************************");
		System.out.println(">>> Java Agent Activated <<<");
		System.out.println("*****************************");
		System.out.println("\n\n\n");

		instrument(inst);
	}

	private static void instrument(Instrumentation inst) {
		// intercept methods defined on ConnectionFactory
		new AgentBuilder.Default()
				.type(isSubTypeOf(ConnectionFactory.class))
				.transform((builder, typeDescription, classLoader, module) -> builder
						.method(named("create").or(named("getMetadata")))
						.intercept(to(ConnectionFactoryInterceptor.class))
				)
				.installOn(inst);

	}

	/**
	 * Interceptor implementation.
	 *
	 * Intercept {@link ConnectionFactory#create()} and{@link ConnectionFactory#getMetadata()}
	 * methods. Then, perform proxy invocation logic.
	 * The returned object is a proxy object and any interaction to it triggers callback
	 * for listeners from r2dbc-proxy framework.
	 * In other words, this is the entry point to the r2dbc-proxy framework.
	 */
	@SuppressWarnings("unchecked")
	public static class ConnectionFactoryInterceptor {

		@RuntimeType
		public static Object intercept(@AllArguments Object[] args,
				@This ConnectionFactory connectionFactory, @Origin Method method,
				@SuperCall Callable<?> callable) throws Throwable {

			// Create callback handler for ConnectionFactory methods.
			// Also, update invocation strategy to directly returns the target object.

			// If invocation strategy is not set, default strategy performs a reflective
			// method call on the original ConnectionFactory instance.
			// However, for ByteBuddy, again the call get intercepted. So, it becomes
			// infinite loop of interceptions.

			ConnectionFactoryCallbackHandler handler = new ConnectionFactoryCallbackHandler(connectionFactory, proxyConfig);
			handler.setMethodInvocationStrategy((invokedMethod, invokedTarget, invokedArgs) -> {
				return callable.call();  // retrieve original result
			});


			// currently proxy argument(first arg) is not used. just passing fake object.
			Object result = handler.invoke("", method, args);


			String methodName = method.getName();

			if ("getMetadata".equals(methodName)) {
				return result;  // result is ConnectionFactoryMetadata
			}

			// handling for "ConnectionFactory#create()"

			// "ConnectionFactory#create()" defines return type as "Publisher<? extends Connection>".
			// Usually driver implementation class declares it as Mono.
			// On the other hand, the callback handler returns the proxy always as Flux in order to
			// handle method call generically.
			// This is not a problem in regular case; however, since ByteBuddy requires exact
			// type to be returned for its subclass, here requires converting the result to Mono.
			// To be defensive, check the return type. If the return type is not Mono(must be Flux),
			// then return as is.
			if (Mono.class.equals(method.getReturnType())) {
				return Mono.from((Publisher<? extends Connection>) result);
			}
			return result;  // return as Flux

		}
	}

}
