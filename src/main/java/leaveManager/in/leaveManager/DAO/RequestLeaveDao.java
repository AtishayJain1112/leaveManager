package leaveManager.in.leaveManager.DAO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Body for POST /leave-requests and PUT /leave-requests/{id}. Dates use the format yyyy-MM-dd. */
public record RequestLeaveDao(
        @NotNull(message = "Start date is required (format yyyy-MM-dd)")
        LocalDate startDate,

        @NotNull(message = "End date is required (format yyyy-MM-dd)")
        LocalDate endDate,

        @NotBlank(message = "Reason is required")
        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason
) {
}
