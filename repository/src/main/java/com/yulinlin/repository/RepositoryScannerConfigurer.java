package com.yulinlin.repository;

import com.yulinlin.data.core.anno.JoinRepository;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.beans.Introspector;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Metadata-only scanner for Repository interfaces. Supports Ant-style package segments. */
public class RepositoryScannerConfigurer implements BeanDefinitionRegistryPostProcessor,
        EnvironmentAware, ResourceLoaderAware {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "[A-Za-z_$*?][A-Za-z0-9_$*?]*(?:\\.[A-Za-z_$*?][A-Za-z0-9_$*?]*)*");

    private final Set<String> basePackagePatterns = new LinkedHashSet<>();
    private Environment environment;
    private ResourceLoader resourceLoader;

    public RepositoryScannerConfigurer(String[] basePackages) {
        if (basePackages == null || basePackages.length == 0) {
            throw new IllegalArgumentException("Repository scan packages must not be empty");
        }
        Arrays.stream(basePackages).map(value -> value == null ? "" : value.trim())
                .forEach(this::addBasePackage);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        RepositoryScanner scanner = new RepositoryScanner();
        if (environment != null) scanner.setEnvironment(environment);
        if (resourceLoader != null) scanner.setResourceLoader(resourceLoader);
        for (String basePackagePattern : basePackagePatterns) {
            String basePackage = resolveBasePackage(basePackagePattern);
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                registerRepository(registry, candidate);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    private void addBasePackage(String basePackage) {
        if (basePackage.isEmpty()
                || (!basePackage.contains("${") && !PACKAGE_PATTERN.matcher(basePackage).matches())) {
            throw new IllegalArgumentException("Invalid Repository scan package pattern: " + basePackage);
        }
        basePackagePatterns.add(basePackage);
    }

    private String resolveBasePackage(String basePackagePattern) {
        String resolved = environment == null
                ? basePackagePattern
                : environment.resolveRequiredPlaceholders(basePackagePattern);
        resolved = resolved.trim();
        if (!PACKAGE_PATTERN.matcher(resolved).matches()) {
            throw new IllegalArgumentException("Invalid Repository scan package pattern: " + resolved);
        }
        return resolved;
    }

    private void registerRepository(BeanDefinitionRegistry registry, BeanDefinition candidate) {
        if (!(candidate instanceof AnnotatedBeanDefinition annotated)) return;
        String repositoryType = candidate.getBeanClassName();
        if (repositoryType == null) return;
        Map<String, Object> attributes = annotated.getMetadata()
                .getAnnotationAttributes(JoinRepository.class.getName());
        String explicitName = attributes == null ? "" : (String) attributes.getOrDefault("value", "");
        String beanName = explicitName == null || explicitName.isBlank()
                ? Introspector.decapitalize(ClassUtils.getShortName(repositoryType))
                : explicitName.trim();

        if (registry.containsBeanDefinition(beanName)) {
            if (sameRepository(registry.getBeanDefinition(beanName), repositoryType)) return;
            throw new IllegalStateException("Repository bean name collision: '" + beanName + "' for "
                    + repositoryType + ". Set a unique @JoinRepository(\"beanName\").");
        }

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(RepositoryFactory.class);
        builder.addConstructorArgValue(repositoryType);
        AbstractBeanDefinition definition = builder.getBeanDefinition();
        definition.setAttribute("repositoryInterface", repositoryType);
        registry.registerBeanDefinition(beanName, definition);
    }

    private boolean sameRepository(BeanDefinition definition, String repositoryType) {
        return repositoryType.equals(definition.getAttribute("repositoryInterface"));
    }

    private static final class RepositoryScanner extends ClassPathScanningCandidateComponentProvider {
        private RepositoryScanner() {
            super(false);
            addIncludeFilter(new AnnotationTypeFilter(JoinRepository.class));
        }

        @Override
        protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
            return beanDefinition.getMetadata().isIndependent()
                    && beanDefinition.getMetadata().isInterface();
        }
    }
}
