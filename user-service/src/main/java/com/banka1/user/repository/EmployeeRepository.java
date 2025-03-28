package com.banka1.user.repository;

import com.banka1.common.model.Department;
import com.banka1.user.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);
    Optional<Employee> findByVerificationCode(String verificationCode);
    List<Employee> findByDepartment(Department department);



     boolean existsByEmail(String email);
     boolean existsById(Long id);
}
