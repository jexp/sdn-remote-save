package org.neo4j.save.services;

import org.neo4j.save.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.support.Neo4jTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class PersonService {

    @Autowired
    Neo4jTemplate template;

    @Transactional
    public Long savePersonWithChanges(int userId, String name) {
        Person person = new Person();
        person.setUserId(userId);
        person.setName(name);
        person.setCreatedDate(new Date());

        SalaryHistory salaryHistory = new SalaryHistory();
        salaryHistory.setSalaries(template.save(new Salary()),template.save(new Salary()));
        JobHistory jobHistory = new JobHistory();
        Job job1 = template.save(new Job());
        Job job2 = template.save(new Job());
        jobHistory.setJobs(job1, job2);
        EmployeeStatus employeeStatus = new EmployeeStatus();
        employeeStatus.jobHistory = template.save(jobHistory);
        employeeStatus.salaryHistory = template.save(salaryHistory);

        person.employeeStatus = template.save(employeeStatus);
        template.save(person);
        return person.getId();
    }

}
