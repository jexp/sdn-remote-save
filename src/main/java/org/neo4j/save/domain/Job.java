package org.neo4j.save.domain;

import org.springframework.data.neo4j.annotation.GraphId;
import org.springframework.data.neo4j.annotation.NodeEntity;

@NodeEntity
public class Job extends BaseEntity{
    public Job() { }
}
