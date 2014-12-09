package org.neo4j.save.repositories;

import org.neo4j.save.domain.Person;
import org.springframework.data.neo4j.repository.GraphRepository;

public interface PersonRepository extends GraphRepository<Person> {

}
