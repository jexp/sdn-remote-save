package org.neo4j.save.domain;

import org.springframework.data.neo4j.annotation.NodeEntity;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;

@NodeEntity
public class JobHistory extends BaseEntity {
    Set<Job> jobs;

    public JobHistory() { }

    public void setJobs(Job...jobs) {
        this.jobs = new HashSet<>(asList(jobs));
    }
}
