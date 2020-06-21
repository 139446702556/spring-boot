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

package org.springframework.boot.autoconfigure.condition;

import java.util.Map;

import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.web.reactive.context.ConfigurableReactiveWebEnvironment;
import org.springframework.boot.web.reactive.context.ReactiveWebApplicationContext;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.WebApplicationContext;

/**
 * {@link Condition} that checks for the presence or absence of
 * {@link WebApplicationContext}.
 * 用于匹配@ConditionalOnWebApplication和@ConditionalOnNotWebApplication两个
 * 注解使用的条件匹配实现类
 * @author Dave Syer
 * @author Phillip Webb
 * @see ConditionalOnWebApplication
 * @see ConditionalOnNotWebApplication
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class OnWebApplicationCondition extends FilteringSpringBootCondition {

	private static final String SERVLET_WEB_APPLICATION_CLASS = "org.springframework.web.context.support.GenericWebApplicationContext";

	private static final String REACTIVE_WEB_APPLICATION_CLASS = "org.springframework.web.reactive.HandlerResult";
	/**此方法实现于FilteringSpringBootCondition抽象类中，主要用于帮助判断哪些自动配置类需要引入*/
	@Override
	protected ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		//创建ConditionOutcome数组（用于存储匹配结果）
		ConditionOutcome[] outcomes = new ConditionOutcome[autoConfigurationClasses.length];
		//遍历autoConfigurationClasses数组，进行逐个匹配
		for (int i = 0; i < outcomes.length; i++) {
			//获取自动配置类
			String autoConfigurationClass = autoConfigurationClasses[i];
			//执行匹配（匹配@ConditionalOnWebApplication注解设置的条件）
			if (autoConfigurationClass != null) {
				outcomes[i] = getOutcome(
						autoConfigurationMetadata.get(autoConfigurationClass, "ConditionalOnWebApplication"));
			}
		}
		//返回匹配结果
		return outcomes;
	}
	/**进行注解条件匹配*/
	private ConditionOutcome getOutcome(String type) {
		//如果给定条件为null，则默认为匹配，返回null
		if (type == null) {
			return null;
		}
		//创建注解对应的匹配信息builder对象
		ConditionMessage.Builder message = ConditionMessage.forCondition(ConditionalOnWebApplication.class);
		//如果要求是SERVLET类型，但是当前项目的结果不存在SERVLET_WEB_APPLICATION_CLASS类，则返回不匹配
		if (ConditionalOnWebApplication.Type.SERVLET.name().equals(type)) {
			if (!ClassNameFilter.isPresent(SERVLET_WEB_APPLICATION_CLASS, getBeanClassLoader())) {
				return ConditionOutcome.noMatch(message.didNotFind("servlet web application classes").atAll());
			}
		}
		//如果要求是REACTIVE类型的，但是当前项目的结果不存在REACTIVE_WEB_APPLICATION_CLASS类，则返回不匹配
		if (ConditionalOnWebApplication.Type.REACTIVE.name().equals(type)) {
			if (!ClassNameFilter.isPresent(REACTIVE_WEB_APPLICATION_CLASS, getBeanClassLoader())) {
				return ConditionOutcome.noMatch(message.didNotFind("reactive web application classes").atAll());
			}
		}
		//如果当前项目中SERVLET_WEB_APPLICATION_CLASS和REACTIVE_WEB_APPLICATION_CLASS两个类都不存在，则直接返回不匹配
		if (!ClassNameFilter.isPresent(SERVLET_WEB_APPLICATION_CLASS, getBeanClassLoader())
				&& !ClassUtils.isPresent(REACTIVE_WEB_APPLICATION_CLASS, getBeanClassLoader())) {
			return ConditionOutcome.noMatch(message.didNotFind("reactive or servlet web application classes").atAll());
		}
		//如果匹配，返回null
		return null;
	}
	/**此方法实现于SpringBootCondition抽象类*/
	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		//通过判断是否有@ConditionalOnWebApplication注解，来判断当前是否要求要在web环境下
		boolean required = metadata.isAnnotated(ConditionalOnWebApplication.class.getName());
		//判断是否匹配web环境
		ConditionOutcome outcome = isWebApplication(context, metadata, required);
		//如果要求在web环境下，但是当前不是web环境，则返回不匹配
		if (required && !outcome.isMatch()) {
			return ConditionOutcome.noMatch(outcome.getConditionMessage());
		}
		//如果不要求在web环境下（即有@ConditionalOnNotWebApplication注解），但是当前结果匹配是在web环境下，则返回不匹配
		if (!required && outcome.isMatch()) {
			return ConditionOutcome.noMatch(outcome.getConditionMessage());
		}
		//返回匹配
		return ConditionOutcome.match(outcome.getConditionMessage());
	}

	private ConditionOutcome isWebApplication(ConditionContext context, AnnotatedTypeMetadata metadata,
			boolean required) {
		//获得要求的web类型
		switch (deduceType(metadata)) {
		case SERVLET:
			//判断是否为Servlet Web环境
			return isServletWebApplication(context);
		case REACTIVE:
			//判断是否为Reactive Web环境
			return isReactiveWebApplication(context);
		default:
			//判断是否为任意web环境
			return isAnyWebApplication(context, required);
		}
	}

	private ConditionOutcome isAnyWebApplication(ConditionContext context, boolean required) {
		ConditionMessage.Builder message = ConditionMessage.forCondition(ConditionalOnWebApplication.class,
				required ? "(required)" : "");
		//如果当前是Servlet环境，并且要求在WEB环境，则返回匹配
		ConditionOutcome servletOutcome = isServletWebApplication(context);
		if (servletOutcome.isMatch() && required) {
			return new ConditionOutcome(servletOutcome.isMatch(), message.because(servletOutcome.getMessage()));
		}
		//如果当前是Reactive环境，并且要求在Web环境，则返回匹配
		ConditionOutcome reactiveOutcome = isReactiveWebApplication(context);
		if (reactiveOutcome.isMatch() && required) {
			return new ConditionOutcome(reactiveOutcome.isMatch(), message.because(reactiveOutcome.getMessage()));
		}
		//根据上述匹配情况，来返回是否匹配
		return new ConditionOutcome(servletOutcome.isMatch() || reactiveOutcome.isMatch(),
				message.because(servletOutcome.getMessage()).append("and").append(reactiveOutcome.getMessage()));
	}
	/**判断是否为Servlet Web环境*/
	private ConditionOutcome isServletWebApplication(ConditionContext context) {
		ConditionMessage.Builder message = ConditionMessage.forCondition("");
		//判断当前是否存在SERVLET_WEB_APPLICATION_CLASS类，如果不存在，则返回不匹配
		if (!ClassNameFilter.isPresent(SERVLET_WEB_APPLICATION_CLASS, context.getClassLoader())) {
			return ConditionOutcome.noMatch(message.didNotFind("servlet web application classes").atAll());
		}
		//如果当前上下文的beanFactory中存在session scope，则返回匹配
		if (context.getBeanFactory() != null) {
			String[] scopes = context.getBeanFactory().getRegisteredScopeNames();
			if (ObjectUtils.containsElement(scopes, "session")) {
				return ConditionOutcome.match(message.foundExactly("'session' scope"));
			}
		}
		//如果environment是ConfigurableWebEnvironment类型的，则返回匹配
		if (context.getEnvironment() instanceof ConfigurableWebEnvironment) {
			return ConditionOutcome.match(message.foundExactly("ConfigurableWebEnvironment"));
		}
		//如果resourceLoader是WebApplicationContext类型，则返回匹配
		if (context.getResourceLoader() instanceof WebApplicationContext) {
			return ConditionOutcome.match(message.foundExactly("WebApplicationContext"));
		}
		//如果当前上下文中的配置不满足上述全部要求，则返回不匹配
		return ConditionOutcome.noMatch(message.because("not a servlet web application"));
	}

	private ConditionOutcome isReactiveWebApplication(ConditionContext context) {
		ConditionMessage.Builder message = ConditionMessage.forCondition("");
		//如果不存在REACTIVE_WEB_APPLICATION_CLASS类，则返回不匹配
		if (!ClassNameFilter.isPresent(REACTIVE_WEB_APPLICATION_CLASS, context.getClassLoader())) {
			return ConditionOutcome.noMatch(message.didNotFind("reactive web application classes").atAll());
		}
		//如果environment为ConfigurableReactiveWebEnvironment类型，则返回匹配
		if (context.getEnvironment() instanceof ConfigurableReactiveWebEnvironment) {
			return ConditionOutcome.match(message.foundExactly("ConfigurableReactiveWebEnvironment"));
		}
		//如果resourceLoader是ReactiveWebApplicationContext类型的，则返回匹配
		if (context.getResourceLoader() instanceof ReactiveWebApplicationContext) {
			return ConditionOutcome.match(message.foundExactly("ReactiveWebApplicationContext"));
		}
		//如果当前不满足上述全部要求，则返回不匹配
		return ConditionOutcome.noMatch(message.because("not a reactive web application"));
	}
	/**获得要求的web类型*/
	private Type deduceType(AnnotatedTypeMetadata metadata) {
		//获取@ConditionalOnWebApplication注解的属性
		Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnWebApplication.class.getName());
		//获取此注解的type属性值
		if (attributes != null) {
			return (Type) attributes.get("type");
		}
		//在没有此注解属性的时候，返回的默认类型值
		return Type.ANY;
	}

}
