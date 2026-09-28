package com.carex.leave.leave.type;

import com.carex.leave.leave.calendar.HolidayRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class LeaveTypeController {
    private final LeaveTypeRepository types;
    private final HolidayRepository holidays;

    public LeaveTypeController(LeaveTypeRepository types, HolidayRepository holidays) {
        this.types = types;
        this.holidays = holidays;
    }

    public record LeaveTypeView(String code, String displayName, BigDecimal annualEntitlement, boolean prorated,
                                int backdateDaysAllowed) {}

    public record HolidayView(LocalDate date, String name) {}

    @GetMapping("/leave-types")
    public List<LeaveTypeView> leaveTypes() {
        return types.findByActiveTrueOrderByCode().stream()
                .map(t -> new LeaveTypeView(t.getCode(), t.getDisplayName(), t.getAnnualEntitlement(), t.isProrated(),
                        t.getBackdateDaysAllowed()))
                .toList();
    }

    @GetMapping("/holidays")
    public List<HolidayView> holidays(@RequestParam int year) {
        return holidays.findByHolidayDateBetweenOrderByHolidayDate(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream().map(h -> new HolidayView(h.getHolidayDate(), h.getName())).toList();
    }
}
