package org.neo4j.save.extension;

import com.sun.jersey.spi.inject.Injectable;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.helpers.Pair;
import org.neo4j.save.services.PersonService;
import org.springframework.data.neo4j.server.SpringPluginInitializer;
import org.springframework.data.neo4j.support.Neo4jTemplate;

import java.util.Collection;
import org.apache.commons.configuration.Configuration;
/**
 * @author mh
 * @since 16.09.14
 */
public class PersonPluginInitializer extends SpringPluginInitializer {
    public PersonPluginInitializer() {
        super(new String[] {"classpath:META-INF/spring/springContext.xml"},
              expose("personService", PersonService.class), expose("neo4jTemplate", Neo4jTemplate.class));
    }

    @Override
    public Collection<org.neo4j.server.plugins.Injectable<?>> start(GraphDatabaseService graphDatabaseService, Configuration config) {
        Collection<org.neo4j.server.plugins.Injectable<?>> injectableCollection = super.start(graphDatabaseService, config);
        System.out.println("injectableCollection = " + injectableCollection);
        Neo4jTemplate neo4jTemplate = ctx.getBean(Neo4jTemplate.class);
        PersonService personService = ctx.getBean(PersonService.class);
        return injectableCollection;
    }
}
