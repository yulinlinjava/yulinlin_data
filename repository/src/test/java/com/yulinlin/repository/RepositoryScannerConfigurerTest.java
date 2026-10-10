package com.yulinlin.repository;

import com.yulinlin.repository.fixture.alpha.local.AlphaUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryScannerConfigurerTest {

    @Test
    void configuredPackagesRegisterAUsableFactoryBean() {
        try (AnnotationConfigApplicationContext context = autoConfiguredContext(Map.of(
                "yulinlin.repository.scan-packages[0]",
                "com.yulinlin.repository.fixture.alpha.local"))) {
            assertThat(context.getBean(AlphaUserRepository.class)).isNotNull();
            assertThat(context.containsBean("alphaUserRepository")).isTrue();
            assertThat(context.getBean(RepositoryProperties.class).getScanPackages())
                    .containsExactly("com.yulinlin.repository.fixture.alpha.local");
        }
    }

    @Test
    void emptyConfigurationUsesSpringBootApplicationRootPackage() {
        try (AnnotationConfigApplicationContext context = autoConfiguredContext(
                Map.of(), "com.yulinlin.repository.fixture.alpha.local")) {
            assertThat(context.getBean(AlphaUserRepository.class)).isNotNull();
            assertThat(context.containsBean("alphaUserRepository")).isTrue();
            assertThat(context.getBean(RepositoryProperties.class).getScanPackages()).isEmpty();
        }
    }

    @Test
    void starMatchesExactlyOnePackageSegment() {
        DefaultListableBeanFactory registry = scan("com.yulinlin.repository.fixture.*.local");

        assertThat(registry.containsBeanDefinition("alphaUserRepository")).isTrue();
        assertThat(registry.containsBeanDefinition("betaRepository")).isTrue();
        assertThat(registry.containsBeanDefinition("ignoredRepository")).isFalse();
        assertThat(registry.getBeanDefinition("alphaUserRepository").getAttribute("repositoryInterface"))
                .isEqualTo("com.yulinlin.repository.fixture.alpha.local.AlphaUserRepository");
    }

    @Test
    void supportsMultipleStarsAndQuestionMarks() {
        DefaultListableBeanFactory stars = scan("com.yulinlin.repository.*.*.local");
        DefaultListableBeanFactory questionMark = scan("com.yulinlin.repository.fixture.alph?.local");

        assertThat(stars.containsBeanDefinition("alphaUserRepository")).isTrue();
        assertThat(stars.containsBeanDefinition("betaRepository")).isTrue();
        assertThat(questionMark.containsBeanDefinition("alphaUserRepository")).isTrue();
        assertThat(questionMark.containsBeanDefinition("betaRepository")).isFalse();
    }

    @Test
    void doubleStarMatchesAcrossPackageDepthsAndDuplicatePatternsAreIdempotent() {
        DefaultListableBeanFactory registry = scan(
                "com.yulinlin.repository.fixture.**.local",
                "com.yulinlin.repository.fixture.alpha.local");

        assertThat(registry.containsBeanDefinition("alphaUserRepository")).isTrue();
        assertThat(registry.containsBeanDefinition("betaRepository")).isTrue();
    }

    @Test
    void plainPackageStillScansRecursively() {
        DefaultListableBeanFactory registry = scan("com.yulinlin.repository.fixture.alpha");

        assertThat(registry.containsBeanDefinition("alphaUserRepository")).isTrue();
        assertThat(registry.containsBeanDefinition("ignoredRepository")).isTrue();
    }

    @Test
    void reportsSimpleNameCollisionAndExplainsExplicitName() {
        RepositoryScannerConfigurer configurer = new RepositoryScannerConfigurer(new String[]{
                "com.yulinlin.repository.fixture.collision.*"
        });

        assertThatThrownBy(() -> configurer.postProcessBeanDefinitionRegistry(new DefaultListableBeanFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Repository bean name collision")
                .hasMessageContaining("@JoinRepository(\"beanName\")");
    }

    @Test
    void rejectsInvalidPackagePatternEarly() {
        assertThatThrownBy(() -> new RepositoryScannerConfigurer(new String[]{"com..repository"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Repository scan package pattern");
    }

    @Test
    void resolvesPackagePatternFromSpringEnvironment() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "repository.scan", "com.yulinlin.repository.fixture.*.local")));
        RepositoryScannerConfigurer configurer =
                new RepositoryScannerConfigurer(new String[]{"${repository.scan}"});
        configurer.setEnvironment(environment);
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();

        configurer.postProcessBeanDefinitionRegistry(registry);

        assertThat(registry.containsBeanDefinition("alphaUserRepository")).isTrue();
        assertThat(registry.containsBeanDefinition("betaRepository")).isTrue();
    }

    private DefaultListableBeanFactory scan(String... packages) {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        new RepositoryScannerConfigurer(packages).postProcessBeanDefinitionRegistry(registry);
        return registry;
    }

    private AnnotationConfigApplicationContext autoConfiguredContext(Map<String, Object> properties,
                                                                     String... applicationPackages) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (!properties.isEmpty()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        }
        if (applicationPackages.length > 0) {
            AutoConfigurationPackages.register(context, applicationPackages);
        }
        context.register(RepositoryAutoConfig.class);
        context.refresh();
        return context;
    }
}
