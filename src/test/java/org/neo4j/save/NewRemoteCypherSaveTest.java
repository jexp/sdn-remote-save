package org.neo4j.save;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.save.config.SingleLabelBasedNodeTypeRepresentationStrategy;
import org.neo4j.save.domain.*;
import org.neo4j.save.services.PersonService;
import org.neo4j.server.CommunityNeoServer;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.helpers.CommunityServerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.data.neo4j.config.EnableNeo4jRepositories;
import org.springframework.data.neo4j.config.Neo4jConfiguration;
import org.springframework.data.neo4j.conversion.Result;
import org.springframework.data.neo4j.core.TypeRepresentationStrategy;
import org.springframework.data.neo4j.mapping.EntityInstantiator;
import org.springframework.data.neo4j.mapping.MappingPolicy;
import org.springframework.data.neo4j.rest.SpringCypherRestGraphDatabase;
import org.springframework.data.neo4j.support.Neo4jTemplate;
import org.springframework.data.neo4j.support.typerepresentation.NoopRelationshipTypeRepresentationStrategy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.*;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author mh
 * @since 16.09.14
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = NewRemoteCypherSaveTest.RemoteConfiguration.class)
public class NewRemoteCypherSaveTest {

    public static final int COUNT = 1000;
    public static final int BATCH = 50;
    static CommunityNeoServer server;
    public static String BASE_URI;

    @BeforeClass
    public static void startServer() throws Exception {
        URL propertyFile = ClassLoader.getSystemResource("test-cypher-server-neo4j.properties");
        server = CommunityServerBuilder.server()
                .withProperty(Configurator.DB_TUNING_PROPERTY_FILE_KEY, propertyFile.getPath())
                .withProperty(Configurator.WEBSERVER_MAX_THREADS_PROPERTY_KEY, "4")
                .onPort(8484).build();
        server.start();
        BASE_URI = NewRemoteCypherSaveTest.server.baseUri().toString() + "db/data/";
    }

    @AfterClass
    public static void stopServer() throws Exception {
        server.stop();
    }

    @EnableNeo4jRepositories("org.neo4j.save.repositories")
    @EnableTransactionManagement(mode = AdviceMode.PROXY)
    @Configuration
    @ComponentScan("org.neo4j.save.services")
    public static class RemoteConfiguration extends Neo4jConfiguration {
        public RemoteConfiguration() {
            setBasePackage("org.neo4j.save.domain");
        }

        @Bean
        public GraphDatabaseService graphDatabaseService() {
            return new SpringCypherRestGraphDatabase(BASE_URI);
        }

        @Override
        public TypeRepresentationStrategy<Node> nodeTypeRepresentationStrategy() throws Exception {
            return new SingleLabelBasedNodeTypeRepresentationStrategy(graphDatabase());
        }

        @Override
        public TypeRepresentationStrategy<Relationship> relationshipTypeRepresentationStrategy() throws Exception {
            return new NoopRelationshipTypeRepresentationStrategy();
        }

        @Bean
        protected EntityInstantiator<Node> graphEntityInstantiator() throws Exception {
            return new EntityInstantiator<Node>() {
                public <T> T createEntityFromState(Node s, Class<T> c, MappingPolicy mappingPolicy) {
                    if (c.equals(Person.class)) return (T)new Person();
                    if (c.equals(JobHistory.class)) return (T)new JobHistory();
                    if (c.equals(Job.class)) return (T)new Job();
                    if (c.equals(SalaryHistory.class)) return (T)new SalaryHistory();
                    if (c.equals(EmployeeStatus.class)) return (T)new EmployeeStatus();
                    if (c.equals(Salary.class)) return (T)new Salary();
                    throw new IllegalArgumentException("Unsupported class "+c);
                }
            };
        }
    }

    @Autowired
    Neo4jTemplate template;

    @Autowired
    PersonService personService;


    @Qualifier("graphDatabaseService")
    @Autowired
    GraphDatabaseService db;

    ExecutorService pool = Executors.newFixedThreadPool(4);

    @Test
    public void testSaveParallel() throws Exception {

        personService.savePersonWithChanges(124, "Name");
        long time = System.currentTimeMillis();
        for (int i = 0; i < COUNT; i++) {
            final int number = i;
            pool.execute(new Runnable() {
                public void run() {
                        personService.savePersonWithChanges(125 + number, "Name" + number);
                        if (number % BATCH == 0) System.out.println("i = " + number);
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.MINUTES);
        time = System.currentTimeMillis() - time;
        System.out.println("remote server total " + time + " ms, per operation " + (time / COUNT) + " ms");


        Result<Map<String, Object>> result = template.query("MATCH (n) return labels(n) as labels, count(*) as cnt", null);
        for (Map<String, Object> row : result) {
            System.out.println("row = " + row);
        }
    }

    @Test
    public void testSaveParallelCombined() throws Exception {
        personService.savePersonWithChanges(124, "Name");
        long time = System.currentTimeMillis();
        for (int i = 0; i < COUNT; i += BATCH) {
            final int number = i;
            pool.execute(new Runnable() {
                public void run() {
                    try (Transaction tx = db.beginTx()) {
                        for (int j = 0; j < BATCH; j++) {
                            int offset = number + j;
                            personService.savePersonWithChanges(125 + offset, "Name" + offset);
                        }
                        tx.success();
                    }
                    System.out.println("i = " + number);
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.MINUTES);
        time = System.currentTimeMillis() - time;
        System.out.println("remote server total " + time + " ms, per operation " + (time / COUNT) + " ms");


        Result<Map<String, Object>> result = template.query("MATCH (n) return labels(n) as labels, count(*) as cnt", null);
        for (Map<String, Object> row : result) {
            System.out.println("row = " + row);
        }
    }
    @Test
    @Transactional
    public void testSave() throws Exception {

        personService.savePersonWithChanges(124, "Name");
        long time = System.currentTimeMillis();
        for (int i = 0; i < COUNT; i++) {
            personService.savePersonWithChanges(125 + i, "Name" + i);
            if (i % BATCH == 0) System.out.println("i = " + i);
        }
        time = System.currentTimeMillis() - time;
        System.out.println("remote server total " + time + " ms, per operation " + (time / COUNT) + " ms");

        Result<Map<String, Object>> result = template.query("MATCH (n) return labels(n) as labels, count(*) as cnt", null);
        for (Map<String, Object> row : result) {
            System.out.println("row = " + row);
        }
    }

    @Test
    public void testSingleSave() throws Exception {
        long time = System.currentTimeMillis();
        personService.savePersonWithChanges(124, "Name");
        time = System.currentTimeMillis() - time;
        System.out.println("remote server total " + time + " ms");

    }

}
