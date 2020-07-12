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

package org.springframework.boot.context;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * {@link ApplicationContextInitializer} that sets the Spring
 * {@link ApplicationContext#getId() ApplicationContext ID}. The
 * {@code spring.application.name} property is used to create the ID. If the property is
 * not set {@code application} is used.
 *
 * @author Dave Syer
 * @author Andy Wilkinson
 * @since 1.0.0
 * 负责生成spring容器的编号
 */
public class ContextIdApplicationContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {
	/**排序使用的优先级*/
	private int order = Ordered.LOWEST_PRECEDENCE - 10;

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		//获得（创建）ContextId对象
		ContextId contextId = getContextId(applicationContext);
		//将contextId设置到applicationContext中
		applicationContext.setId(contextId.getId());
		//将ContextId类型的对象注册到spring的bean容器当中
		applicationContext.getBeanFactory().registerSingleton(ContextId.class.getName(), contextId);
	}

	private ContextId getContextId(ConfigurableApplicationContext applicationContext) {
		//获取父applicationContext对象
		ApplicationContext parent = applicationContext.getParent();
		//情况一、如果当前上下文有父ApplicationContext存在，并且其有ContextId对象，则使用其父类的此对象来生成当前容器的ContextId对象
		if (parent != null && parent.containsBean(ContextId.class.getName())) {
			return parent.getBean(ContextId.class).createChildId();
		}
		//情况二、如果其没有父容器或者父容器没有ContextId对象存在，则直接创建当前容器ContextId对象，并返回
		return new ContextId(getApplicationId(applicationContext.getEnvironment()));
	}

	private String getApplicationId(ConfigurableEnvironment environment) {
		String name = environment.getProperty("spring.application.name");
		return StringUtils.hasText(name) ? name : "application";
	}

	/**
	 * The ID of a context.
	 * 此类为spring容器编号的封装
	 */
	static class ContextId {
		/**递增序号，用于生成当前上下文的子Context的编号而使用*/
		private final AtomicLong children = new AtomicLong(0);
		/**编号*/
		private final String id;

		ContextId(String id) {
			this.id = id;
		}
		/**创建当前上下文的子Context的编号*/
		ContextId createChildId() {
			return new ContextId(this.id + "-" + this.children.incrementAndGet());
		}

		String getId() {
			return this.id;
		}

	}

}
