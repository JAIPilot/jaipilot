Implement an upcoming-visits projection in Spring Framework Petclinic.

Add this exact Java 17 record in the service package:

```java
public record UpcomingVisit(int petId, String petName, LocalDate date, String description) {}
```

Add this exact method to `ClinicService` and `ClinicServiceImpl`:

```java
List<UpcomingVisit> findUpcomingVisits(int ownerId, LocalDate fromInclusive, int limit)
```

Required behavior:

- Reject a null `fromInclusive`, a limit outside 1 through 50, and a missing owner with
  `IllegalArgumentException`.
- Aggregate visits from all of the owner's pets and include only visits with a non-null date on or
  after `fromInclusive`.
- Sort by date ascending, then pet name case-insensitively, then description case-insensitively.
- Apply `limit` after sorting and return an unmodifiable result.
- Do not mutate the owner, pets, visits, or repository results.

Add focused tests covering filtering, tie ordering, limits, immutability, and invalid input. Preserve
existing behavior and API, do not add dependencies or change build configuration, and keep the
implementation minimal. Use Java 17. Do not commit or push. Use the tools available to you when they
materially help.
