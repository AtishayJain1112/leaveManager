package leaveManager.in.leaveManager.Service;

import leaveManager.in.leaveManager.DAO.RequestEmployeeDao;
import leaveManager.in.leaveManager.DAO.ResponseEmployeeDAO;
import leaveManager.in.leaveManager.Exceptions.BadRequestException;
import leaveManager.in.leaveManager.Exceptions.ConflictException;
import leaveManager.in.leaveManager.Exceptions.ResourceNotFoundException;
import leaveManager.in.leaveManager.Model.Employee;
import leaveManager.in.leaveManager.Model.Role;
import leaveManager.in.leaveManager.Repository.EmployeeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class EmployeeService {

    static final int DEFAULT_LEAVE_BALANCE = 20;

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(EmployeeRepository employeeRepository, PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<ResponseEmployeeDAO> getAllEmployees() {
        return employeeRepository.findAll().stream().map(ResponseEmployeeDAO::from).toList();
    }

    @Transactional(readOnly = true)
    public ResponseEmployeeDAO getEmployees(Integer id) {
        return ResponseEmployeeDAO.from(findOrThrow(id));
    }

    public ResponseEmployeeDAO save(RequestEmployeeDao dto) {
        if (dto.password() == null || dto.password().isBlank()) {
            throw new BadRequestException("Password is required when creating an employee");
        }
        String email = normalize(dto.email());
        if (employeeRepository.existsByEmail(email)) {
            throw new ConflictException("An employee with email " + email + " already exists");
        }

        Employee employee = new Employee();
        employee.setName(dto.name().trim());
        employee.setEmail(email);
        employee.setPassword(passwordEncoder.encode(dto.password()));
        employee.setRole(dto.role() != null ? dto.role() : Role.EMPLOYEE);
        employee.setLeaveBalance(dto.leaveBalance() != null ? dto.leaveBalance() : DEFAULT_LEAVE_BALANCE);
        return ResponseEmployeeDAO.from(employeeRepository.save(employee));
    }

    public ResponseEmployeeDAO update(Integer id, RequestEmployeeDao dto) {
        Employee employee = findOrThrow(id);
        String email = normalize(dto.email());
        if (employeeRepository.existsByEmailAndIdNot(email, id)) {
            throw new ConflictException("An employee with email " + email + " already exists");
        }

        employee.setName(dto.name().trim());
        employee.setEmail(email);
        if (dto.role() != null) {
            employee.setRole(dto.role());
        }
        if (dto.leaveBalance() != null) {
            employee.setLeaveBalance(dto.leaveBalance());
        }
        if (dto.password() != null && !dto.password().isBlank()) {
            employee.setPassword(passwordEncoder.encode(dto.password()));
        }
        return ResponseEmployeeDAO.from(employeeRepository.save(employee));
    }

    public void delete(Integer id) {
        Employee employee = findOrThrow(id);
        employeeRepository.delete(employee);
    }

    private Employee findOrThrow(Integer id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id " + id));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
