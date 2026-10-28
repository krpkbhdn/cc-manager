package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpTool;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/** Spring injects List&lt;McpTool&gt; into the project server and List&lt;AssistantMcpTool&gt; into the app server, so no bean may be both. */
class McpServerSeparationTest {

    private static final String BASE_PACKAGE = "com.github.krpkbhdn.ccmanager";

    private List<Class<?>> components() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        return scanner.findCandidateComponents(BASE_PACKAGE).stream().map(BeanDefinition::getBeanClassName)
                .<Class<?>>map(name -> ClassUtils.resolveClassName(name, getClass().getClassLoader())).toList();
    }

    @Test
    void noBeanIsBothAProjectToolAndAnAssistantTool() {
        var classes = components();

        var projectTools = classes.stream().filter(McpTool.class::isAssignableFrom).toList();
        var assistantTools = classes.stream().filter(AssistantMcpTool.class::isAssignableFrom).toList();

        assertThat(projectTools).isNotEmpty().noneMatch(AssistantMcpTool.class::isAssignableFrom);
        assertThat(assistantTools).isNotEmpty().noneMatch(McpTool.class::isAssignableFrom);
        assertThat(projectTools).extracting(Class::getSimpleName).contains("SetRunConfigurationTool").doesNotContain("ListProjectsTool");
        assertThat(assistantTools).extracting(Class::getSimpleName).contains("ListProjectsTool").doesNotContain("SetRunConfigurationTool");
    }
}
