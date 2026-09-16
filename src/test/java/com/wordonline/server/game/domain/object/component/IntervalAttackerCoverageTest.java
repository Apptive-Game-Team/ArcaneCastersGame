package com.wordonline.server.game.domain.object.component;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntervalAttackerCoverageTest {

    private static final String COMPONENT_PACKAGE = "com.wordonline.server.game.domain.object.component";

    // Effects that hasten or slow an attack find their targets through IntervalAttacker alone. A
    // component that keeps an attack interval without implementing it takes no such effect, and
    // nothing else reports that: it just never speeds up. This is the only check that says so.
    @Test
    void everyComponentWithAnAttackIntervalImplementsIntervalAttacker() {
        List<String> missing = new ArrayList<>();

        for (Class<?> componentType : componentClasses()) {
            if (!declaresAttackIntervalGetter(componentType)) {
                continue;
            }

            if (!IntervalAttacker.class.isAssignableFrom(componentType)) {
                missing.add(componentType.getName());
            }
        }

        assertThat(missing)
                .as("components that expose getAttackInterval() but do not implement IntervalAttacker")
                .isEmpty();
    }

    @Test
    void findsTheComponentsThatAreAlreadyPaced() {
        List<Class<?>> paced = componentClasses().stream()
                .filter(IntervalAttacker.class::isAssignableFrom)
                .toList();

        assertThat(paced)
                .as("the scan has to see real classes, or the check above passes vacuously")
                .isNotEmpty();
    }

    private static boolean declaresAttackIntervalGetter(Class<?> componentType) {
        return Arrays.stream(componentType.getDeclaredMethods())
                .filter(method -> method.getName().equals("getAttackInterval"))
                .map(Method::getParameterCount)
                .anyMatch(parameterCount -> parameterCount == 0);
    }

    private static List<Class<?>> componentClasses() {
        // the default filters keep only concrete, annotated beans; components are neither
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        return true;
                    }
                };
        scanner.addIncludeFilter(new AssignableTypeFilter(Component.class));

        List<Class<?>> componentClasses = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(COMPONENT_PACKAGE)) {
            try {
                componentClasses.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new AssertionError("scanned class is not loadable: " + definition.getBeanClassName(), e);
            }
        }
        return componentClasses;
    }
}
