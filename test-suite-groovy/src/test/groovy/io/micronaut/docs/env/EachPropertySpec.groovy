package io.micronaut.docs.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.core.util.CollectionUtils
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class EachPropertySpec extends Specification {

    void "test each property"() {
        when:
        // tag::config[]
        ApplicationContext applicationContext = ApplicationContext.run(PropertySource.of(
                "test",
                CollectionUtils.mapOf(
                "test.datasource.one.url", "jdbc:mysql://localhost/one",
                "test.datasource.two.url", "jdbc:mysql://localhost/two")
        ))
        // end::config[]

        // tag::beans[]
        Collection<DataSourceConfiguration> beansOfType = applicationContext.getBeansOfType(DataSourceConfiguration.class)
        DataSourceConfiguration firstConfig = applicationContext.getBean(
                DataSourceConfiguration.class,
                Qualifiers.byName("one") // <2>
        )

        then:
        beansOfType.size() == 2 // <1>
        firstConfig.getUrl() == new URI("jdbc:mysql://localhost/one")
        // end::beans[]
    }
}
