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
import org.neo4j.save.services.PersonService;
import org.neo4j.server.CommunityNeoServer;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.helpers.CommunityServerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.config.EnableNeo4jRepositories;
import org.springframework.data.neo4j.config.Neo4jConfiguration;
import org.springframework.data.neo4j.conversion.Result;
import org.springframework.data.neo4j.core.TypeRepresentationStrategy;
import org.springframework.data.neo4j.rest.SpringCypherRestGraphDatabase;
import org.springframework.data.neo4j.support.Neo4jTemplate;
import org.springframework.data.neo4j.support.typerepresentation.NoopRelationshipTypeRepresentationStrategy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static org.neo4j.helpers.collection.MapUtil.map;

/**
 * @author mh
 * @since 16.09.14
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = RemoteCypherStatementSaveTest.RemoteConfiguration.class)
public class RemoteCypherStatementSaveTest {

    public static final int COUNT = 1000;
    public static final int BATCH = 50;
    static CommunityNeoServer server;

    @BeforeClass
    public static void startServer() throws Exception {
        URL propertyFile = ClassLoader.getSystemResource("test-cypher-server-neo4j.properties");
        server = CommunityServerBuilder.server()
                .withProperty(Configurator.DB_TUNING_PROPERTY_FILE_KEY, propertyFile.getPath())
                .withProperty(Configurator.WEBSERVER_MAX_THREADS_PROPERTY_KEY, "4")
                .onPort(8484).build();
        server.start();
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
            return new SpringCypherRestGraphDatabase(RemoteCypherStatementSaveTest.server.baseUri().toString() + "db/data/");
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
    Neo4jTemplate template;

    @Autowired
    PersonService personService;


    @Qualifier("graphDatabaseService")
    @Autowired
    GraphDatabaseService db;

    ExecutorService pool = Executors.newFixedThreadPool(4);

    @Test
    public void testSaveParallel() throws Exception {
        template.query(QUERY, map("people", people(1)));
        final List people = people(1);
        long time = System.currentTimeMillis();
        for (int i = 0; i < COUNT; i++) {
            pool.execute(new Runnable() {
                public void run() {
                    template.query(QUERY, map("people", people));
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.MINUTES);
        time = System.currentTimeMillis() - time;
        System.out.println("parallel individual remote server total " + time + " ms, per operation " + (time / COUNT) + " ms");

        dumpContents();
    }

    @Test
    public void testSaveParallelBatchCombined() throws Exception {
        template.query(QUERY, map("people", people(1)));
        final List people = people(BATCH);
        long time = System.currentTimeMillis();
        for (int i = 0; i < COUNT; i += BATCH) {
            final int number = i;
            pool.execute(new Runnable() {
                public void run() {
                    try (Transaction tx = db.beginTx()) {
                        template.query(QUERY, map("people", people));
                        tx.success();
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.MINUTES);
        time = System.currentTimeMillis() - time;
        System.out.println("parallel batch remote server total " + time + " ms, per operation " + (time / COUNT) + " ms");

        dumpContents();
    }

    @Test
    public void testSaveParallelBatchIndividual() throws Exception {
        template.query(QUERY, map("people", people(1)));
        final List people = people(1);
        long time = System.currentTimeMillis();
        for (int i = 0; i < COUNT; i += BATCH) {
            final int number = i;
            pool.execute(new Runnable() {
                public void run() {
                    try (Transaction tx = db.beginTx()) {
                        for (int j = 0; j < BATCH; j++) {
                            int offset = number + j;
                            template.query(QUERY, map("people", people));
                        }
                        tx.success();
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.MINUTES);
        time = System.currentTimeMillis() - time;
        System.out.println("parallel batch individual remote server total " + time + " ms, per operation " + (time / COUNT) + " ms");

        dumpContents();
    }
    @Test
    @Transactional
    public void testSave() throws Exception {

        template.query(QUERY, map("people", people(1)));
        List people = people(BATCH);
        long time = System.currentTimeMillis();
        for (int i = 0; i < COUNT/BATCH; i++) {
            template.query(QUERY,map("people", people));
            if (i % BATCH == 0) System.out.println("i = " + i);
        }
        time = System.currentTimeMillis() - time;
        System.out.println("batch single remote server total " + time + " ms, per operation " + (time / COUNT) + " ms");

        dumpContents();
    }

    @Test
    public void testSingleSave() throws Exception {
        template.query(QUERY,map("people",people(1)));
        long time = System.currentTimeMillis();
        template.query(QUERY, map("people", people(1)));
        time = System.currentTimeMillis() - time;
        System.out.println("single remote server total " + time + " ms");
    }

    String QUERY = "UNWIND {people} as u\n" +
            "CREATE (p:Person {id:u.id, phone:u.phone, email:u.email})\n" +
            "CREATE (p)<-[:PERSON_SALARY]-(ps:PersonSalary)\n" +
            "FOREACH (s in u.salaries |\n" +
            " CREATE (ps)<-[:SALARY]-(:Salary {amount:s.amount, since:s.since})\n" +
            ")\n" +
            "CREATE (p)<-[:PERSON_JOB]-(pj:PersonJob)\n" +
            "FOREACH (j in u.jobs |\n" +
            "  CREATE (pj)<-[:JOB]-(:Job {title:j.title, since:j.since})\n" +
            ")\n";

    private List people(int count) {
        List salaries = asList(map("amount", 100_000,"since","2010-01-01"), map("amount", 120_000,"since","2014-01-01"));
        List jobs = asList(map("title", "Developer","since","2010-01-01"), map("title", "Senior Developer","since","2014-01-01"));
        Map person = map("id",2348094,"phone","308-34-333","email","john@doe.com", "firstname", "John","lastname", "Doe",
                "salaries",salaries,"jobs",jobs);
        List people = new ArrayList(count);
        for (int i=0;i< count;i++) {
            people.add(person);
        }
        return people;
    }

    private void dumpContents() {
        Result<Map<String, Object>> result = template.query("MATCH (n) return labels(n) as labels, count(*) as cnt", null);
        for (Map<String, Object> row : result) {
            System.out.println("row = " + row);
        }
    }
}
