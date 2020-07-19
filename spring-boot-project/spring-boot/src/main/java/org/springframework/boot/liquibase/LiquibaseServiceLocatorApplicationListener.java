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

package org.springframework.boot.liquibase;

import liquibase.servicelocator.CustomResolverServiceLocator;
import liquibase.servicelocator.ServiceLocator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.util.ClassUtils;

/**
 * {@link ApplicationListener} that replaces the liquibase {@link ServiceLocator} with a
 * version that works with Spring Boot executable archives.
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @since 1.0.0
 * 初始化Liquibase的ServiceLocator对象
 */
public class LiquibaseServiceLocatorApplicationListener implements ApplicationListener<ApplicationStartingEvent> {

	private static final Log logger = LogFactory.getLog(LiquibaseServiceLocatorApplicationListener.class);

	@Override
	public void onApplicationEvent(ApplicationStartingEvent event) {
		//判断是否存在liquibase.servicelocator.CustomResolverServiceLocator类，间接性知道是否引入了liquibase-core包
		if (ClassUtils.isPresent("liquibase.servicelocator.CustomResolverServiceLocator",
				event.getSpringApplication().getClassLoader())) {
			//进行初始化Liquibase功能
			new LiquibasePresent().replaceServiceLocator();
		}
	}

	/**
	 * Inner class to prevent class not found issues.
	 */
	private static class LiquibasePresent {

		void replaceServiceLocator() {
			//创建CustomResolverServiceLocator对象
			CustomResolverServiceLocator customResolverServiceLocator = new CustomResolverServiceLocator(
					new SpringPackageScanClassResolver(logger));
			//将CustomResolverServiceLocator对象设置到ServiceLocator的instance属性中
			ServiceLocator.setInstance(customResolverServiceLocator);
		}

	}

}
