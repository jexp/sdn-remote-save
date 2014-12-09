package org.neo4j.save.domain;

import org.springframework.data.neo4j.annotation.NodeEntity;

@NodeEntity
public class EmployeeStatus extends BaseEntity {
    public JobHistory jobHistory;

    public SalaryHistory salaryHistory;

    public EmployeeStatus() { }

}
