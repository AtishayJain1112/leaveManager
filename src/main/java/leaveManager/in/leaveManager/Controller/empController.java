package leaveManager.in.leaveManager.Controller;

import jakarta.validation.Valid;
import leaveManager.in.leaveManager.DAO.RequestEmployeeDao;
import leaveManager.in.leaveManager.DAO.ResponseEmployeeDAO;
import leaveManager.in.leaveManager.Security.EmployeePrincipal;
import leaveManager.in.leaveManager.Service.EmployeeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class empController {
    private EmployeeService employeeService;
    public empController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    // Managers manage employees. Any logged-in user can read their own record via /employees/me.

    @GetMapping("/employees")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<ResponseEmployeeDAO>> getAllEmployees() {
        return ResponseEntity.ok(employeeService.getAllEmployees());
    }

    @GetMapping("/employees/me")
    public ResponseEntity<ResponseEmployeeDAO> getCurrentEmployee(@AuthenticationPrincipal EmployeePrincipal principal) {
        return ResponseEntity.ok(employeeService.getEmployees(principal.getId()));
    }

    @GetMapping("/employees/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseEmployeeDAO> getEmployees(@PathVariable Integer id) {
        return ResponseEntity.ok(employeeService.getEmployees(id));
    }

    @PostMapping("/employees")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseEmployeeDAO> createEmployee(@Valid @RequestBody RequestEmployeeDao empl) {
        ResponseEmployeeDAO savedEmployee = employeeService.save(empl);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedEmployee);
    }

    @PutMapping("/employees/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseEmployeeDAO> updateEmployee(@PathVariable Integer id,
                                                              @Valid @RequestBody RequestEmployeeDao empl) {
        return ResponseEntity.ok(employeeService.update(id, empl));
    }

    @DeleteMapping("/employees/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Integer id) {
        employeeService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
