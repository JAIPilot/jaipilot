package org.springframework.samples.petclinic.service.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.repository.OwnerRepository;
import org.springframework.samples.petclinic.repository.PetRepository;
import org.springframework.samples.petclinic.repository.VetRepository;
import org.springframework.samples.petclinic.repository.VisitRepository;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.samples.petclinic.service.ClinicServiceImpl;

class PetTransferAcceptanceTests {

    private PetRepository petRepository;
    private OwnerRepository ownerRepository;
    private ClinicService service;

    @BeforeEach
    void setUp() {
        this.petRepository = mock(PetRepository.class);
        this.ownerRepository = mock(OwnerRepository.class);
        this.service = new ClinicServiceImpl(
            this.petRepository,
            mock(VetRepository.class),
            this.ownerRepository,
            mock(VisitRepository.class)
        );
    }

    @Test
    void transfersTheSamePetAndPreservesVisits() {
        Owner source = owner(1);
        Owner target = owner(2);
        Pet pet = pet(10, "Milo");
        Visit visit = new Visit();
        visit.setDate(LocalDate.of(2026, 8, 8));
        visit.setDescription("checkup");
        pet.addVisit(visit);
        source.addPet(pet);
        when(this.petRepository.findById(10)).thenReturn(pet);
        when(this.ownerRepository.findById(2)).thenReturn(target);

        this.service.transferPet(10, 2);

        assertThat(source.getPets()).doesNotContain(pet);
        assertThat(target.getPets()).containsExactly(pet);
        assertThat(pet.getOwner()).isSameAs(target);
        assertThat(pet.getVisits()).containsExactly(visit);
        verify(this.petRepository).save(pet);
    }

    @Test
    void rejectsDuplicateNameWithoutMutatingEitherOwner() {
        Owner source = owner(1);
        Owner target = owner(2);
        Pet pet = pet(10, "Milo");
        Pet duplicate = pet(20, "mILO");
        source.addPet(pet);
        target.addPet(duplicate);
        when(this.petRepository.findById(10)).thenReturn(pet);
        when(this.ownerRepository.findById(2)).thenReturn(target);

        assertThatIllegalStateException().isThrownBy(() -> this.service.transferPet(10, 2));

        assertThat(source.getPets()).containsExactly(pet);
        assertThat(target.getPets()).containsExactly(duplicate);
        assertThat(pet.getOwner()).isSameAs(source);
        verify(this.petRepository, never()).save(any());
    }

    @Test
    void rejectsMissingAndInvalidRelationshipsWithoutSaving() {
        Owner source = owner(1);
        Pet pet = pet(10, "Milo");
        source.addPet(pet);
        Pet orphan = pet(11, "Solo");
        when(this.petRepository.findById(10)).thenReturn(pet);
        when(this.petRepository.findById(11)).thenReturn(orphan);
        when(this.petRepository.findById(99)).thenReturn(null);
        when(this.ownerRepository.findById(1)).thenReturn(source);
        when(this.ownerRepository.findById(2)).thenReturn(null);

        assertThatIllegalArgumentException().isThrownBy(() -> this.service.transferPet(99, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> this.service.transferPet(11, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> this.service.transferPet(10, 2));
        assertThatIllegalArgumentException().isThrownBy(() -> this.service.transferPet(10, 1));

        assertThat(source.getPets()).containsExactly(pet);
        assertThat(pet.getOwner()).isSameAs(source);
        verify(this.petRepository, never()).save(any());
    }

    private static Owner owner(int id) {
        Owner owner = new Owner();
        owner.setId(id);
        owner.setFirstName("Owner" + id);
        owner.setLastName("Test");
        return owner;
    }

    private static Pet pet(int id, String name) {
        Pet pet = new Pet();
        pet.setId(id);
        pet.setName(name);
        return pet;
    }
}
