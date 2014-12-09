package org.neo4j.save;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.save.config.SingleLabelBasedNodeTypeRepresentationStrategy;
import org.neo4j.save.domain.*;
import org.neo4j.save.repositories.PersonRepository;
import org.neo4j.save.services.PersonService;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.config.EnableNeo4jRepositories;
import org.springframework.data.neo4j.config.Neo4jConfiguration;
import org.springframework.data.neo4j.conversion.Result;
import org.springframework.data.neo4j.core.TypeRepresentationStrategy;
import org.springframework.data.neo4j.support.GraphDatabaseFactoryBean;
import org.springframework.data.neo4j.support.GraphDatabaseServiceFactoryBean;
import org.springframework.data.neo4j.support.Neo4jTemplate;
import org.springframework.data.neo4j.support.typerepresentation.LabelBasedNodeTypeRepresentationStrategy;
import org.springframework.data.neo4j.support.typerepresentation.NoopRelationshipTypeRepresentationStrategy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = EmbeddedSaveTest.EmbeddedConfiguration.class)
public class EmbeddedSaveTest {

    private static final int COUNT = 1000;

    @EnableNeo4jRepositories("org.neo4j.save.repositories")
    @Configuration
    @EnableTransactionManagement
    @ComponentScan("org.neo4j.save.services")
    public static class EmbeddedConfiguration extends Neo4jConfiguration {
        public EmbeddedConfiguration() {
            setBasePackage("org.neo4j.save.domain");
        }

        @Bean
        public GraphDatabaseService graphDatabaseService() throws Exception {
            return new TestGraphDatabaseFactory().newImpermanentDatabase();
        }

        @Override
        public TypeRepresentationStrategy<Node> nodeTypeRepresentationStrategy() throws Exception {
            return new SingleLabelBasedNodeTypeRepresentationStrategy(graphDatabase());
        }

        @Override
        public TypeRepresentationStrategy<Relationship> relationshipTypeRepresentationStrategy() throws Exception {
            return new NoopRelationshipTypeRepresentationStrategy();
        }
    }

    @Autowired
    PersonService personService;

    @Autowired Neo4jTemplate template;

    @Test
    public void testSave() throws Exception {

        personService.savePersonWithChanges(124, "Name");
        long time = System.currentTimeMillis();
        for (int i = 0; i < COUNT; i++) {
            personService.savePersonWithChanges(125 + i, "Name" + i);
        }
        time = System.currentTimeMillis() - time;
        System.out.println("embedded total " + time + " ms, per operation " + (time / COUNT) + " ms");


        Result<Map<String, Object>> result = template.query("MATCH (n) return labels(n) as labels, count(*) as cnt", null);
        for (Map<String, Object> row : result) {
            System.out.println("row = " + row);
        }
    }

}
