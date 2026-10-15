package leaveManager.in.leaveManager.DAO;

import leaveManager.in.leaveManager.Model.LeaveRequest;
import leaveManager.in.leaveManager.Model.Status;

import java.time.LocalDate;

public record ResponseLeaveDAO(
        Integer id,
        Integer employeeId,
        String employeeName,
        LocalDate startDate,
        LocalDate endDate,
        long days,
        String reason,
        Status status
) {
    public static ResponseLeaveDAO from(LeaveRequest l) {
        return new ResponseLeaveDAO(
                l.getId(),
                l.getEmployee().getId(),
                l.getEmployee().getName(),
                l.getStartDate(),
                l.getEndDate(),
                l.calculateDays(),
                l.getReason(),
                l.getStatus());
    }
}
