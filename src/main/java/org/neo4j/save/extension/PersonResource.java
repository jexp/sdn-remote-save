package org.neo4j.save.extension;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.save.services.PersonService;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;

@Path("/")
public class PersonResource {

    @Context PersonService service;
    @Context
    GraphDatabaseService db;

    @Path("/{id}/{name}")
    @POST
    public String createPerson(@PathParam("id") int id, @PathParam("name")String name) {
        Transaction tx = db.beginTx();
        try  {
            String result = String.valueOf(service.savePersonWithChanges(id, name));
            tx.success();
            return result;
        } finally {
            tx.close();
        }
    }
}
