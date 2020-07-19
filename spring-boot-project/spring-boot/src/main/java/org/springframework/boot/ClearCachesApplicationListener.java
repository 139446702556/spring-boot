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

package org.springframework.boot;

import java.lang.reflect.Method;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.ReflectionUtils;

/**
 * {@link ApplicationListener} to cleanup caches once the context is loaded.
 *
 * @author Phillip Webb
 * 实现针对ReflectionUtils和classLoader的缓存的清除操作
 * 此监听器在接收到容器初始化的ContextRefreshedEvent事件时，触发此监听器
 */
class ClearCachesApplicationListener implements ApplicationListener<ContextRefreshedEvent> {

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		//清除ReflectionUtils的缓存
		ReflectionUtils.clearCache();
		//清除类加载器的缓存
		clearClassLoaderCaches(Thread.currentThread().getContextClassLoader());
	}

	private void clearClassLoaderCaches(ClassLoader classLoader) {
		//如果类加载器为空，则直接返回
		if (classLoader == null) {
			return;
		}
		try {
			//通过反射获取到classLoader的clearCache方法对象
			Method clearCacheMethod = classLoader.getClass().getDeclaredMethod("clearCache");
			//通过反射调用classLoader的clearCache方法，来清除其缓存
			clearCacheMethod.invoke(classLoader);
		}
		catch (Exception ex) {
			// Ignore
		}
		//递归调用，用来清空其父类们的缓存
		clearClassLoaderCaches(classLoader.getParent());
	}

}
