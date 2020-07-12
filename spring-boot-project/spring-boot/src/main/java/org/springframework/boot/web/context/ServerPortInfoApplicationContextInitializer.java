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

package org.springframework.boot.web.context;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

/**
 * {@link ApplicationContextInitializer} that sets {@link Environment} properties for the
 * ports that {@link WebServer} servers are actually listening on. The property
 * {@literal "local.server.port"} can be injected directly into tests using
 * {@link Value @Value} or obtained via the {@link Environment}.
 * <p>
 * If the {@link WebServerInitializedEvent} has a
 * {@link WebServerApplicationContext#getServerNamespace() server namespace} , it will be
 * used to construct the property name. For example, the "management" actuator context
 * will have the property name {@literal "local.management.port"}.
 * <p>
 * Properties are automatically propagated up to any parent context.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @since 2.0.0
 * 此类监听了EmbeaddedServletContainerInitializedEvent类型的事件，然后将内嵌的web服务器使用的端口给设置到ApplicationContext中
 */
public class ServerPortInfoApplicationContextInitializer implements
		ApplicationContextInitializer<ConfigurableApplicationContext>, ApplicationListener<WebServerInitializedEvent> {

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		//将自身作为ApplicationListener监听器添加到容器中
		applicationContext.addApplicationListener(this);
	}
	/**当监听到WebServerInitializedEvent事件时进行触发此方法*/
	@Override
	public void onApplicationEvent(WebServerInitializedEvent event) {
		//获得属性名
		String propertyName = "local." + getName(event.getApplicationContext()) + ".port";
		//设置触发当前方法的事件所对应的服务端口到environment的propertyName中
		setPortProperty(event.getApplicationContext(), propertyName, event.getWebServer().getPort());
	}

	private String getName(WebServerApplicationContext context) {
		String name = context.getServerNamespace();
		return StringUtils.hasText(name) ? name : "server";
	}

	private void setPortProperty(ApplicationContext context, String propertyName, int port) {
		//设置端口到environment的propertyName中
		if (context instanceof ConfigurableApplicationContext) {
			setPortProperty(((ConfigurableApplicationContext) context).getEnvironment(), propertyName, port);
		}
		//如果当前上下文容器有父类容器，则继续将端口设置到父类的环境变量中
		if (context.getParent() != null) {
			setPortProperty(context.getParent(), propertyName, port);
		}
	}

	@SuppressWarnings("unchecked")
	private void setPortProperty(ConfigurableEnvironment environment, String propertyName, int port) {
		//获取给定环境变量对象中的PropertySources属性
		MutablePropertySources sources = environment.getPropertySources();
		//获取名字为“server.ports”的属性的值
		PropertySource<?> source = sources.get("server.ports");
		//如果当前environment中不存在此属性，则初始化一个PropertySource对象，并将其添加到environment中
		if (source == null) {
			source = new MapPropertySource("server.ports", new HashMap<>());
			sources.addFirst(source);
		}
		//将当前服务的端口添加到source中
		((Map<String, Object>) source.getSource()).put(propertyName, port);
	}

}
