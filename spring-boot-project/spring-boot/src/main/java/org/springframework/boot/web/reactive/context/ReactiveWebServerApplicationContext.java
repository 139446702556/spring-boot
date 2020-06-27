/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.web.reactive.context;

import java.util.function.Supplier;

import reactor.core.publisher.Mono;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.web.context.ConfigurableWebServerApplicationContext;
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationContextException;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;

/**
 * A {@link GenericReactiveWebApplicationContext} that can be used to bootstrap itself
 * from a contained {@link ReactiveWebServerFactory} bean.
 * spring boot使用Reactive web服务器时用的ApplicationContext实现类
 * @author Brian Clozel
 * @since 2.0.0
 */
public class ReactiveWebServerApplicationContext extends GenericReactiveWebApplicationContext
		implements ConfigurableWebServerApplicationContext {
	/**ServerManager对象*/
	private volatile ServerManager serverManager;
	/**server命名空间  此属性由setServerNamespace方法进行注入*/
	private String serverNamespace;

	/**
	 * Create a new {@link ReactiveWebServerApplicationContext}.
	 */
	public ReactiveWebServerApplicationContext() {
	}

	/**
	 * Create a new {@link ReactiveWebServerApplicationContext} with the given
	 * {@code DefaultListableBeanFactory}.
	 * @param beanFactory the DefaultListableBeanFactory instance to use for this context
	 */
	public ReactiveWebServerApplicationContext(DefaultListableBeanFactory beanFactory) {
		super(beanFactory);
	}
	/**此方法实现于AbstractApplicationContext抽象类中*/
	@Override
	public final void refresh() throws BeansException, IllegalStateException {
		try {
			//调用父类方法
			super.refresh();
		}
		catch (RuntimeException ex) {
			//停止，并释放Reactive WebServer
			stopAndReleaseReactiveWebServer();
			throw ex;
		}
	}
	/**在容器初始化时，来完成WebServer的创建（部分服务不包括启动，像tomcat服务器在初始化对象时，就会进行服务启动的调用）*/
	@Override
	protected void onRefresh() {
		//调用父类方法
		super.onRefresh();
		try {
			//创建WebServer
			createWebServer();
		}
		catch (Throwable ex) {
			throw new ApplicationContextException("Unable to start reactive web server", ex);
		}
	}
	/**创建WebServer对象*/
	private void createWebServer() {
		//获取当前容器中的serverManager属性
		ServerManager serverManager = this.serverManager;
		//如果不存在，则进行初始化创建
		if (serverManager == null) {
			//获取WebServerFactory在当前容器中的beanName
			String webServerFactoryBeanName = getWebServerFactoryBeanName();
			//从容器中获取指定名称的ReactiveWebSServerFactory类型的bean对象
			ReactiveWebServerFactory webServerFactory = getWebServerFactory(webServerFactoryBeanName);
			//获取当前工厂对象的设置  是否要延迟初始化
			boolean lazyInit = getBeanFactory().getBeanDefinition(webServerFactoryBeanName).isLazyInit();
			//创建ServerManager对象，并设置到serverManager属性中
			this.serverManager = ServerManager.get(webServerFactory, lazyInit);
		}
		//初始化PropertySource
		initPropertySources();
	}
	/**
	 * 获取WebServerFactory在当前容器中的beanName
	 * 因为在引入spring-boot-starter-webflux的时候会自动配置出NettyReactiveWebServerFactory bean对象
	 * 所以此处默认获得的就是NettyReactiveWebServerFactory对象
	 */
	protected String getWebServerFactoryBeanName() {
		// Use bean names so that we don't consider the hierarchy。
		//从当前spring容器中获取ReactiveWebServerFactory类型的bean对象的名称数组
		String[] beanNames = getBeanFactory().getBeanNamesForType(ReactiveWebServerFactory.class);
		//如果未找到结果，则抛出异常
		if (beanNames.length == 0) {
			throw new ApplicationContextException(
					"Unable to start ReactiveWebApplicationContext due to missing ReactiveWebServerFactory bean.");
		}
		//如果结果操作一个，则不知道应该使用哪一个，也抛出异常
		if (beanNames.length > 1) {
			throw new ApplicationContextException("Unable to start ReactiveWebApplicationContext due to multiple "
					+ "ReactiveWebServerFactory beans : " + StringUtils.arrayToCommaDelimitedString(beanNames));
		}
		//如果只有一个，则直接返回对应的beanName
		return beanNames[0];
	}

	protected ReactiveWebServerFactory getWebServerFactory(String factoryBeanName) {
		return getBeanFactory().getBean(factoryBeanName, ReactiveWebServerFactory.class);
	}

	/**
	 * Return the {@link ReactiveWebServerFactory} that should be used to create the
	 * reactive web server. By default this method searches for a suitable bean in the
	 * context itself.
	 * @return a {@link ReactiveWebServerFactory} (never {@code null})
	 * @deprecated since 2.2.0 in favor of {@link #getWebServerFactoryBeanName()} and
	 * {@link #getWebServerFactory(String)}
	 */
	@Deprecated
	protected ReactiveWebServerFactory getWebServerFactory() {
		return getWebServerFactory(getWebServerFactoryBeanName());
	}
	/**在spring容器初始化完成时，启动WebServer*/
	@Override
	protected void finishRefresh() {
		//调用父类方法
		super.finishRefresh();
		//启动Reactive WebServer
		WebServer webServer = startReactiveWebServer();
		//如果启动成功，则发布ReactiveWebServerInitializedEvent事件
		if (webServer != null) {
			publishEvent(new ReactiveWebServerInitializedEvent(webServer, this));
		}
	}
	/**启动Reactive WebServer*/
	private WebServer startReactiveWebServer() {
		//获取当前容器中的serverManager属性
		ServerManager serverManager = this.serverManager;
		//获取HttpHandler对象，并使用其作为参数启动WebServer
		ServerManager.start(serverManager, this::getHttpHandler);
		//获取WebServer，并返回
		return ServerManager.getWebServer(serverManager);
	}

	/**
	 * Return the {@link HttpHandler} that should be used to process the reactive web
	 * server. By default this method searches for a suitable bean in the context itself.
	 * @return a {@link HttpHandler} (never {@code null}
	 * 获取HttpHandler对象
	 */
	protected HttpHandler getHttpHandler() {
		// Use bean names so that we don't consider the hierarchy
		//从当前容器中获取HttpHandler类型对应的bean的名字们
		String[] beanNames = getBeanFactory().getBeanNamesForType(HttpHandler.class);
		//0个，则抛出异常，因为最少要有一个
		if (beanNames.length == 0) {
			throw new ApplicationContextException(
					"Unable to start ReactiveWebApplicationContext due to missing HttpHandler bean.");
		}
		//多个，因为无法确定初始化哪一个，所以也抛出对应异常
		if (beanNames.length > 1) {
			throw new ApplicationContextException(
					"Unable to start ReactiveWebApplicationContext due to multiple HttpHandler beans : "
							+ StringUtils.arrayToCommaDelimitedString(beanNames));
		}
		//从当前容器中获取HttpHandler类型对应的Bean对象，并返回
		return getBeanFactory().getBean(beanNames[0], HttpHandler.class);
	}
	/**在spring容器关闭时，关闭WebServer*/
	@Override
	protected void onClose() {
		//调用父类方法
		super.onClose();
		//关闭Reactive WebServer，并释放对用资源
		stopAndReleaseReactiveWebServer();
	}

	private void stopAndReleaseReactiveWebServer() {
		//获取当前容器中的serverManager服务管理器
		ServerManager serverManager = this.serverManager;
		try {
			//调用管理器的stop方法，来停止Reactive WebServer
			ServerManager.stop(serverManager);
		}
		finally {
			//清空当前容器的serverManager属性值（为了让此变量可以得到释放）
			this.serverManager = null;
		}
	}

	/**
	 * Returns the {@link WebServer} that was created by the context or {@code null} if
	 * the server has not yet been created.
	 * @return the web server
	 */
	@Override
	public WebServer getWebServer() {
		return ServerManager.getWebServer(this.serverManager);
	}

	@Override
	public String getServerNamespace() {
		return this.serverNamespace;
	}

	@Override
	public void setServerNamespace(String serverNamespace) {
		this.serverNamespace = serverNamespace;
	}

	/**
	 * {@link HttpHandler} that initializes its delegate on first request.
	 * 此类为HttpHandler的延迟类，此类对http请求的处理是委托其设置的委托器中设置HttpHandler的handle方法来处理的
	 */
	private static final class LazyHttpHandler implements HttpHandler {

		private final Mono<HttpHandler> delegate;

		private LazyHttpHandler(Mono<HttpHandler> delegate) {
			this.delegate = delegate;
		}

		@Override
		public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
			return this.delegate.flatMap((handler) -> handler.handle(request, response));
		}

	}

	/**
	 * Internal class used to manage the server and the {@link HttpHandler}, taking care
	 * not to initialize the handler too early.
	 * Server的管理器
	 */
	static final class ServerManager implements HttpHandler {
		/**WebServer对象*/
		private final WebServer server;
		/**是否延迟初始化*/
		private final boolean lazyInit;
		/**HttpHandler对象*/
		private volatile HttpHandler handler;

		private ServerManager(ReactiveWebServerFactory factory, boolean lazyInit) {
			this.handler = this::handleUninitialized;//此处同下面注释操作相同
			//          this.handler = new HttpHandler() {
//                @Override
//                public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
//                    return handleUninitialized(request, response);
//                }
//            };
			this.server = factory.getWebServer(this);
			this.lazyInit = lazyInit;
		}
		/**因为此时的WebServer对象刚初始化完成，并未启动，所以此处抛出异常表示不可用*/
		private Mono<Void> handleUninitialized(ServerHttpRequest request, ServerHttpResponse response) {
			throw new IllegalStateException("The HttpHandler has not yet been initialized");
		}
		/**服务用来处理请求，此处将此操作委托给了handler属性的对应方法进行处理*/
		@Override
		public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
			return this.handler.handle(request, response);
		}

		HttpHandler getHandler() {
			return this.handler;
		}
		/**创建一个ServerManager对象，并返回*/
		static ServerManager get(ReactiveWebServerFactory factory, boolean lazyInit) {
			return new ServerManager(factory, lazyInit);
		}
		/**从给定的manager对象中获取WebServer对象*/
		static WebServer getWebServer(ServerManager manager) {
			return (manager != null) ? manager.server : null;
		}
		/**启动*/
		static void start(ServerManager manager, Supplier<HttpHandler> handlerSupplier) {
			//如果当前存在已创建可以启动的server对象，则进行启动
			if (manager != null && manager.server != null) {
				//根据是否延迟初始化变量，来给manager的HttpHandler对象属性设置对应的值
				manager.handler = manager.lazyInit ? new LazyHttpHandler(Mono.fromSupplier(handlerSupplier))
						: handlerSupplier.get();
				//启动WebServer
				manager.server.start();
			}
		}
		/**停止服务*/
		static void stop(ServerManager manager) {
			if (manager != null && manager.server != null) {
				try {
					//停止server
					manager.server.stop();
				}
				catch (Exception ex) {
					throw new IllegalStateException(ex);
				}
			}
		}

	}

}
