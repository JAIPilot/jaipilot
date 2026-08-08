Implement a production-ready visit scheduling use case in Spring Framework Petclinic.

Add this exact method to `ClinicService` and `ClinicServiceImpl`:

```java
void scheduleVisit(int petId, Visit visit, LocalDate today)
```

Required behavior:

- Reject a null visit, null `today`, missing pet, null visit date, a date before `today`, and a blank
  description with `IllegalArgumentException`.
- Trim and retain a valid description.
- Reject a different existing visit for the same pet on the same date with
  `IllegalStateException`.
- The MVC model-attribute flow already attaches the submitted visit to the pet. Do not treat that
  same object as a scheduling conflict.
- Attach a not-yet-attached visit to the pet and persist it exactly once through `VisitRepository`.
- Update the visit POST flow to call `scheduleVisit` with its path `petId` instead of calling
  `saveVisit` directly. A rejected rule must return the existing form with a binding error.

Add focused tests for normal, boundary, and rejection behavior. Preserve existing behavior and API,
do not add dependencies or change build configuration, and keep the implementation minimal. Use
Java 17. Do not commit or push. Use the tools available to you when they materially help.
