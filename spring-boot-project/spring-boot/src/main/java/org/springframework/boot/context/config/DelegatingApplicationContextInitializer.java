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

package org.springframework.boot.context.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * {@link ApplicationContextInitializer} that delegates to other initializers that are
 * specified under a {@literal context.initializer.classes} environment property.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @since 1.0.0
 * 此类的作用是将初始化过程交给 环境变量中配置的context.initializer.classes属性值对应的ApplicationContextInitializer类们进行
 */
public class DelegatingApplicationContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

	// NOTE: Similar to org.springframework.web.context.ContextLoader
	/**环境变量配置的属性名*/
	private static final String PROPERTY_NAME = "context.initializer.classes";
	/**默认优先级  为0 表示最高，排在全部的ApplicationContextInitializer中的最前面*/
	private int order = 0;

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		//获取当前应用程序上下文中的环境变量
		ConfigurableEnvironment environment = context.getEnvironment();
		//从环境变量中获取配置的ApplicationContextInitializer类集合们
		List<Class<?>> initializerClasses = getInitializerClasses(environment);
		//如果环境变量中配置了ApplicationContextInitializer类，及不为空，则开始进行初始化
		if (!initializerClasses.isEmpty()) {
			//进行ApplicationContextInitializer的初始化过程
			applyInitializerClasses(context, initializerClasses);
		}
	}
	/**从给定的环境变量中获取配置的ApplicationContextInitializer类集合们*/
	private List<Class<?>> getInitializerClasses(ConfigurableEnvironment env) {
		//获取环境变量中配置的属性名为PROPERTY_NAME的属性值
		String classNames = env.getProperty(PROPERTY_NAME);
		//存储配置的ApplicationContextInitializer类集合
		List<Class<?>> classes = new ArrayList<>();
		//如果属性设置了值
		if (StringUtils.hasLength(classNames)) {
			//因为类名在环境变量中是以逗号进行分隔的，所以按照逗号进行切分属性值，然后遍历所有的类名
			for (String className : StringUtils.tokenizeToStringArray(classNames, ",")) {
				//获取类名对应的类对象，并将其添加到classes容器中
				classes.add(getInitializerClass(className));
			}
		}
		return classes;
	}

	private Class<?> getInitializerClass(String className) throws LinkageError {
		try {
			//根据给定的全类名，去获取并加载其对应的类
			Class<?> initializerClass = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
			//断言加载的指定全类名的类是ApplicationContextInitializer类型
			Assert.isAssignable(ApplicationContextInitializer.class, initializerClass);
			//返回类对象
			return initializerClass;
		}
		catch (ClassNotFoundException ex) {
			throw new ApplicationContextException("Failed to load context initializer class [" + className + "]", ex);
		}
	}

	private void applyInitializerClasses(ConfigurableApplicationContext context, List<Class<?>> initializerClasses) {
		//获取上下文对象的类
		Class<?> contextClass = context.getClass();
		//用于存储全部给定的类对应的ApplicationContextInitializer
		List<ApplicationContextInitializer<?>> initializers = new ArrayList<>();
		//遍历给定的ApplicationContextInitializer的类集合
		for (Class<?> initializerClass : initializerClasses) {
			//实例化类，得到其对应的ApplicationContextInitializer的对象，并将其添加到initializers容器中
			initializers.add(instantiateInitializer(contextClass, initializerClass));
		}
		//应用初始化器，执行初始化逻辑
		applyInitializers(context, initializers);
	}

	private ApplicationContextInitializer<?> instantiateInitializer(Class<?> contextClass, Class<?> initializerClass) {
		//校验当前ApplicationContextInitializer的泛型类型（即初始化方法的参数类型）是否与当前上下文环境类型相匹配
		Class<?> requireContextClass = GenericTypeResolver.resolveTypeArgument(initializerClass,
				ApplicationContextInitializer.class);
		Assert.isAssignable(requireContextClass, contextClass,
				String.format(
						"Could not add context initializer [%s] as its generic parameter [%s] is not assignable "
								+ "from the type of application context used by this context loader [%s]: ",
						initializerClass.getName(), requireContextClass.getName(), contextClass.getName()));
		//根据ApplicationContextInitializer类进行实例化创建对象，并返回
		return (ApplicationContextInitializer<?>) BeanUtils.instantiateClass(initializerClass);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void applyInitializers(ConfigurableApplicationContext context,
			List<ApplicationContextInitializer<?>> initializers) {
		//初始化器集合进行排序（使用实现的Ordered接口或者注解）
		initializers.sort(new AnnotationAwareOrderComparator());
		//遍历给定的所有初始化器，执行其初始化逻辑
		for (ApplicationContextInitializer initializer : initializers) {
			initializer.initialize(context);
		}
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

}
