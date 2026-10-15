package leaveManager.in.leaveManager.Config;

import leaveManager.in.leaveManager.Model.Employee;
import leaveManager.in.leaveManager.Model.Role;
import leaveManager.in.leaveManager.Repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates two demo users the first time the app starts on an empty database, so you can log in
 * straight away (otherwise nobody could create the first manager, because creating employees needs a manager).
 * Turn off with app.seed.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final String managerPassword;
    private final String employeePassword;

    public DataSeeder(EmployeeRepository employeeRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.seed.manager-password:manager123}") String managerPassword,
                      @Value("${app.seed.employee-password:employee123}") String employeePassword) {
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.managerPassword = managerPassword;
        this.employeePassword = employeePassword;
    }

    @Override
    public void run(String... args) {
        if (employeeRepository.count() > 0) {
            return;
        }

        Employee manager = new Employee("Admin Manager", "manager@company.com", Role.MANAGER, 20);
        manager.setPassword(passwordEncoder.encode(managerPassword));
        employeeRepository.save(manager);

        Employee employee = new Employee("John Employee", "john@company.com", Role.EMPLOYEE, 20);
        employee.setPassword(passwordEncoder.encode(employeePassword));
        employeeRepository.save(employee);
    }
}
