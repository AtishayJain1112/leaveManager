package leaveManager.in.leaveManager.DAO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import leaveManager.in.leaveManager.Model.Role;

/**
 * Body for POST /employees and PUT /employees/{id}.
 * password is required when creating; on update it is optional (blank = keep the old one).
 * role defaults to EMPLOYEE and leaveBalance defaults to 20 when omitted on create.
 */
public record RequestEmployeeDao(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @Size(min = 6, max = 72, message = "Password must be between 6 and 72 characters")
        String password,

        Role role,

        @Min(value = 0, message = "Leave balance cannot be negative")
        @Max(value = 365, message = "Leave balance cannot be more than 365")
        Integer leaveBalance
) {
}
