# Smart Parking Management REST API

Spring Boot REST API for parking-slot availability, vehicle entry, live fare, checkout, payment status, and daily revenue reporting.

## Requirements

- JDK 21 (recommended; the project targets Java 21)
- PostgreSQL 14 or newer
- Internet access on the first Maven Wrapper run so it can download Maven and dependencies

## Database setup

Create an empty PostgreSQL database named `parkingdb`, then run these commands from the project root (adjust the PostgreSQL username if needed):

```bash
psql -U postgres -d parkingdb -f sql/schema.sql
psql -U postgres -d parkingdb -f sql/seed.sql
```

`schema.sql` drops and recreates the project's two tables, so do not run it against a database containing data you need to keep.

Configure credentials with environment variables before starting the application (`DB_PASSWORD` has no default, so it must be set unless your local Postgres user has no password). Example for Windows Command Prompt:

```cmd
set DB_URL=jdbc:postgresql://localhost:5432/parkingdb
set DB_USERNAME=postgres
set DB_PASSWORD=your_postgres_password
```

Do not commit real database passwords.

## Build and test

Windows Command Prompt:

```cmd
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
chmod +x mvnw   # only needed if the zip extraction dropped the execute bit
./mvnw clean test
./mvnw spring-boot:run
```

(If `chmod` is not an option, `sh mvnw clean test` works too.)

The application uses port `8080` and context path `/parking`. Base URL: `http://localhost:8080/parking`.

## Main API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | Health check |
| GET | `/slots` | List all slots |
| GET | `/slots/available` | List available slots |
| GET | `/slots/{id}` | Get one slot |
| POST | `/bookings/entry` | Book a slot for a vehicle |
| GET | `/bookings/active` | List active bookings |
| GET | `/bookings/{id}/fare` | Calculate live/final fare |
| POST | `/bookings/{id}/checkout` | Check out and calculate bill |
| POST | `/bookings/{id}/pay` | Mark completed booking as paid |
| GET | `/reports/revenue?date=2026-10-30` | Daily paid revenue |

Fee rule: ₹50 per started 3-hour block. For example, 2 hours 15 minutes costs ₹50; 3 hours 5 minutes costs ₹100. Exactly 3 hours costs ₹50.

Revenue rule: `/reports/revenue` sums the fees of bookings that are checked out **and PAID**, grouped by the calendar day of checkout (`[00:00, next day 00:00)`). Checked-out bookings still marked `PENDING` are not counted until `/bookings/{id}/pay` is called.

## API verification

Import `parking.postman_collection.json` into Postman. Start the application and PostgreSQL first. The collection's base URL is `http://localhost:8080/parking`; its `slotId` and `bookingId` variables default to `1`, so update them if your database IDs differ. Run the entry request before live-fare, checkout, and payment requests.

## Submission notes

This project includes SQL scripts, unit tests, and a Postman collection. Run the build and exercise the API against a configured PostgreSQL database before treating the application as verified.
