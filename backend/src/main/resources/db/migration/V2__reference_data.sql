-- Reference data (implementation.md §6, assumption A3)
INSERT INTO leave_type (code, display_name, annual_entitlement, prorated, backdate_days_allowed) VALUES
  ('ANNUAL', 'Annual Leave', 18.0, true, 0),
  ('CASUAL', 'Casual Leave',  8.0, true, 0),
  ('SICK',   'Sick Leave',   10.0, true, 7);

-- 2026 holidays (national + demo values)
INSERT INTO holiday (holiday_date, name) VALUES
  ('2026-01-26', 'Republic Day'),
  ('2026-03-04', 'Holi'),
  ('2026-04-03', 'Good Friday'),
  ('2026-05-01', 'Labour Day'),
  ('2026-08-15', 'Independence Day'),
  ('2026-10-02', 'Gandhi Jayanti'),
  ('2026-10-20', 'Diwali'),
  ('2026-12-25', 'Christmas'),
  ('2027-01-26', 'Republic Day'),
  ('2027-08-15', 'Independence Day'),
  ('2027-10-02', 'Gandhi Jayanti'),
  ('2027-12-25', 'Christmas');
