package org.neo4j.save;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.neo4j.graphdb.Transaction;
import org.neo4j.server.CommunityNeoServer;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.helpers.CommunityServerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.conversion.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
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
@ContextConfiguration(classes = RemoteJdbcCypherSaveTest.RemoteJdbcConfiguration.class)
public class RemoteJdbcCypherSaveTest {

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

    @Configuration
    @EnableTransactionManagement(mode = AdviceMode.PROXY)
    public static class RemoteJdbcConfiguration {
        @Bean
        public DataSource dataSource() {
            String url = RemoteJdbcCypherSaveTest.server.baseUri().toString().substring("http:".length());
            return new DriverManagerDataSource("jdbc:neo4j:"+ url);
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @Autowired JdbcTemplate template;
    ExecutorService pool = Executors.newFixedThreadPool(4);

    @Test
    public void testSaveParallel() throws Exception {
        template.update(QUERY, people(1));
        final List people = people(BATCH);
        long time = System.currentTimeMillis();
        for (int i=0;i<COUNT/BATCH;i++) {
            final int number = i;
            pool.execute(new Runnable() {
                public void run() {
                    template.update(QUERY, people);
                    if (number % BATCH == 0) System.out.println("i = " + number);
                }
            });

        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.MINUTES);
        time = System.currentTimeMillis() - time;
        System.out.println("save parallel remote server total " + time + " ms, per operation " + (time / COUNT) + " ms");

        dumpContents();
    }

    private void dumpContents() {
        template.query("MATCH (n) return labels(n) as labels, count(*) as cnt", new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                System.out.println("row = " + rs.getObject(1)+ " "+rs.getObject(2));
            }
        });
    }

    @Test
    @Transactional
    public void testSingleSave() throws Exception {
        template.update(QUERY, people(1));
        long time = System.currentTimeMillis();
        template.update(QUERY, people(COUNT));
        time = System.currentTimeMillis() - time;
        System.out.println("test single save remote server total " + time + " ms per person "+time/COUNT+" ms");

    }
    @Test
    @Transactional
    public void testSaveInBatches() throws Exception {
        template.update(QUERY, people(1));
        List people = people(COUNT / BATCH);
        long time = System.currentTimeMillis();
        for (int i=0;i<BATCH;i++) {
            template.update(QUERY, people);
        }
        time = System.currentTimeMillis() - time;
        System.out.println("save in batches remote server total " + time + " ms per person "+time/COUNT+" ms");
        dumpContents();
    }

    String QUERY = "UNWIND {1} as u\n" +
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
}
