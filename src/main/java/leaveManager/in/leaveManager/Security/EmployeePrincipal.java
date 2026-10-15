package leaveManager.in.leaveManager.Security;

import leaveManager.in.leaveManager.Model.Employee;
import leaveManager.in.leaveManager.Model.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The logged-in user as Spring Security sees them. Carries the employee id so controllers
 * can use @AuthenticationPrincipal and know who is calling without another database lookup.
 */
public class EmployeePrincipal implements UserDetails {

    private final Integer id;
    private final String email;
    private final String password;
    private final Role role;

    public EmployeePrincipal(Integer id, String email, String password, Role role) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public static EmployeePrincipal from(Employee employee) {
        return new EmployeePrincipal(employee.getId(), employee.getEmail(), employee.getPassword(), employee.getRole());
    }

    public Integer getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public boolean isManager() {
        return role == Role.MANAGER;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // hasRole('MANAGER') checks for the authority "ROLE_MANAGER"
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
