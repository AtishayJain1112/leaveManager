package leaveManager.in.leaveManager.Service;

import leaveManager.in.leaveManager.DAO.RequestEmployeeDao;
import leaveManager.in.leaveManager.DAO.ResponseEmployeeDAO;
import leaveManager.in.leaveManager.Exceptions.BadRequestException;
import leaveManager.in.leaveManager.Exceptions.ConflictException;
import leaveManager.in.leaveManager.Exceptions.ResourceNotFoundException;
import leaveManager.in.leaveManager.Model.Employee;
import leaveManager.in.leaveManager.Model.Role;
import leaveManager.in.leaveManager.Repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee employee(Integer id, String name, String email, int balance) {
        Employee e = new Employee(name, email, Role.EMPLOYEE, balance);
        e.setId(id);
        e.setPassword("OLDHASH");
        return e;
    }

    @Test
    void save_createsEmployeeWithEncodedPasswordAndDefaults() {
        when(employeeRepository.existsByEmail("john@company.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("ENCODED");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        RequestEmployeeDao dto = new RequestEmployeeDao("  John  ", "John@Company.com", "secret123", null, null);
        ResponseEmployeeDAO result = employeeService.save(dto);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        Employee saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("John");
        assertThat(saved.getEmail()).isEqualTo("john@company.com");
        assertThat(saved.getPassword()).isEqualTo("ENCODED");
        assertThat(saved.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(saved.getLeaveBalance()).isEqualTo(20);
        assertThat(result.email()).isEqualTo("john@company.com");
    }

    @Test
    void save_usesRoleAndBalanceFromRequestWhenGiven() {
        when(employeeRepository.existsByEmail("boss@company.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("ENCODED");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEmployeeDAO result = employeeService.save(
                new RequestEmployeeDao("Boss", "boss@company.com", "secret123", Role.MANAGER, 30));

        assertThat(result.role()).isEqualTo(Role.MANAGER);
        assertThat(result.leaveBalance()).isEqualTo(30);
    }

    @Test
    void save_withoutPassword_throwsBadRequest() {
        RequestEmployeeDao dto = new RequestEmployeeDao("John", "john@company.com", null, null, null);

        assertThatThrownBy(() -> employeeService.save(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Password");
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void save_duplicateEmail_throwsConflict() {
        when(employeeRepository.existsByEmail("john@company.com")).thenReturn(true);
        RequestEmployeeDao dto = new RequestEmployeeDao("John", "john@company.com", "secret123", null, null);

        assertThatThrownBy(() -> employeeService.save(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void getAllEmployees_returnsMappedList() {
        when(employeeRepository.findAll()).thenReturn(List.of(
                employee(1, "A", "a@company.com", 10),
                employee(2, "B", "b@company.com", 12)));

        List<ResponseEmployeeDAO> result = employeeService.getAllEmployees();

        assertThat(result).hasSize(2);
        assertThat(result.get(1).name()).isEqualTo("B");
    }

    @Test
    void getEmployees_unknownId_throwsNotFound() {
        when(employeeRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getEmployees(99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void update_changesFieldsAndKeepsPasswordWhenBlank() {
        Employee existing = employee(1, "Old", "old@company.com", 10);
        when(employeeRepository.findById(1)).thenReturn(Optional.of(existing));
        when(employeeRepository.existsByEmailAndIdNot("new@company.com", 1)).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEmployeeDAO result = employeeService.update(1,
                new RequestEmployeeDao("New", "new@company.com", null, Role.MANAGER, 15));

        assertThat(result.name()).isEqualTo("New");
        assertThat(result.email()).isEqualTo("new@company.com");
        assertThat(result.role()).isEqualTo(Role.MANAGER);
        assertThat(result.leaveBalance()).isEqualTo(15);
        assertThat(existing.getPassword()).isEqualTo("OLDHASH");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void update_emailUsedByAnotherEmployee_throwsConflict() {
        Employee existing = employee(1, "Old", "old@company.com", 10);
        when(employeeRepository.findById(1)).thenReturn(Optional.of(existing));
        when(employeeRepository.existsByEmailAndIdNot("taken@company.com", 1)).thenReturn(true);

        RequestEmployeeDao dto = new RequestEmployeeDao("Old", "taken@company.com", null, null, null);

        assertThatThrownBy(() -> employeeService.update(1, dto))
                .isInstanceOf(ConflictException.class);
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void delete_existingEmployee_deletesIt() {
        Employee existing = employee(1, "A", "a@company.com", 10);
        when(employeeRepository.findById(1)).thenReturn(Optional.of(existing));

        employeeService.delete(1);

        verify(employeeRepository).delete(existing);
    }

    @Test
    void delete_unknownEmployee_throwsNotFound() {
        when(employeeRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.delete(99))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(employeeRepository, never()).delete(any(Employee.class));
    }
}
