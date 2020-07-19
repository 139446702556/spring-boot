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

package org.springframework.boot.context.logging;

import java.net.URLClassLoader;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.ResolvableType;

/**
 * A {@link SmartApplicationListener} that reacts to
 * {@link ApplicationEnvironmentPreparedEvent environment prepared events} and to
 * {@link ApplicationFailedEvent failed events} by logging the classpath of the thread
 * context class loader (TCCL) at {@code DEBUG} level.
 *
 * @author Andy Wilkinson
 * @since 2.0.0
 * 在程序启动时，如果启动成功则记录相关日志并将classpath打印到debug日志中，启动失败时，则将失败信息和对应的classpath打印到debug日志中
 */
public final class ClasspathLoggingApplicationListener implements GenericApplicationListener {
	/**Listener在容器中的排序使用的权重*/
	private static final int ORDER = LoggingApplicationListener.DEFAULT_ORDER + 1;

	private static final Log logger = LogFactory.getLog(ClasspathLoggingApplicationListener.class);

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		//开启debug日志
		if (logger.isDebugEnabled()) {
			//如果是ApplicationEnvironmentPreparedEvent事件，则说明启动成功，打印成功相关日志到debug日志中
			if (event instanceof ApplicationEnvironmentPreparedEvent) {
				logger.debug("Application started with classpath: " + getClasspath());
			}
			//如果是ApplicationFailedEvent事件，则说明启动失败，打印失败日志到debug日志中
			else if (event instanceof ApplicationFailedEvent) {
				logger.debug("Application failed to start with classpath: " + getClasspath());
			}
		}
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

	@Override
	public boolean supportsEventType(ResolvableType resolvableType) {
		//使用ResolvableType类，可以解析当前传入的参数的泛型，从而得知事件类型
		Class<?> type = resolvableType.getRawClass();
		//如果未设置事件类型，即泛型，则直接返回false
		if (type == null) {
			return false;
		}
		//判断事件类型是否为ApplicationEnvironmentPreparedEvent类型事件或者ApplicationFailedEvent类型事件
		return ApplicationEnvironmentPreparedEvent.class.isAssignableFrom(type)
				|| ApplicationFailedEvent.class.isAssignableFrom(type);
	}
	/**获得classpath*/
	private String getClasspath() {
		//获取类加载器
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		//如果classLoader是URLClassLoader类型的加载器，则直接获取其加载到的资源路径
		if (classLoader instanceof URLClassLoader) {
			return Arrays.toString(((URLClassLoader) classLoader).getURLs());
		}
		//如果classLoader是其它类型，则直接返回unknown，表示无法获取到classpath
		return "unknown";
	}

}
