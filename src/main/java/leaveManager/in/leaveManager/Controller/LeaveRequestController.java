package leaveManager.in.leaveManager.Controller;

import jakarta.validation.Valid;
import leaveManager.in.leaveManager.DAO.PageResponse;
import leaveManager.in.leaveManager.DAO.RequestLeaveDao;
import leaveManager.in.leaveManager.DAO.ResponseLeaveDAO;
import leaveManager.in.leaveManager.Model.Status;
import leaveManager.in.leaveManager.Security.EmployeePrincipal;
import leaveManager.in.leaveManager.Service.LeaveRequestService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/leave-requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    // Employee (or manager) applies for their own leave.
    @PostMapping
    public ResponseEntity<ResponseLeaveDAO> create(@Valid @RequestBody RequestLeaveDao dto,
                                                   @AuthenticationPrincipal EmployeePrincipal me) {
        ResponseLeaveDAO created = leaveRequestService.create(me.getId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // GET /leave-requests?page=0&size=10&sort=startDate,desc&status=PENDING&employeeId=3
    // Employees only ever see their own requests; managers see everyone's (employeeId filter optional).
    @GetMapping
    public ResponseEntity<PageResponse<ResponseLeaveDAO>> list(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Integer employeeId,
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal EmployeePrincipal me) {
        Integer scope = me.isManager() ? employeeId : me.getId();
        return ResponseEntity.ok(leaveRequestService.list(scope, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseLeaveDAO> getById(@PathVariable Integer id,
                                                    @AuthenticationPrincipal EmployeePrincipal me) {
        return ResponseEntity.ok(leaveRequestService.getById(id, me.getId(), me.isManager()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseLeaveDAO> update(@PathVariable Integer id,
                                                   @Valid @RequestBody RequestLeaveDao dto,
                                                   @AuthenticationPrincipal EmployeePrincipal me) {
        return ResponseEntity.ok(leaveRequestService.update(id, me.getId(), dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id,
                                       @AuthenticationPrincipal EmployeePrincipal me) {
        leaveRequestService.delete(id, me.getId(), me.isManager());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseLeaveDAO> approve(@PathVariable Integer id) {
        return ResponseEntity.ok(leaveRequestService.approve(id));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseLeaveDAO> reject(@PathVariable Integer id) {
        return ResponseEntity.ok(leaveRequestService.reject(id));
    }
}
