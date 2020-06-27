/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.boot.web.servlet.context;

import java.util.Collection;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.web.context.ConfigurableWebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.context.support.ServletContextAwareProcessor;
import org.springframework.web.context.support.ServletContextResource;
import org.springframework.web.context.support.ServletContextScope;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * A {@link WebApplicationContext} that can be used to bootstrap itself from a contained
 * {@link ServletWebServerFactory} bean.
 * <p>
 * This context will create, initialize and run an {@link WebServer} by searching for a
 * single {@link ServletWebServerFactory} bean within the {@link ApplicationContext}
 * itself. The {@link ServletWebServerFactory} is free to use standard Spring concepts
 * (such as dependency injection, lifecycle callbacks and property placeholder variables).
 * <p>
 * In addition, any {@link Servlet} or {@link Filter} beans defined in the context will be
 * automatically registered with the web server. In the case of a single Servlet bean, the
 * '/' mapping will be used. If multiple Servlet beans are found then the lowercase bean
 * name will be used as a mapping prefix. Any Servlet named 'dispatcherServlet' will
 * always be mapped to '/'. Filter beans will be mapped to all URLs ('/*').
 * <p>
 * For more advanced configuration, the context can instead define beans that implement
 * the {@link ServletContextInitializer} interface (most often
 * {@link ServletRegistrationBean}s and/or {@link FilterRegistrationBean}s). To prevent
 * double registration, the use of {@link ServletContextInitializer} beans will disable
 * automatic Servlet and Filter bean registration.
 * <p>
 * Although this context can be used directly, most developers should consider using the
 * {@link AnnotationConfigServletWebServerApplicationContext} or
 * {@link XmlServletWebServerApplicationContext} variants.
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Scott Frederick
 * @since 2.0.0
 * @see AnnotationConfigServletWebServerApplicationContext
 * @see XmlServletWebServerApplicationContext
 * @see ServletWebServerFactory
 * 此类为spring boot 使用servlet web服务器的ApplicationContext实现类
 */
