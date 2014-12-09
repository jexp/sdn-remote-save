package org.neo4j.save.domain;

import org.springframework.data.neo4j.annotation.GraphId;
import org.springframework.data.neo4j.annotation.GraphProperty;
import org.springframework.data.neo4j.annotation.NodeEntity;

import java.util.Date;

/**
 * @author mh
 * @since 15.09.14
 */
public abstract class BaseEntity {
    @GraphId
    Long id;
    @GraphProperty(propertyType = long.class)
    Date createdTs;
    String createdBy;
    @GraphProperty(propertyType = long.class)
    Date modifiedTs;
    String modifiedBy;
}
