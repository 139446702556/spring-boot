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

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionMessage.Style;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * {@link Condition} and {@link AutoConfigurationImportFilter} that checks for the
 * presence or absence of specific classes.
 *
 * 此类是给@ConditionalOnClass和@ConditionalOnMissingClass两个注解使用的Condition的实现类
 * @author Phillip Webb
 * @see ConditionalOnClass
 * @see ConditionalOnMissingClass
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class OnClassCondition extends FilteringSpringBootCondition {
	/**批量进行匹配，并返回匹配结果   此方法实现于FilteringSpringBootCondition抽象类*/
	@Override
	protected final ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		// Split the work and perform half in a background thread if more than one
		// processor is available. Using a single additional thread seems to offer the
		// best performance. More threads make things worse.
		//如果当前可用处理器有多个，则使用多个线程进行批量解析
		if (Runtime.getRuntime().availableProcessors() > 1) {
			return resolveOutcomesThreaded(autoConfigurationClasses, autoConfigurationMetadata);
		}
		//如果只有一个可用处理器
		else {
			//创建一个StandardOutcomesResolver对象
			OutcomesResolver outcomesResolver = new StandardOutcomesResolver(autoConfigurationClasses, 0,
					autoConfigurationClasses.length, autoConfigurationMetadata, getBeanClassLoader());
			//开始匹配(使用同步方式运行，当前线程  单线程)
			return outcomesResolver.resolveOutcomes();
		}
	}

	private ConditionOutcome[] resolveOutcomesThreaded(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		//在后台线程中将工作一分为二
		//原因是单一附加线程，似乎提供了更好的性能，多个线程会使事情变得更糟糕
		int split = autoConfigurationClasses.length / 2;
		//将前一半，创建一个OutComesResolver对象（使用新线程来执行匹配操作）
		OutcomesResolver firstHalfResolver = createOutcomesResolver(autoConfigurationClasses, 0, split,
				autoConfigurationMetadata);
		//将后一半，创建一个OutcomesResolver对象（直接使用当前线程进行匹配）
		OutcomesResolver secondHalfResolver = new StandardOutcomesResolver(autoConfigurationClasses, split,
				autoConfigurationClasses.length, autoConfigurationMetadata, getBeanClassLoader());
		//执行解析（匹配）（先执行第二个，后执行第一个是因为第一个通过新线程执行，所以当前执行线程要通过thread.join方法来等待其执行完成才能够获取结果）
		//这种执行顺序不会影响，当前线程的匹配操作进行
		ConditionOutcome[] secondHalf = secondHalfResolver.resolveOutcomes();
		ConditionOutcome[] firstHalf = firstHalfResolver.resolveOutcomes();
		//创建outcomes结果数组，然后将上述分开处理的结果合并，并返回
		ConditionOutcome[] outcomes = new ConditionOutcome[autoConfigurationClasses.length];
		System.arraycopy(firstHalf, 0, outcomes, 0, firstHalf.length);
		System.arraycopy(secondHalf, 0, outcomes, split, secondHalf.length);
		return outcomes;
	}

	private OutcomesResolver createOutcomesResolver(String[] autoConfigurationClasses, int start, int end,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		//创建一个StandardOutcomesResolver对象
		OutcomesResolver outcomesResolver = new StandardOutcomesResolver(autoConfigurationClasses, start, end,
				autoConfigurationMetadata, getBeanClassLoader());
		try {
			//创建一个TreadedOutcomesResolver对象，并将其outcomesResolver对象包装进去
			//此处相当于创建了一个新的线程，来执行匹配操作
			return new ThreadedOutcomesResolver(outcomesResolver);
		}
		catch (AccessControlException ex) {
			return outcomesResolver;
		}
	}
	/**进行条件匹配，并获取匹配结果  此方法实现于springbootCondition抽象类*/
	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		//获取上下文中的类加载器
		ClassLoader classLoader = context.getClassLoader();
		//创建ConditionMessage类的实例化对象
		ConditionMessage matchMessage = ConditionMessage.empty();
		//获得@ConditionalOnClass注解的属性值
		List<String> onClasses = getCandidates(metadata, ConditionalOnClass.class);
		//如果当前匹配项上存在@ConditionalOnClass注解，则进行匹配
		if (onClasses != null) {
			//进行条件匹配，看看是否有缺失的
			List<String> missing = filter(onClasses, ClassNameFilter.MISSING, classLoader);
			//如果有缺失的，则表示有未匹配的条件，则返回不匹配结果
			if (!missing.isEmpty()) {
				return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnClass.class)
						.didNotFind("required class", "required classes").items(Style.QUOTE, missing));
			}
			//如果匹配了，则将其添加到matchMessage中
			matchMessage = matchMessage.andCondition(ConditionalOnClass.class)
					.found("required class", "required classes")
					.items(Style.QUOTE, filter(onClasses, ClassNameFilter.PRESENT, classLoader));
		}
		//获得@ConditionalOnMissingClass注解的属性值
		List<String> onMissingClasses = getCandidates(metadata, ConditionalOnMissingClass.class);
		//如果存在@ConditionalOnMissingClass注解
		if (onMissingClasses != null) {
			//进行条件匹配，看看是否有多余的（即不匹配）
			List<String> present = filter(onMissingClasses, ClassNameFilter.PRESENT, classLoader);
			//如果不匹配，则返回不匹配的信息
			if (!present.isEmpty()) {
				return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnMissingClass.class)
						.found("unwanted class", "unwanted classes").items(Style.QUOTE, present));
			}
			//如果全部匹配了，则将其添加到matchMessage中
			matchMessage = matchMessage.andCondition(ConditionalOnMissingClass.class)
					.didNotFind("unwanted class", "unwanted classes")
					.items(Style.QUOTE, filter(onMissingClasses, ClassNameFilter.MISSING, classLoader));
		}
		//返回匹配结果
		return ConditionOutcome.match(matchMessage);
	}

	private List<String> getCandidates(AnnotatedTypeMetadata metadata, Class<?> annotationType) {
		MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(annotationType.getName(), true);
		if (attributes == null) {
			return null;
		}
		List<String> candidates = new ArrayList<>();
		addAll(candidates, attributes.get("value"));
		addAll(candidates, attributes.get("name"));
		return candidates;
	}

	private void addAll(List<String> list, List<Object> itemsToAdd) {
		if (itemsToAdd != null) {
			for (Object item : itemsToAdd) {
				Collections.addAll(list, (String[]) item);
			}
		}
	}
	/**结果解析器接口*/
	private interface OutcomesResolver {
		/**执行解析，返回解析结果*/
		ConditionOutcome[] resolveOutcomes();

	}

	private static final class ThreadedOutcomesResolver implements OutcomesResolver {
		/**新起的执行匹配解析的线程*/
		private final Thread thread;
		/**条件匹配结果*/
		private volatile ConditionOutcome[] outcomes;

		private ThreadedOutcomesResolver(OutcomesResolver outcomesResolver) {
			//创建线程，线程内部任务为执行解析匹配
			this.thread = new Thread(() -> this.outcomes = outcomesResolver.resolveOutcomes());
			//开启线程
			this.thread.start();
		}

		@Override
		public ConditionOutcome[] resolveOutcomes() {
			try {
				//等待新线程中的匹配操作执行完成
				this.thread.join();
			}
			catch (InterruptedException ex) {
				//如果新线程中的匹配操作发生异常，则直接终止当前的操作，停止执行
				Thread.currentThread().interrupt();
			}
			//返回匹配结果
			return this.outcomes;
		}

	}

	private final class StandardOutcomesResolver implements OutcomesResolver {
		/**所有自动配置类的数组*/
		private final String[] autoConfigurationClasses;
		/**匹配的autoConfigurationClasses的开始位置*/
		private final int start;
		/**匹配的autoConfigurationClasses的结果位置*/
		private final int end;
		/**自动配置类相关注解的元数据对象*/
		private final AutoConfigurationMetadata autoConfigurationMetadata;
		/**类加载器*/
		private final ClassLoader beanClassLoader;

		private StandardOutcomesResolver(String[] autoConfigurationClasses, int start, int end,
				AutoConfigurationMetadata autoConfigurationMetadata, ClassLoader beanClassLoader) {
			this.autoConfigurationClasses = autoConfigurationClasses;
			this.start = start;
			this.end = end;
			this.autoConfigurationMetadata = autoConfigurationMetadata;
			this.beanClassLoader = beanClassLoader;
		}
		/**执行匹配匹配，并返回结果*/
		@Override
		public ConditionOutcome[] resolveOutcomes() {
			return getOutcomes(this.autoConfigurationClasses, this.start, this.end, this.autoConfigurationMetadata);
		}

		private ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses, int start, int end,
				AutoConfigurationMetadata autoConfigurationMetadata) {
			//创建存储条件结果的数组
			ConditionOutcome[] outcomes = new ConditionOutcome[end - start];
			//遍历自动配置类数组，进行逐个匹配，从start到end
			for (int i = start; i < end; i++) {
				//获取当前的自动配置类
				String autoConfigurationClass = autoConfigurationClasses[i];
				//如果不为空
				if (autoConfigurationClass != null) {
					//获取当前自动配置类上的@ConditionalOnClass注解设置的条件
					String candidates = autoConfigurationMetadata.get(autoConfigurationClass, "ConditionalOnClass");
					//如果此配置类上设置了ConditionalOnClass条件注解
					if (candidates != null) {
						//则开始进行匹配，保存匹配结果
						outcomes[i - start] = getOutcome(candidates);
					}
				}
			}
			//返回匹配结果
			return outcomes;
		}
		/**匹配给定的所有类名是否都在当前项目中存在，即有引入*/
		private ConditionOutcome getOutcome(String candidates) {
			try {
				//如果条件是单一的，则直接进行匹配，并返回匹配结果
				if (!candidates.contains(",")) {
					return getOutcome(candidates, this.beanClassLoader);
				}
				//如果条件是复合的（多个），则进行遍历，逐个匹配
				for (String candidate : StringUtils.commaDelimitedListToStringArray(candidates)) {
					//匹配
					ConditionOutcome outcome = getOutcome(candidate, this.beanClassLoader);
					//如果有一个条件不匹配，则直接返回匹配结果
					if (outcome != null) {
						return outcome;
					}
				}
			}
			catch (Exception ex) {
				// We'll get another chance later
			}
			//如果设置的所有条件都匹配，则直接返回null
			return null;
		}
		/**判断当前给定的类是否存在，是否可以使用给定的classLoader加载到*/
		private ConditionOutcome getOutcome(String className, ClassLoader classLoader) {
			//如果给定加载器无法加载到当前给定类，即当前类在项目中不存在，则直接返回不匹配
			if (ClassNameFilter.MISSING.matches(className, classLoader)) {
				return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnClass.class)
						.didNotFind("required class").items(Style.QUOTE, className));
			}
			//如果当前给定类可以加载到，即存在，则直接返回null
			return null;
		}

	}

}
