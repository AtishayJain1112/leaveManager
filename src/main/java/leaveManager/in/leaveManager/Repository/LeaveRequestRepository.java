package leaveManager.in.leaveManager.Repository;

import leaveManager.in.leaveManager.Model.LeaveRequest;
import leaveManager.in.leaveManager.Model.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Integer> {

    // The entity graph loads the employee in the same query, avoiding one extra query per row.
    @Override
    @EntityGraph(attributePaths = "employee")
    Page<LeaveRequest> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "employee")
    Page<LeaveRequest> findByEmployeeId(Integer employeeId, Pageable pageable);

    @EntityGraph(attributePaths = "employee")
    Page<LeaveRequest> findByStatus(Status status, Pageable pageable);

    @EntityGraph(attributePaths = "employee")
    Page<LeaveRequest> findByEmployeeIdAndStatus(Integer employeeId, Status status, Pageable pageable);

    /**
     * How many of this employee's requests (in the given statuses) overlap [startDate, endDate].
     * Two ranges overlap when each one starts on or before the other one ends.
     * excludeId lets an update ignore the request being edited (use -1 when creating).
     */
    @Query("select count(l) from LeaveRequest l "
            + "where l.employee.id = :employeeId "
            + "and l.status in :statuses "
            + "and l.startDate <= :endDate "
            + "and l.endDate >= :startDate "
            + "and l.id <> :excludeId")
    long countOverlapping(@Param("employeeId") Integer employeeId,
                          @Param("statuses") Collection<Status> statuses,
                          @Param("startDate") LocalDate startDate,
                          @Param("endDate") LocalDate endDate,
                          @Param("excludeId") Integer excludeId);
}
