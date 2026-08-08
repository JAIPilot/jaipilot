Implement a production-ready pet transfer use case in Spring Framework Petclinic.

Add this exact method to `ClinicService` and `ClinicServiceImpl`:

```java
void transferPet(int petId, int targetOwnerId)
```

Required behavior:

- Reject a missing pet, a pet without a current owner, a missing target owner, and transfer to the
  current owner with `IllegalArgumentException`.
- Reject transfer when the target already owns a different pet with the same name, ignoring case,
  with `IllegalStateException`.
- On success remove the pet from the source owner, add the same pet instance to the target owner,
  update the pet's owner link, and preserve every existing visit.
- Persist the moved pet exactly once through the existing `PetRepository`.
- A rejected transfer must not mutate either owner and must not save anything.

Add the smallest cohesive domain operation needed to keep both sides of the owner/pet relationship
consistent. Add focused tests for success and every rejection. Preserve existing behavior and API,
do not add dependencies or change build configuration, and keep the implementation minimal. Use
Java 17. Do not commit or push. Use the tools available to you when they materially help.
