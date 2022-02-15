/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.core.io.support;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.core.io.support.SpringFactoriesLoader.FactoryInstantiator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link SpringFactoriesLoader}.
 *
 * @author Arjen Poutsma
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Andy Wilkinson
 * @author Madhura Bhave
 */
class SpringFactoriesLoaderTests {

	@BeforeAll
	static void clearCache() {
		SpringFactoriesLoader.cache.clear();
		assertThat(SpringFactoriesLoader.cache).isEmpty();
	}

	@AfterAll
	static void checkCache() {
		assertThat(SpringFactoriesLoader.cache).hasSize(3);
		SpringFactoriesLoader.cache.clear();
	}


	@Test
	void loadFactoryNames() {
		List<String> factoryNames = SpringFactoriesLoader.loadFactoryNames(DummyFactory.class, null);
		assertThat(factoryNames).containsExactlyInAnyOrder(MyDummyFactory1.class.getName(), MyDummyFactory2.class.getName());
	}

	@Test
	void loadFactoriesWithNoRegisteredImplementations() {
		List<Integer> factories = SpringFactoriesLoader.loadFactories(Integer.class, null);
		assertThat(factories).isEmpty();
	}

	@Test
	void loadFactoriesInCorrectOrderWithDuplicateRegistrationsPresent() {
		List<DummyFactory> factories = SpringFactoriesLoader.loadFactories(DummyFactory.class, null);
		assertThat(factories).hasSize(2);
		assertThat(factories.get(0)).isInstanceOf(MyDummyFactory1.class);
		assertThat(factories.get(1)).isInstanceOf(MyDummyFactory2.class);
	}

	@Test
	void loadPackagePrivateFactory() {
		List<DummyPackagePrivateFactory> factories =
				SpringFactoriesLoader.loadFactories(DummyPackagePrivateFactory.class, null);
		assertThat(factories).hasSize(1);
		assertThat(Modifier.isPublic(factories.get(0).getClass().getModifiers())).isFalse();
	}

