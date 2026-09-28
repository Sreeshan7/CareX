package com.carex.leave.leave.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, LocalDate> {
    List<Holiday> findByHolidayDateBetweenOrderByHolidayDate(LocalDate from, LocalDate to);
}
