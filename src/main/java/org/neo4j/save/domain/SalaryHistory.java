package org.neo4j.save.domain;

import org.springframework.data.neo4j.annotation.NodeEntity;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;

@NodeEntity
public class SalaryHistory extends BaseEntity {
    Set<Salary> salaries;

    public SalaryHistory() { }

    public void setSalaries(Salary... salaries) {
        this.salaries = new HashSet<Salary>(asList(salaries));
    }

}
