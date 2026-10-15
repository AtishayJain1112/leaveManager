package leaveManager.in.leaveManager.Model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "employees")
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private String email;

    // BCrypt hash, used by Spring Security for login. Never returned by the API.
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    private int leaveBalance;

    // @ToString/@EqualsAndHashCode excluded: Employee <-> LeaveRequest point at each other,
    // so Lombok's generated toString()/hashCode() would otherwise recurse forever.
    @OneToMany(mappedBy = "employee", cascade = CascadeType.REMOVE)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<LeaveRequest> leaveRequests = new ArrayList<>();

    public Employee(String name, String email, Role role, int leaveBalance) {
        this.name = name;
        this.email = email;
        this.role = role;
        this.leaveBalance = leaveBalance;
    }
}