public class ServletWebServerApplicationContext extends GenericWebApplicationContext
		implements ConfigurableWebServerApplicationContext {

	private static final Log logger = LogFactory.getLog(ServletWebServerApplicationContext.class);

	/**
	 * Constant value for the DispatcherServlet bean name. A Servlet bean with this name
	 * is deemed to be the "main" servlet and is automatically given a mapping of "/" by
	 * default. To change the default behavior you can use a
	 * {@link ServletRegistrationBean} or a different bean name.
	 */
	public static final String DISPATCHER_SERVLET_NAME = "dispatcherServlet";
	/**spring webServer对象*/
	private volatile WebServer webServer;
	/**spring servlet的配置对象*/
	private ServletConfig servletConfig;
	/**server的命名空间，通过setServerNamespace方法来注入*/
	private String serverNamespace;

	/**
	 * Create a new {@link ServletWebServerApplicationContext}.
	 */
	public ServletWebServerApplicationContext() {
	}

	/**
	 * Create a new {@link ServletWebServerApplicationContext} with the given
	 * {@code DefaultListableBeanFactory}.
	 * @param beanFactory the DefaultListableBeanFactory instance to use for this context
	 */
	public ServletWebServerApplicationContext(DefaultListableBeanFactory beanFactory) {
		super(beanFactory);
	}

	/**
	 * Register ServletContextAwareProcessor.
	 * 注册ServletContextAwareProcessor组件
	 * @see ServletContextAwareProcessor
	 */
	@Override
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		//注册WebApplicationContextServletContextAwareProcessor后置处理器到当前beanFactory容器中
		beanFactory.addBeanPostProcessor(new WebApplicationContextServletContextAwareProcessor(this));
		//忽略ServletContextAware接口
		beanFactory.ignoreDependencyInterface(ServletContextAware.class);
		//注册ExistingWebApplicationScopes和web应用程序相关的scope到当前WebApplicationContext中
		registerWebApplicationScopes();
	}
	/**初始化spring容器*/
	@Override
	public final void refresh() throws BeansException, IllegalStateException {
		try {
			//调用父类AbstractApplicationContext中的refresh方法
			super.refresh();
		}
		catch (RuntimeException ex) {
			//如果发生异常，则停止WebServer，并释放相关资源
			stopAndReleaseWebServer();
			throw ex;
		}
	}
	/**在spring容器初始化时，完成WebServer的创建（不包括启动）*/
	@Override
	protected void onRefresh() {
		//调用父类对应方法
		super.onRefresh();
		try {
			//创建WebServer对象
			createWebServer();
		}
		catch (Throwable ex) {
			throw new ApplicationContextException("Unable to start web server", ex);
		}
	}
	/**用于在容器初始化完成时，启动WebServer*/
	@Override
	protected void finishRefresh() {
		//调用父类的相关方法
		super.finishRefresh();
		//启动WebServer
		WebServer webServer = startWebServer();
		//如果启动WebServer成功，则进行发布ServletWebServerInitializedEvent事件
		if (webServer != null) {
			publishEvent(new ServletWebServerInitializedEvent(webServer, this));
		}
	}
	/**实现此方法为了在spring容器关闭时，关闭WebServer*/
	@Override
	protected void onClose() {
		//调用父类方法
		super.onClose();
		//停止并释放WebServer
		stopAndReleaseWebServer();
	}
	/**创建WebServer对象*/
	private void createWebServer() {
		//获取当前上下文容器中存储的webServer对象
		WebServer webServer = this.webServer;
		//获取当前容器中的ServletContext对象
		ServletContext servletContext = getServletContext();
		//如果webServer和serlverContext皆为空，说明未初始化
		if (webServer == null && servletContext == null) {
			//获取ServletWebServerFactory对象
			ServletWebServerFactory factory = getWebServerFactory();
			//先获取ServletContextInitializer对象，然后创建webServer对象
			this.webServer = factory.getWebServer(getSelfInitializer());
		}
		//如果servletContext不为空，则对其进行一些启动前的初始化操作
		else if (servletContext != null) {
			try {
				getSelfInitializer().onStartup(servletContext);
			}
			catch (ServletException ex) {
				throw new ApplicationContextException("Cannot initialize servlet context", ex);
			}
		}
		//初始化PropertySource
		initPropertySources();
	}

	/**
	 * Returns the {@link ServletWebServerFactory} that should be used to create the
	 * embedded {@link WebServer}. By default this method searches for a suitable bean in
	 * the context itself.
	 * 此方法默认获得的应该是TomcatServletWebServerFactory，因为此类是spring boot引入是默认引入的
	 * @return a {@link ServletWebServerFactory} (never {@code null})
	 */
	protected ServletWebServerFactory getWebServerFactory() {
		// Use bean names so that we don't consider the hierarchy
		//从当前上下文容器中获取类型为ServletWebServerFactory类型对应的bean的名称们
		String[] beanNames = getBeanFactory().getBeanNamesForType(ServletWebServerFactory.class);
		//如果不存在，则抛出异常，因为至少应该有一个
		if (beanNames.length == 0) {
			throw new ApplicationContextException("Unable to start ServletWebServerApplicationContext due to missing "
					+ "ServletWebServerFactory bean.");
		}
		//如果当前容器中有多个此类型的bean，则抛出异常，因为不知道应该初始化哪个
		if (beanNames.length > 1) {
			throw new ApplicationContextException("Unable to start ServletWebServerApplicationContext due to multiple "
					+ "ServletWebServerFactory beans : " + StringUtils.arrayToCommaDelimitedString(beanNames));
		}
		//从当前上下文容器中获取ServletWebServerFactory类型的bean对象
		return getBeanFactory().getBean(beanNames[0], ServletWebServerFactory.class);
	}

	/**
	 * Returns the {@link ServletContextInitializer} that will be used to complete the
	 * setup of this {@link WebApplicationContext}.
	 * @return the self initializer
	 * @see #prepareWebApplicationContext(ServletContext)
	 */
	private org.springframework.boot.web.servlet.ServletContextInitializer getSelfInitializer() {
		//此处实现和下方注释一致，在webServer创建之后进行初始化的时候，会调用OnStartup方法
		//进而调用此处的selfInitialize方法
		return this::selfInitialize;
		/*return new ServletContextInitializer() {
			@Override
			public void onStartup(ServletContext servletContext) throws ServletException {
				selfInitialize(servletContext);
			}
		};*/
	}
	/**初始化WebServer*/
	private void selfInitialize(ServletContext servletContext) throws ServletException {
		//添加Spring容器（WebApplicationContext）到servletContext属性中
		prepareWebApplicationContext(servletContext);
		//注册servletContextScope
		registerApplicationScope(servletContext);
		//注册web-specific environment beans （向当前spring容器中注册contextParameters、contextAttributes等bean对象）
		WebApplicationContextUtils.registerEnvironmentBeans(getBeanFactory(), servletContext);
		//遍历所有的ServletContextInitializer，并逐个启动它们
		for (ServletContextInitializer beans : getServletContextInitializerBeans()) {
			beans.onStartup(servletContext);
		}
	}

	private void registerApplicationScope(ServletContext servletContext) {
		//创建ServletContextScope对象
		ServletContextScope appScope = new ServletContextScope(servletContext);
		//将创建的ServletContextScope对象注册到spring容器中
		getBeanFactory().registerScope(WebApplicationContext.SCOPE_APPLICATION, appScope);
		// Register as ServletContext attribute, for ContextCleanupListener to detect it.
		//将servletContextScope对象设置到servletContext对应的属性中
		servletContext.setAttribute(ServletContextScope.class.getName(), appScope);
	}

	private void registerWebApplicationScopes() {
		//创建一个新的ExistingWebApplicationScopes对象
		ExistingWebApplicationScopes existingScopes = new ExistingWebApplicationScopes(getBeanFactory());
		//注册webApplication相关scope到当前的web应用程序上下文中
		WebApplicationContextUtils.registerWebApplicationScopes(getBeanFactory());
		//恢复（注册ExistingWebApplicationScopes到WebApplicationContext中）
		existingScopes.restore();
	}

	/**
	 * Returns {@link ServletContextInitializer}s that should be used with the embedded
	 * web server. By default this method will first attempt to find
	 * {@link ServletContextInitializer}, {@link Servlet}, {@link Filter} and certain
	 * {@link EventListener} beans.
	 * @return the servlet initializer beans
	 */
	protected Collection<ServletContextInitializer> getServletContextInitializerBeans() {
		return new ServletContextInitializerBeans(getBeanFactory());
	}

	/**
	 * Prepare the {@link WebApplicationContext} with the given fully loaded
	 * {@link ServletContext}. This method is usually called from
	 * {@link ServletContextInitializer#onStartup(ServletContext)} and is similar to the
	 * functionality usually provided by a {@link ContextLoaderListener}.
	 * 准备WebApplicationContext环境
	 * 检查当前给定servletContext中是否有初始化spring容器，如果有的话，则进行检查
	 * 如果没有则进行相关设置
	 * @param servletContext the operational servlet context
	 */
	protected void prepareWebApplicationContext(ServletContext servletContext) {
		//从servletContext中获取Root WebApplicationContext
		Object rootContext = servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		//如果此servletContext中已存在spring容器
		if (rootContext != null) {
			//判断servletContext中的rootContext容器和当前this是否是相同的容器
			//如果是相同的容器，则抛出异常，说明可能有重复的ServletContextInitializers
			if (rootContext == this) {
				throw new IllegalStateException(
						"Cannot initialize context because there is already a root application context present - "
								+ "check whether you have multiple ServletContextInitializers!");
			}
			//如果不存在相同的容器，则直接返回
			return;
		}
		//记录初始化spring容器日志
		servletContext.log("Initializing Spring embedded WebApplicationContext");
		try {
			//将当前容器设置为给定servletContext对象的Root WebApplicationContext属性中
			servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, this);
			//记录日志
			if (logger.isDebugEnabled()) {
				logger.debug("Published root WebApplicationContext as ServletContext attribute with name ["
						+ WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE + "]");
			}
			//将给定的servletContext对象设置到当前容器的对应属性中
			setServletContext(servletContext);
			//记录日志
			if (logger.isInfoEnabled()) {
				long elapsedTime = System.currentTimeMillis() - getStartupDate();
				logger.info("Root WebApplicationContext: initialization completed in " + elapsedTime + " ms");
			}
		}
		catch (RuntimeException | Error ex) {
			//如果上下文容器初始化失败，则打印日志，并将相关异常记录到servletContext的对应ROOT属性中
			logger.error("Context initialization failed", ex);
			servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, ex);
			throw ex;
		}
	}

	private WebServer startWebServer() {
		//获取当前的webServer对象
		WebServer webServer = this.webServer;
		//如果此对象已经初始化过（存在），则进行启动
		if (webServer != null) {
			webServer.start();
		}
		//返回webServer对象
		return webServer;
	}
	/**停止WebServer*/
	private void stopAndReleaseWebServer() {
		//获取WebServer对象，避免被多线程修改
		WebServer webServer = this.webServer;
		//如果当前上下文中存在webServer对象
		if (webServer != null) {
			try {
				//停止webServer对象
				webServer.stop();
				//置空webServer对象（目的是为了方便GC回收资源）
				this.webServer = null;
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}
	}

	@Override
	protected Resource getResourceByPath(String path) {
		if (getServletContext() == null) {
			return new ClassPathContextResource(path, getClassLoader());
		}
		return new ServletContextResource(getServletContext(), path);
	}

	@Override
	public String getServerNamespace() {
		return this.serverNamespace;
	}

	@Override
	public void setServerNamespace(String serverNamespace) {
		this.serverNamespace = serverNamespace;
	}

	@Override
	public void setServletConfig(ServletConfig servletConfig) {
		this.servletConfig = servletConfig;
	}

	@Override
	public ServletConfig getServletConfig() {
		return this.servletConfig;
	}

	/**
	 * Returns the {@link WebServer} that was created by the context or {@code null} if
	 * the server has not yet been created.
	 * @return the embedded web server
	 */
	@Override
	public WebServer getWebServer() {
		return this.webServer;
	}

	/**
	 * Utility class to store and restore any user defined scopes. This allow scopes to be
	 * registered in an ApplicationContextInitializer in the same way as they would in a
	 * classic non-embedded web application context.
	 */
	public static class ExistingWebApplicationScopes {

		private static final Set<String> SCOPES;

		static {
			Set<String> scopes = new LinkedHashSet<>();
			scopes.add(WebApplicationContext.SCOPE_REQUEST);
			scopes.add(WebApplicationContext.SCOPE_SESSION);
			SCOPES = Collections.unmodifiableSet(scopes);
		}

		private final ConfigurableListableBeanFactory beanFactory;

		private final Map<String, Scope> scopes = new HashMap<>();

		public ExistingWebApplicationScopes(ConfigurableListableBeanFactory beanFactory) {
			this.beanFactory = beanFactory;
			//遍历当前的SCOPES数组，将其的scopeName和对应的scope对象存入scopes容器中
			for (String scopeName : SCOPES) {
				Scope scope = beanFactory.getRegisteredScope(scopeName);
				if (scope != null) {
					this.scopes.put(scopeName, scope);
				}
			}
		}

		public void restore() {
			//将scopes容器中的相关scope对象注册回beanFactory容器中
			this.scopes.forEach((key, value) -> {
				if (logger.isInfoEnabled()) {
					logger.info("Restoring user defined scope " + key);
				}
				this.beanFactory.registerScope(key, value);
			});
		}

	}

}
