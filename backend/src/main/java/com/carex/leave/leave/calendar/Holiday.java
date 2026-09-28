package com.carex.leave.leave.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "holiday")
public class Holiday {
    @Id
    @Column(name = "holiday_date")
    private LocalDate holidayDate;

    @Column(nullable = false)
    private String name;

    protected Holiday() {}

    public Holiday(LocalDate holidayDate, String name) {
        this.holidayDate = holidayDate;
        this.name = name;
    }

    public LocalDate getHolidayDate() { return holidayDate; }
    public String getName() { return name; }
}
