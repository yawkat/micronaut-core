package io.micronaut.docs.context.env.yaml;

import groovy.lang.GroovyClassLoader;
import io.micronaut.context.env.DefaultEnvironment;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.context.env.yaml.YamlPropertySourceLoader;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import spock.lang.Specification;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

public class YamlPropertySourceLoaderSpec {
    // TODO how best to convert this? need mocking, alternative to GCL
//    public void testLoadYamlPropertiesSource() {
//        given:
//        ServiceDefinition serviceDefinition = Mock(ServiceDefinition.class);
//        serviceDefinition.isPresent() >> true;
//        serviceDefinition.load() >> new YamlPropertySourceLoader();
//
//        Environment env = new DefaultEnvironment(new String[]{"test"}) {
//            @Override
//            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
//                GroovyClassLoader gcl = new GroovyClassLoader();
//                gcl.addClass(YamlPropertySourceLoader.class);
//                gcl.addURL(YamlPropertySourceLoader.class.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"));
//                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader.class, gcl);
//            }
//
//            @Override
//            public Optional<InputStream> getResourceAsStream(String path) {
//                if (path.endsWith("-test.yml")) {
//                    return Optional.of(new ByteArrayInputStream("\\ndataSource:\n    jmxExport: true\n    username: sa\n    password: 'test'\n".getBytes()));
//                } else if (path.endsWith("application.yml")) {
//                    return Optional.of(new ByteArrayInputStream("\\nhibernate:\n    cache:\n        queries: false\ndataSource:\n    pooled: true\n    driverClassName: org.h2.Driver\n    username: sa\n    password: ''    \n".getBytes()));
//                }
//
//
//                return Optional.empty();
//            }
//
//        };
//
//        env.start();
//
//        assertFalse(env.get("hibernate.cache.queries", Boolean.class).get());
//        assertTrue(env.get("data-source.pooled", Boolean.class).get());
//        assertEquals("test", env.get("data-source.password", String.class).get());
//        assertTrue(env.get("data-source.jmx-export", Boolean.class).get());
//    }
//
//    public void testDatasourcesDefault() {
//        ServiceDefinition serviceDefinition = Mock(ServiceDefinition.class);
//        serviceDefinition.isPresent() >> true;
//        serviceDefinition.load() >> new YamlPropertySourceLoader();
//
//        Environment env = new DefaultEnvironment(new String[]{"test"}) {
//            @Override
//            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
//                GroovyClassLoader gcl = new GroovyClassLoader();
//                gcl.addClass(YamlPropertySourceLoader.class);
//                gcl.addURL(YamlPropertySourceLoader.class.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"));
//                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader.class, gcl);
//            }
//
//            @Override
//            public Optional<InputStream> getResourceAsStream(String path) {
//                if (path.endsWith("-test.yml")) {
//                    return Optional.of(new ByteArrayInputStream("\\ndatasources.default: {}\n".getBytes()));
//                } else if (path.endsWith("application.yml")) {
//                    return Optional.of(new ByteArrayInputStream("\\ndatasources.default: {}    \n".getBytes()));
//                }
//
//
//                return Optional.empty();
//            }
//
//        };
//
//
//        when:
//        ((DefaultEnvironment) env).start();
//
//        then:
//        env.get("datasources.default", String.class).get().equals("{}");
//        DefaultGroovyMethods.equals(env.get("datasources.default", Map.class).get(), new LinkedHashMap<Object, Object>());
//
//    }

}