	@Test
	void attemptToLoadFactoryOfIncompatibleType() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> SpringFactoriesLoader.loadFactories(String.class, null))
			.withMessageContaining("Unable to instantiate factory class "
					+ "[org.springframework.core.io.support.MyDummyFactory1] for factory type [java.lang.String]");
	}

	@Test
	void loadFactoryWithNonDefaultConstructor() {
		ArgumentResolver resolver = ArgumentResolver.of(String.class, "injected");
		List<DummyFactory> factories = SpringFactoriesLoader.loadFactories(DummyFactory.class, LimitedClassLoader.constructorArgumentFactories, resolver);
		assertThat(factories).hasSize(3);
		assertThat(factories.get(0)).isInstanceOf(MyDummyFactory1.class);
		assertThat(factories.get(1)).isInstanceOf(MyDummyFactory2.class);
		assertThat(factories.get(2)).isInstanceOf(ConstructorArgsDummyFactory.class);
		assertThat(factories).extracting(DummyFactory::getString).containsExactly("Foo", "Bar", "injected");
	}

	@Test
	void loadFactoryWithMultipleConstructors() {
		ArgumentResolver resolver = ArgumentResolver.of(String.class, "injected");
		assertThatIllegalArgumentException()
				.isThrownBy(() -> SpringFactoriesLoader.loadFactories(DummyFactory.class, LimitedClassLoader.multipleArgumentFactories, resolver))
				.withMessageContaining("Unable to instantiate factory class "
						+ "[org.springframework.core.io.support.MultipleConstructorArgsDummyFactory] for factory type [org.springframework.core.io.support.DummyFactory]")
				.havingRootCause().withMessageContaining("Class [org.springframework.core.io.support.MultipleConstructorArgsDummyFactory] has no suitable constructor");
	}


	@Nested
	class ArgumentResolverTests {

		@Test
		void ofValueResolvesValue() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test");
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isNull();
		}

		@Test
		void ofValueSupplierResolvesValue() {
			ArgumentResolver resolver = ArgumentResolver.ofSupplied(CharSequence.class, () -> "test");
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isNull();
		}

		@Test
		void fromAdaptsFunction() {
			ArgumentResolver resolver = ArgumentResolver.from(
					type -> CharSequence.class.equals(type) ? "test" : null);
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isNull();
		}

		@Test
		void andValueReturnsComposite() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test").and(Integer.class, 123);
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isEqualTo(123);
		}

		@Test
		void andValueWhenSameTypeReturnsCompositeResolvingFirst() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test").and(CharSequence.class, "ignore");
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
		}

		@Test
		void andValueSupplierReturnsComposite() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test").andSupplied(Integer.class, () -> 123);
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isEqualTo(123);
		}

		@Test
		void andValueSupplierWhenSameTypeReturnsCompositeResolvingFirst() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test").andSupplied(CharSequence.class, () -> "ignore");
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
		}

		@Test
		void andResolverReturnsComposite() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test").and(Integer.class, 123);
			resolver = resolver.and(ArgumentResolver.of(CharSequence.class, "ignore").and(Long.class, 234L));
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isEqualTo(123);
			assertThat(resolver.resolve(Long.class)).isEqualTo(234L);
		}

	}

	@Nested
	class FactoryInstantiatorTests {

		private final ArgumentResolver resolver = ArgumentResolver.of(String.class, "test");

		@Test
		void defaultConstructorCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					DefaultConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		@Test
		void singleConstructorWithArgumentsCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					SingleConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		@Test
		void multiplePrivateAndSinglePublicConstructorCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					MultiplePrivateAndSinglePublicConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		@Test
		void multiplePackagePrivateAndSinglePublicConstructorCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					MultiplePackagePrivateAndSinglePublicConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		@Test
		void singlePackagePrivateConstructorCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					SinglePackagePrivateConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		@Test
		void singlePrivateConstructorCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					SinglePrivateConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		@Test
		void multiplePackagePrivateConstructorsThrowsException() throws Exception {
			assertThatIllegalStateException().isThrownBy(
					() -> FactoryInstantiator.forClass(MultiplePackagePrivateConstructors.class))
				.withMessageContaining("has no suitable constructor");
		}

		static class DefaultConstructor {

		}

		static class SingleConstructor {

			SingleConstructor(String arg) {
			}

		}

		static class MultiplePrivateAndSinglePublicConstructor {

			public MultiplePrivateAndSinglePublicConstructor(String arg) {
				this(arg, false);
			}

			private MultiplePrivateAndSinglePublicConstructor(String arg, boolean extra) {
			}

		}

		static class MultiplePackagePrivateAndSinglePublicConstructor {

			public MultiplePackagePrivateAndSinglePublicConstructor(String arg) {
				this(arg, false);
			}

			MultiplePackagePrivateAndSinglePublicConstructor(String arg, boolean extra) {
			}

		}


		static class SinglePackagePrivateConstructor {

			SinglePackagePrivateConstructor(String arg) {
			}

		}

		static class SinglePrivateConstructor {

			private SinglePrivateConstructor(String arg) {
			}

		}

		static class MultiplePackagePrivateConstructors {

			MultiplePackagePrivateConstructors(String arg) {
				this(arg, false);
			}

			MultiplePackagePrivateConstructors(String arg, boolean extra) {
			}

		}

	}

	private static class LimitedClassLoader extends URLClassLoader {

		private static final ClassLoader constructorArgumentFactories = new LimitedClassLoader("constructor-argument-factories");

		private static final ClassLoader multipleArgumentFactories = new LimitedClassLoader("multiple-arguments-factories");

		LimitedClassLoader(String location) {
			super(new URL[] { toUrl(location) });
		}

		private static URL toUrl(String location) {
			try {
				return new File("src/test/resources/org/springframework/core/io/support/" + location + "/").toURI().toURL();
			}
			catch (MalformedURLException ex) {
				throw new IllegalStateException(ex);
			}
		}

	}

}
