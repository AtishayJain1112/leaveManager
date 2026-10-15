package leaveManager.in.leaveManager.Service;

import leaveManager.in.leaveManager.DAO.PageResponse;
import leaveManager.in.leaveManager.DAO.RequestLeaveDao;
import leaveManager.in.leaveManager.DAO.ResponseLeaveDAO;
import leaveManager.in.leaveManager.Exceptions.BadRequestException;
import leaveManager.in.leaveManager.Exceptions.ConflictException;
import leaveManager.in.leaveManager.Exceptions.ResourceNotFoundException;
import leaveManager.in.leaveManager.Model.Employee;
import leaveManager.in.leaveManager.Model.LeaveRequest;
import leaveManager.in.leaveManager.Model.Role;
import leaveManager.in.leaveManager.Model.Status;
import leaveManager.in.leaveManager.Repository.EmployeeRepository;
import leaveManager.in.leaveManager.Repository.LeaveRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private LeaveRequestService leaveRequestService;

    private final LocalDate today = LocalDate.now();

    private Employee employee(Integer id, int balance) {
        Employee e = new Employee("Emp" + id, "emp" + id + "@company.com", Role.EMPLOYEE, balance);
        e.setId(id);
        return e;
    }

    private LeaveRequest leave(Integer id, Employee owner, Status status, LocalDate start, LocalDate end) {
        LeaveRequest l = new LeaveRequest();
        l.setId(id);
        l.setEmployee(owner);
        l.setStartDate(start);
        l.setEndDate(end);
        l.setReason("Trip");
        l.setStatus(status);
        return l;
    }

    // ------------------------------------------------------------------ create

    @Test
    void create_savesPendingRequestAndDoesNotDeductBalance() {
        Employee emp = employee(1, 20);
        LocalDate start = today.plusDays(5);
        LocalDate end = start.plusDays(2); // 3 days
        when(employeeRepository.findById(1)).thenReturn(Optional.of(emp));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseLeaveDAO result = leaveRequestService.create(1, new RequestLeaveDao(start, end, "Vacation"));

        assertThat(result.status()).isEqualTo(Status.PENDING);
        assertThat(result.days()).isEqualTo(3L);
        assertThat(result.employeeId()).isEqualTo(1);
        assertThat(emp.getLeaveBalance()).isEqualTo(20);
    }

    @Test
    void create_singleDayRequestCountsAsOneDay() {
        Employee emp = employee(1, 20);
        LocalDate day = today.plusDays(3);
        when(employeeRepository.findById(1)).thenReturn(Optional.of(emp));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseLeaveDAO result = leaveRequestService.create(1, new RequestLeaveDao(day, day, "Appointment"));

        assertThat(result.days()).isEqualTo(1L);
    }

    @Test
    void create_unknownEmployee_throwsNotFound() {
        when(employeeRepository.findById(7)).thenReturn(Optional.empty());
        RequestLeaveDao dto = new RequestLeaveDao(today.plusDays(1), today.plusDays(2), "x");

        assertThatThrownBy(() -> leaveRequestService.create(7, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_endDateBeforeStartDate_throwsBadRequest() {
        when(employeeRepository.findById(1)).thenReturn(Optional.of(employee(1, 20)));
        RequestLeaveDao dto = new RequestLeaveDao(today.plusDays(5), today.plusDays(3), "x");

        assertThatThrownBy(() -> leaveRequestService.create(1, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("End date");
        verify(leaveRequestRepository, never()).save(any(LeaveRequest.class));
    }

    @Test
    void create_startDateInPast_throwsBadRequest() {
        when(employeeRepository.findById(1)).thenReturn(Optional.of(employee(1, 20)));
        RequestLeaveDao dto = new RequestLeaveDao(today.minusDays(1), today.plusDays(1), "x");

        assertThatThrownBy(() -> leaveRequestService.create(1, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("past");
        verify(leaveRequestRepository, never()).save(any(LeaveRequest.class));
    }

    @Test
    void create_moreDaysThanBalance_throwsBadRequest() {
        when(employeeRepository.findById(1)).thenReturn(Optional.of(employee(1, 2)));
        RequestLeaveDao dto = new RequestLeaveDao(today.plusDays(1), today.plusDays(3), "x"); // 3 days

        assertThatThrownBy(() -> leaveRequestService.create(1, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient leave balance");
        verify(leaveRequestRepository, never()).save(any(LeaveRequest.class));
    }

    @Test
    void create_overlappingExistingRequest_throwsConflict() {
        when(employeeRepository.findById(1)).thenReturn(Optional.of(employee(1, 20)));
        when(leaveRequestRepository.countOverlapping(any(), any(), any(), any(), any())).thenReturn(1L);
        RequestLeaveDao dto = new RequestLeaveDao(today.plusDays(1), today.plusDays(2), "x");

        assertThatThrownBy(() -> leaveRequestService.create(1, dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("overlapping");
        verify(leaveRequestRepository, never()).save(any(LeaveRequest.class));
    }

    // ------------------------------------------------------------------ approve / reject

    @Test
    void approve_marksApprovedAndDeductsBalance() {
        Employee emp = employee(1, 10);
        LeaveRequest request = leave(5, emp, Status.PENDING, today.plusDays(1), today.plusDays(3)); // 3 days
        when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseLeaveDAO result = leaveRequestService.approve(5);

        assertThat(result.status()).isEqualTo(Status.APPROVED);
        assertThat(emp.getLeaveBalance()).isEqualTo(7);
        verify(employeeRepository).save(emp);
    }

    @Test
    void approve_alreadyApproved_throwsConflictAndKeepsBalance() {
        Employee emp = employee(1, 10);
        LeaveRequest request = leave(5, emp, Status.APPROVED, today.plusDays(1), today.plusDays(3));
        when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> leaveRequestService.approve(5))
                .isInstanceOf(ConflictException.class);
        assertThat(emp.getLeaveBalance()).isEqualTo(10);
    }

    @Test
    void approve_balanceTooLowNow_throwsBadRequest() {
        Employee emp = employee(1, 1);
        LeaveRequest request = leave(5, emp, Status.PENDING, today.plusDays(1), today.plusDays(3)); // 3 days
        when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> leaveRequestService.approve(5))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot approve");
        assertThat(request.getStatus()).isEqualTo(Status.PENDING);
        assertThat(emp.getLeaveBalance()).isEqualTo(1);
    }

    @Test
    void approve_unknownRequest_throwsNotFound() {
        when(leaveRequestRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveRequestService.approve(404))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reject_marksRejectedAndKeepsBalance() {
        Employee emp = employee(1, 10);
        LeaveRequest request = leave(5, emp, Status.PENDING, today.plusDays(1), today.plusDays(3));
        when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseLeaveDAO result = leaveRequestService.reject(5);

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(emp.getLeaveBalance()).isEqualTo(10);
    }

    @Test
    void reject_notPending_throwsConflict() {
        LeaveRequest request = leave(5, employee(1, 10), Status.REJECTED, today.plusDays(1), today.plusDays(2));
        when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> leaveRequestService.reject(5))
                .isInstanceOf(ConflictException.class);
    }

    // ------------------------------------------------------------------ read access

    @Test
    void getById_otherEmployeesRequest_isForbiddenForEmployee() {
        LeaveRequest request = leave(5, employee(1, 10), Status.PENDING, today.plusDays(1), today.plusDays(2));
        when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> leaveRequestService.getById(5, 2, false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getById_ownRequest_isAllowed() {
        LeaveRequest request = leave(5, employee(1, 10), Status.PENDING, today.plusDays(1), today.plusDays(2));
        when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(request));

        ResponseLeaveDAO result = leaveRequestService.getById(5, 1, false);

        assertThat(result.id()).isEqualTo(5);
    }

    @Test
    void getById_manager_canSeeAnyRequest() {
        LeaveRequest request = leave(5, employee(1, 10), Status.PENDING, today.plusDays(1), today.plusDays(2));
        when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(request));

        ResponseLeaveDAO result = leaveRequestService.getById(5, 99, true);

        assertThat(result.employeeId()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ update

    @Test
    void update_byOwnerWhilePending_changesDatesAndReason() {
        Employee emp = employee(1, 20);
        LeaveRequest request = leave(5, emp, Status.PENDING, today.plusDays(1), today.plusDays(2));
        when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate newStart = today.plusDays(10);
        LocalDate newEnd = today.plusDays(12);
        ResponseLeaveDAO result = leaveRequestService.update(5, 1, new RequestLeaveDao(newStart, newEnd, "Changed"));

        assertThat(result.startDate()).isEqualTo(newStart);
        assertThat(result.endDate()).isEqualTo(newEnd);
        assertThat(result.reason()).isEqualTo("Changed");
    }

    @Test
    void update_byAnotherEmployee_isForbidden() {
        LeaveRequest request = leave(5, employee(1, 20), Status.PENDING, today.plusDays(1), today.plusDays(2));
        when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(request));
        RequestLeaveDao dto = new RequestLeaveDao(today.plusDays(3), today.plusDays(4), "x");

        assertThatThrownBy(() -> leaveRequestService.update(5, 2, dto))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_alreadyApproved_throwsConflict() {
        LeaveRequest request = leave(5, employee(1, 20), Status.APPROVED, today.plusDays(1), today.plusDays(2));
        when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(request));
        RequestLeaveDao dto = new RequestLeaveDao(today.plusDays(3), today.plusDays(4), "x");

        assertThatThrownBy(() -> leaveRequestService.update(5, 1, dto))
                .isInstanceOf(ConflictException.class);
    }

    // ------------------------------------------------------------------ delete

    @Test
    void delete_ownPendingRequest_deletesIt() {
        LeaveRequest request = leave(5, employee(1, 10), Status.PENDING, today.plusDays(1), today.plusDays(2));
        when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(request));

        leaveRequestService.delete(5, 1, false);

        verify(leaveRequestRepository).delete(request);
    }

    @Test
    void delete_employeeCannotDeleteApprovedRequest() {
        LeaveRequest request = leave(5, employee(1, 10), Status.APPROVED, today.plusDays(1), today.plusDays(2));
        when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> leaveRequestService.delete(5, 1, false))
                .isInstanceOf(ConflictException.class);
        verify(leaveRequestRepository, never()).delete(any(LeaveRequest.class));
    }

    @Test
    void delete_otherEmployeesRequest_isForbidden() {
        LeaveRequest request = leave(5, employee(1, 10), Status.PENDING, today.plusDays(1), today.plusDays(2));
        when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> leaveRequestService.delete(5, 2, false))
                .isInstanceOf(AccessDeniedException.class);
        verify(leaveRequestRepository, never()).delete(any(LeaveRequest.class));
    }

    @Test
    void delete_managerDeletingApprovedRequest_refundsDays() {
        Employee emp = employee(1, 7);
        LeaveRequest request = leave(5, emp, Status.APPROVED, today.plusDays(1), today.plusDays(3)); // 3 days
        when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(request));

        leaveRequestService.delete(5, 99, true);

        assertThat(emp.getLeaveBalance()).isEqualTo(10);
        verify(leaveRequestRepository).delete(request);
    }

    // ------------------------------------------------------------------ list (pagination + sorting)

    @Test
    void list_filtersByEmployeeAndReturnsPageInfo() {
        Employee emp = employee(1, 10);
        LeaveRequest request = leave(5, emp, Status.PENDING, today.plusDays(1), today.plusDays(2));
        Pageable pageable = PageRequest.of(0, 10, Sort.by("startDate"));
        when(leaveRequestRepository.findByEmployeeId(eq(1), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(request)));

        PageResponse<ResponseLeaveDAO> result = leaveRequestService.list(1, null, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.content().get(0).id()).isEqualTo(5);
    }

    @Test
    void list_withoutFilters_returnsEverything() {
        Employee emp = employee(1, 10);
        LeaveRequest request = leave(5, emp, Status.PENDING, today.plusDays(1), today.plusDays(2));
        Pageable pageable = PageRequest.of(0, 10, Sort.by("endDate").descending());
        when(leaveRequestRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(request)));

        PageResponse<ResponseLeaveDAO> result = leaveRequestService.list(null, null, pageable);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    void list_filtersByStatus() {
        Employee emp = employee(1, 10);
        LeaveRequest request = leave(5, emp, Status.APPROVED, today.plusDays(1), today.plusDays(2));
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));
        when(leaveRequestRepository.findByStatus(eq(Status.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(request)));

        PageResponse<ResponseLeaveDAO> result = leaveRequestService.list(null, Status.APPROVED, pageable);

        assertThat(result.content().get(0).status()).isEqualTo(Status.APPROVED);
    }

    @Test
    void list_sortByUnknownField_throwsBadRequest() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("password"));

        assertThatThrownBy(() -> leaveRequestService.list(null, null, pageable))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot sort by");
    }
}
