package leaveManager.in.leaveManager.Service;

import leaveManager.in.leaveManager.DAO.PageResponse;
import leaveManager.in.leaveManager.DAO.RequestLeaveDao;
import leaveManager.in.leaveManager.DAO.ResponseLeaveDAO;
import leaveManager.in.leaveManager.Exceptions.BadRequestException;
import leaveManager.in.leaveManager.Exceptions.ConflictException;
import leaveManager.in.leaveManager.Exceptions.ResourceNotFoundException;
import leaveManager.in.leaveManager.Model.Employee;
import leaveManager.in.leaveManager.Model.LeaveRequest;
import leaveManager.in.leaveManager.Model.Status;
import leaveManager.in.leaveManager.Repository.EmployeeRepository;
import leaveManager.in.leaveManager.Repository.LeaveRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class LeaveRequestService {

    // Only these fields may be used in ?sort=... (anything else would crash the query with a 500).
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "startDate", "endDate", "status");

    // Pending and approved requests both "occupy" the dates, rejected ones do not.
    private static final List<Status> ACTIVE_STATUSES = List.of(Status.PENDING, Status.APPROVED);

    // Used as "no request to exclude" when checking for overlapping dates on create.
    private static final Integer NO_ID = -1;

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository,
                               EmployeeRepository employeeRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
    }

    /** An employee applies for leave. The balance is only deducted when a manager approves. */
    public ResponseLeaveDAO create(Integer employeeId, RequestLeaveDao dto) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id " + employeeId));
        validate(employee, dto, NO_ID);

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(employee);
        leaveRequest.setStartDate(dto.startDate());
        leaveRequest.setEndDate(dto.endDate());
        leaveRequest.setReason(dto.reason().trim());
        leaveRequest.setStatus(Status.PENDING);
        return ResponseLeaveDAO.from(leaveRequestRepository.save(leaveRequest));
    }

    /**
     * Paginated + sorted list. employeeId and status are optional filters.
     * Callers decide the scope: employees pass their own id, managers pass null (everyone) or any id.
     */
    @Transactional(readOnly = true)
    public PageResponse<ResponseLeaveDAO> list(Integer employeeId, Status status, Pageable pageable) {
        validateSort(pageable);

        Page<LeaveRequest> page;
        if (employeeId != null && status != null) {
            page = leaveRequestRepository.findByEmployeeIdAndStatus(employeeId, status, pageable);
        } else if (employeeId != null) {
            page = leaveRequestRepository.findByEmployeeId(employeeId, pageable);
        } else if (status != null) {
            page = leaveRequestRepository.findByStatus(status, pageable);
        } else {
            page = leaveRequestRepository.findAll(pageable);
        }
        return PageResponse.from(page.map(ResponseLeaveDAO::from));
    }

    @Transactional(readOnly = true)
    public ResponseLeaveDAO getById(Integer id, Integer requesterId, boolean requesterIsManager) {
        LeaveRequest leaveRequest = findOrThrow(id);
        if (!requesterIsManager && !isOwner(leaveRequest, requesterId)) {
            throw new AccessDeniedException("You can only view your own leave requests");
        }
        return ResponseLeaveDAO.from(leaveRequest);
    }

    /** The owner can edit their request, but only while it is still PENDING. */
    public ResponseLeaveDAO update(Integer id, Integer requesterId, RequestLeaveDao dto) {
        LeaveRequest leaveRequest = findOrThrow(id);
        if (!isOwner(leaveRequest, requesterId)) {
            throw new AccessDeniedException("You can only edit your own leave requests");
        }
        if (leaveRequest.getStatus() != Status.PENDING) {
            throw new ConflictException("Only pending leave requests can be edited; this one is "
                    + leaveRequest.getStatus());
        }
        validate(leaveRequest.getEmployee(), dto, leaveRequest.getId());

        leaveRequest.setStartDate(dto.startDate());
        leaveRequest.setEndDate(dto.endDate());
        leaveRequest.setReason(dto.reason().trim());
        return ResponseLeaveDAO.from(leaveRequestRepository.save(leaveRequest));
    }

    /**
     * Employees can cancel (delete) their own PENDING requests. Managers can delete any request;
     * if it was already APPROVED, the days are given back to the employee.
     */
    public void delete(Integer id, Integer requesterId, boolean requesterIsManager) {
        LeaveRequest leaveRequest = findOrThrow(id);
        if (!requesterIsManager) {
            if (!isOwner(leaveRequest, requesterId)) {
                throw new AccessDeniedException("You can only delete your own leave requests");
            }
            if (leaveRequest.getStatus() != Status.PENDING) {
                throw new ConflictException("Only pending leave requests can be cancelled; this one is "
                        + leaveRequest.getStatus());
            }
        }
        if (leaveRequest.getStatus() == Status.APPROVED) {
            Employee employee = leaveRequest.getEmployee();
            employee.setLeaveBalance(employee.getLeaveBalance() + (int) leaveRequest.calculateDays());
            employeeRepository.save(employee);
        }
        leaveRequestRepository.delete(leaveRequest);
    }

    /** Manager approves: status becomes APPROVED and the days are deducted from the balance. */
    public ResponseLeaveDAO approve(Integer id) {
        LeaveRequest leaveRequest = findOrThrow(id);
        requirePending(leaveRequest, "approved");

        Employee employee = leaveRequest.getEmployee();
        long days = leaveRequest.calculateDays();
        if (days > employee.getLeaveBalance()) {
            throw new BadRequestException("Cannot approve: request needs " + days + " day(s) but the employee only has "
                    + employee.getLeaveBalance() + " left");
        }
        employee.setLeaveBalance(employee.getLeaveBalance() - (int) days);
        employeeRepository.save(employee);

        leaveRequest.setStatus(Status.APPROVED);
        return ResponseLeaveDAO.from(leaveRequestRepository.save(leaveRequest));
    }

    /** Manager rejects: status becomes REJECTED, balance is untouched. */
    public ResponseLeaveDAO reject(Integer id) {
        LeaveRequest leaveRequest = findOrThrow(id);
        requirePending(leaveRequest, "rejected");

        leaveRequest.setStatus(Status.REJECTED);
        return ResponseLeaveDAO.from(leaveRequestRepository.save(leaveRequest));
    }

    // ---------------------------------------------------------------- helpers

    /** All rules for a new/edited request: date order, not in the past, enough balance, no overlap. */
    private void validate(Employee employee, RequestLeaveDao dto, Integer excludeRequestId) {
        LocalDate start = dto.startDate();
        LocalDate end = dto.endDate();

        if (end.isBefore(start)) {
            throw new BadRequestException("End date cannot be before start date");
        }
        if (start.isBefore(LocalDate.now())) {
            throw new BadRequestException("Start date cannot be in the past");
        }

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > employee.getLeaveBalance()) {
            throw new BadRequestException("Insufficient leave balance: requested " + days
                    + " day(s) but only " + employee.getLeaveBalance() + " available");
        }

        long overlapping = leaveRequestRepository.countOverlapping(
                employee.getId(), ACTIVE_STATUSES, start, end, excludeRequestId);
        if (overlapping > 0) {
            throw new ConflictException("Employee already has a pending or approved leave request overlapping these dates");
        }
    }

    private void validateSort(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty()
                        + "'. Allowed fields: " + String.join(", ", ALLOWED_SORT_FIELDS));
            }
        });
    }

    private void requirePending(LeaveRequest leaveRequest, String action) {
        if (leaveRequest.getStatus() != Status.PENDING) {
            throw new ConflictException("Only pending leave requests can be " + action
                    + "; this one is already " + leaveRequest.getStatus());
        }
    }

    private boolean isOwner(LeaveRequest leaveRequest, Integer requesterId) {
        return leaveRequest.getEmployee().getId().equals(requesterId);
    }

    private LeaveRequest findOrThrow(Integer id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with id " + id));
    }
}
