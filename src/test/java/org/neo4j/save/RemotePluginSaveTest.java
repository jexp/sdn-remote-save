package org.neo4j.save;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.save.config.SingleLabelBasedNodeTypeRepresentationStrategy;
import org.neo4j.save.services.PersonService;
import org.neo4j.server.CommunityNeoServer;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.helpers.CommunityServerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.config.EnableNeo4jRepositories;
import org.springframework.data.neo4j.config.Neo4jConfiguration;
import org.springframework.data.neo4j.conversion.Result;
import org.springframework.data.neo4j.core.TypeRepresentationStrategy;
import org.springframework.data.neo4j.rest.SpringRestGraphDatabase;
import org.springframework.data.neo4j.support.Neo4jTemplate;
import org.springframework.data.neo4j.support.typerepresentation.NoopRelationshipTypeRepresentationStrategy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.util.Map;

/**
 * @author mh
 * @since 16.09.14
 */
public class RemotePluginSaveTest {

    public static final int COUNT = 1000;
    static CommunityNeoServer server;

    @BeforeClass
    public static void startServer() throws Exception {
        URL propertyFile = ClassLoader.getSystemResource("test-cypher-server-neo4j.properties");
        server = CommunityServerBuilder.server()
                .withProperty(Configurator.DB_TUNING_PROPERTY_FILE_KEY, propertyFile.getPath())
                .withProperty(Configurator.WEBSERVER_MAX_THREADS_PROPERTY_KEY, "4")
                .withThirdPartyJaxRsPackage("org.neo4j.save.extension","/person")
                .onPort(8484).build();
        server.start();
    }

    @AfterClass
    public static void stopServer() throws Exception {
        server.stop();
    }


    @Test
    public void testSave() throws Exception {

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.postForEntity(server.baseUri()+"person/124/Name", null, String.class);
        long time = System.currentTimeMillis();
        for (int i=0;i< COUNT;i++) {
            restTemplate.postForEntity(server.baseUri() + "person/"+(125+i)+"/Name"+i, null, String.class);
        }
        time = System.currentTimeMillis() - time;
        System.out.println("remote extension total " + time+" ms, per operation "+(time/ COUNT)+" ms");
    }
}
