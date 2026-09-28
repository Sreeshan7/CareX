package com.carex.leave.leave.type;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, String> {
    List<LeaveType> findByActiveTrueOrderByCode();
}
