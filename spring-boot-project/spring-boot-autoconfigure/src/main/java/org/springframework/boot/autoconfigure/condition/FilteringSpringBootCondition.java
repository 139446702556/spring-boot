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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * Abstract base class for a {@link SpringBootCondition} that also implements
 * {@link AutoConfigurationImportFilter}.
 *
 * @author Phillip Webb
 * 此类为具有AutoConfigurationImportFilter功能的SpringBootCondition的抽象基类
 */
abstract class FilteringSpringBootCondition extends SpringBootCondition
		implements AutoConfigurationImportFilter, BeanFactoryAware, BeanClassLoaderAware {
	/**这两个属性是通过对应的Aware机制进行注入的*/
	private BeanFactory beanFactory;

	private ClassLoader beanClassLoader;
	/**执行批量的自动配置类注解条件的匹配，并且批量的返回匹配结果  此方法实现于AutoConfigurationImportFilter接口*/
	@Override
	public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
		//获得ConditionEvaluationReport对象
		ConditionEvaluationReport report = ConditionEvaluationReport.find(this.beanFactory);
		//执行批量的匹配，并返回匹配的结果
		ConditionOutcome[] outcomes = getOutcomes(autoConfigurationClasses, autoConfigurationMetadata);
		//创建match数组
		boolean[] match = new boolean[outcomes.length];
		//遍历匹配结果outcomes数组
		for (int i = 0; i < outcomes.length; i++) {
			//根据匹配结果来赋值match（匹配或者返回结果为空均认为匹配）
			match[i] = (outcomes[i] == null || outcomes[i].isMatch());
			//如果不匹配，则打印日志和记录
			if (!match[i] && outcomes[i] != null) {
				//记录相关日志
				logOutcome(autoConfigurationClasses[i], outcomes[i]);
				//记录
				if (report != null) {
					report.recordConditionEvaluation(autoConfigurationClasses[i], this, outcomes[i]);
				}
			}
		}
		//返回匹配结果match数组
		return match;
	}

	protected abstract ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata);

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	protected final BeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	protected final ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}
	/**通过使用ClassNameFilter类，过滤出符合条件的类名的数组*/
	protected final List<String> filter(Collection<String> classNames, ClassNameFilter classNameFilter,
			ClassLoader classLoader) {
		//如果给定的classNames数组为空，则直接返回空集合
		if (CollectionUtils.isEmpty(classNames)) {
			return Collections.emptyList();
		}
		//创建matches数组
		List<String> matches = new ArrayList<>(classNames.size());
		//遍历classNames数组，使用ClassNameFilter进行判断是否匹配，将匹配的添加到matches中
		for (String candidate : classNames) {
			if (classNameFilter.matches(candidate, classLoader)) {
				matches.add(candidate);
			}
		}
		//返回匹配的类名集合
		return matches;
	}

	/**
	 * Slightly faster variant of {@link ClassUtils#forName(String, ClassLoader)} that
	 * doesn't deal with primitives, arrays or inner types.
	 * 加载指定类
	 * @param className the class name to resolve
	 * @param classLoader the class loader to use
	 * @return a resolved class
	 * @throws ClassNotFoundException if the class cannot be found
	 */
	protected static Class<?> resolve(String className, ClassLoader classLoader) throws ClassNotFoundException {
		//如果给定的classLoader不为空，则只使用其在对应路径下来加载给类
		if (classLoader != null) {
			return classLoader.loadClass(className);
		}
		//否者在类路径下加载给定类
		return Class.forName(className);
	}
	/**提供判断类是否存在的功能*/
	protected enum ClassNameFilter {
		/**指定类存在*/
		PRESENT {

			@Override
			public boolean matches(String className, ClassLoader classLoader) {
				return isPresent(className, classLoader);
			}

		},
		/**指定类不存在*/
		MISSING {

			@Override
			public boolean matches(String className, ClassLoader classLoader) {
				return !isPresent(className, classLoader);
			}

		};

		abstract boolean matches(String className, ClassLoader classLoader);
		/**判断指定类是否存在*/
		static boolean isPresent(String className, ClassLoader classLoader) {
			//如果给定的classLoader为null，则使用默认的ClassLoader及当前执行线程所对应的类加载器
			if (classLoader == null) {
				classLoader = ClassUtils.getDefaultClassLoader();
			}
			try {
				//加载给定className对应的类，加载成功返回true，失败返回false
				resolve(className, classLoader);
				return true;
			}
			catch (Throwable ex) {
				return false;
			}
		}

	}

}
