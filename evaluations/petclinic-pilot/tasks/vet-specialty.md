Implement specialty-based veterinarian selection in Spring Framework Petclinic.

Add this exact method to `ClinicService` and `ClinicServiceImpl`:

```java
List<Vet> findVetsBySpecialty(String specialtyName, int limit)
```

Required behavior:

- Reject a null or blank specialty and a limit outside 1 through 50 with
  `IllegalArgumentException`.
- Trim the requested specialty and match specialty names exactly, ignoring case.
- Deduplicate repeated repository entries for the same non-null vet ID.
- Sort matches by last name, then first name, case-insensitively, then by ID.
- Apply `limit` after sorting and return an unmodifiable result.
- Do not mutate vets, specialties, or the repository collection.

Add focused tests covering normalization, filtering, deterministic ordering, deduplication, limits,
immutability, and invalid input. Preserve existing behavior and API, do not add dependencies or
change build configuration, and keep the implementation minimal. Use Java 17. Do not commit or push.
Use the tools available to you when they materially help.
