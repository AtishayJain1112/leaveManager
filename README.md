# Employee Leave Management System

A REST API for managing employee leave requests, built with **Spring Boot 3, Spring Data JPA, Spring Security and MySQL**.

Employees apply for leave. Managers approve or reject it. Leave days are deducted from the employee's balance when a request is approved.

This submission is a **backend API only** (there is no frontend). It was built to the assignment's primary goals plus both bonus goals.

## What is implemented

| Requirement | Where |
|---|---|
| `Employee` and `LeaveRequest` entities with a one-to-many relationship | `Model/Employee.java`, `Model/LeaveRequest.java` |
| CRUD for employees and leave requests | `Controller/empController.java`, `Controller/LeaveRequestController.java` |
| Validation with meaningful error responses | `DAO/Request*.java` (field rules), `Service/LeaveRequestService.java` (business rules), `Exceptions/GlobalExceptionHandler.java` |
| Spring Data JPA repositories + `schema.sql` | `Repository/`, `src/main/resources/schema.sql` |
| Unit tests for the service layer (JUnit 5 + Mockito) | `src/test/java/.../Service/` |
| **Bonus:** role-based access control (EMPLOYEE vs MANAGER) | `Security/`, `@PreAuthorize` in the controllers |
| **Bonus:** pagination and sorting on the leave request list | `GET /leave-requests` |

## Requirements

- Java 17 or newer
- Maven 3.8+ (or use your IDE's Maven support)
- MySQL 8.0.16+ running locally

## Setup and run

1. **Start MySQL.** Nothing needs to be created by hand: the app creates the `leave_manager` database and the tables on startup.
2. **Check the database credentials** in `src/main/resources/application.properties`. The defaults are user `root`, password `root`, host `localhost:3306`. Override them without editing the file by setting environment variables:

   ```bash
   export DB_USERNAME=myuser
   export DB_PASSWORD=mypassword
   # optional, to use a different host/port/database:
   # export DB_URL="jdbc:mysql://localhost:3306/leave_manager?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
   ```

3. **Run the app:**

   ```bash
   mvn spring-boot:run
   ```

   The API is now at `http://localhost:8080`.

4. **Run the tests** (no database needed, the service tests use mocks):

   ```bash
   mvn test
   ```

### Database schema

The schema is in `src/main/resources/schema.sql`. It is executed automatically on startup and uses `CREATE TABLE IF NOT EXISTS`, so it is safe to run repeatedly. To create it manually instead:

```bash
mysql -u root -p < src/main/resources/schema.sql
```

> **Note on the database:** the assignment's objective says MySQL but its submission section mentions PostgreSQL. I used **MySQL**, as stated in the objective.

### Demo logins

On the first start with an empty database, two users are created so you can log in straight away (turn this off with `app.seed.enabled=false`):

| Role | Email | Password |
|---|---|---|
| MANAGER | `manager@company.com` | `manager123` |
| EMPLOYEE | `john@company.com` | `employee123` |

Authentication is **HTTP Basic**: send the email as the username and the password. Passwords are stored as BCrypt hashes.

## API

All endpoints require a login. Dates are written `yyyy-MM-dd`.

### Employees

| Method | Path | Who | Description |
|---|---|---|---|
| GET | `/employees` | Manager | List all employees |
| GET | `/employees/{id}` | Manager | Get one employee |
| GET | `/employees/me` | Any user | Get my own record (including my leave balance) |
| POST | `/employees` | Manager | Create an employee (`201 Created`) |
| PUT | `/employees/{id}` | Manager | Update an employee (blank/omitted password keeps the old one) |
| DELETE | `/employees/{id}` | Manager | Delete an employee and their leave requests (`204`) |

### Leave requests

| Method | Path | Who | Description |
|---|---|---|---|
| POST | `/leave-requests` | Any user | Apply for leave for myself (`201 Created`, status `PENDING`) |
| GET | `/leave-requests` | Any user | Paginated list. Employees see only their own, managers see everyone's |
| GET | `/leave-requests/{id}` | Owner or manager | Get one request |
| PUT | `/leave-requests/{id}` | Owner | Edit dates/reason while still `PENDING` |
| DELETE | `/leave-requests/{id}` | Owner or manager | Owner can cancel a `PENDING` request. A manager can delete any (an approved request gives the days back) |
| PATCH | `/leave-requests/{id}/approve` | Manager | Approve a `PENDING` request and deduct the days |
| PATCH | `/leave-requests/{id}/reject` | Manager | Reject a `PENDING` request |

**Pagination and sorting** on `GET /leave-requests`:

- `page` (starts at 0), `size` (default 10)
- `sort=field,direction`, e.g. `sort=startDate,desc`. Allowed fields: `id`, `startDate`, `endDate`, `status`. The default is `startDate,desc`.
- Optional filters: `status=PENDING|APPROVED|REJECTED`, and (managers only) `employeeId=3`

Response shape: `{ "content": [...], "page": 0, "size": 10, "totalElements": 25, "totalPages": 3 }`

### Example requests

```bash
# Employee applies for leave
curl -u john@company.com:employee123 -X POST http://localhost:8080/leave-requests \
  -H "Content-Type: application/json" \
  -d '{"startDate":"2026-12-22","endDate":"2026-12-24","reason":"Family trip"}'

# Employee views own requests, newest start date first
curl -u john@company.com:employee123 "http://localhost:8080/leave-requests?page=0&size=5&sort=startDate,desc"

# Manager approves request 1
curl -u manager@company.com:manager123 -X PATCH http://localhost:8080/leave-requests/1/approve

# Manager creates an employee
curl -u manager@company.com:manager123 -X POST http://localhost:8080/employees \
  -H "Content-Type: application/json" \
  -d '{"name":"Asha Rao","email":"asha@company.com","password":"secret123","role":"EMPLOYEE","leaveBalance":18}'
```

## Business rules

- A leave request counts **calendar days, inclusive** of the start and end day (22 to 24 Dec = 3 days). Weekends and holidays are not excluded.
- Validation when creating or editing a request:
  - end date cannot be before the start date
  - start date cannot be in the past
  - the number of days cannot exceed the employee's leave balance
  - dates cannot overlap another `PENDING` or `APPROVED` request of the same employee
- The balance is **deducted only on approval** (and re-checked then). Rejecting does not change it.
- Only `PENDING` requests can be edited, approved, rejected or cancelled by an employee.
- Emails are unique and stored in lowercase. New employees default to role `EMPLOYEE` and a balance of 20 days.

## Error responses

Every error has the same JSON shape:

```json
{
  "timestamp": "2026-10-15T23:30:00.123",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient leave balance: requested 5 day(s) but only 2 available",
  "path": "/leave-requests"
}
```

Field validation errors add a `fieldErrors` map (`{"email": "Email must be a valid email address"}`).

| Status | Meaning |
|---|---|
| 400 | Invalid input or a broken business rule |
| 401 | Missing or wrong login |
| 403 | Logged in, but not allowed (e.g. an employee calling a manager endpoint) |
| 404 | Employee or leave request not found |
| 409 | Conflict with current state (duplicate email, overlapping dates, request not pending) |

## Project structure

```
src/main/java/leaveManager/in/leaveManager
├── Controller/   REST endpoints (empController, LeaveRequestController)
├── Service/      business logic and validation
├── Repository/   Spring Data JPA repositories
├── Model/        JPA entities and enums (Employee, LeaveRequest, Role, Status)
├── DAO/          request/response objects (what goes in and out of the API)
├── Exceptions/   custom exceptions and the global error handler
├── Security/     login (HTTP Basic against the employees table) and security config
└── Config/       demo data seeder
```
