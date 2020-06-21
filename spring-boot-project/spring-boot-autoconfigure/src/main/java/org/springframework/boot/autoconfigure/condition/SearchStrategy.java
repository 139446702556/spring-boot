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

/**
 * Some named search strategies for beans in the bean factory hierarchy.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public enum SearchStrategy {

	/**
	 * Search only the current context.
	 * 只在当前上下文容器中搜索
	 */
	CURRENT,

	/**
	 * Search all ancestors, but not the current context.
	 * 搜索全部祖先上下文容器，但是不搜索当前上下文容器
	 */
	ANCESTORS,

	/**
	 * Search the entire hierarchy.
	 * 在所有的上下文容器中进行搜索（包括当前上下文以及祖先）
	 */
	ALL

}
