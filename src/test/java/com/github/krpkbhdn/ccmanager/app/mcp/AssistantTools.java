package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.mockito.Mockito.mock;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/** Every concrete {@link AssistantMcpTool} of this package, built with mocked constructor arguments. */
final class AssistantTools {

    private AssistantTools() {
    }

    static List<AssistantMcpTool> all() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(AssistantMcpTool.class));
        return scanner.findCandidateComponents(AssistantMcpTool.class.getPackageName()).stream()
                .map(definition -> instantiate(definition.getBeanClassName()))
                .toList();
    }

    private static AssistantMcpTool instantiate(String className) {
        try {
            var constructor = Arrays.stream(Class.forName(className).getDeclaredConstructors())
                    .max(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
            constructor.setAccessible(true);
            var arguments = Arrays.stream(constructor.getParameterTypes()).map(type -> mock(type)).toArray();
            return (AssistantMcpTool) constructor.newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build " + className, e);
        }
    }
}
