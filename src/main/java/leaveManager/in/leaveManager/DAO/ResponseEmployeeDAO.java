package leaveManager.in.leaveManager.DAO;

import leaveManager.in.leaveManager.Model.Employee;
import leaveManager.in.leaveManager.Model.Role;

/** What the API returns for an employee. The password hash is deliberately not included. */
public record ResponseEmployeeDAO(
        Integer id,
        String name,
        String email,
        Role role,
        int leaveBalance
) {
    public static ResponseEmployeeDAO from(Employee e) {
        return new ResponseEmployeeDAO(e.getId(), e.getName(), e.getEmail(), e.getRole(), e.getLeaveBalance());
    }
}
