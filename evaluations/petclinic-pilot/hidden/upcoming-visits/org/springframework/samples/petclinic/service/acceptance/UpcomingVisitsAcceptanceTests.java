package org.springframework.samples.petclinic.service.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

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
import org.springframework.samples.petclinic.service.UpcomingVisit;

class UpcomingVisitsAcceptanceTests {

    private OwnerRepository ownerRepository;
    private ClinicService service;

    @BeforeEach
    void setUp() {
        this.ownerRepository = mock(OwnerRepository.class);
        this.service = new ClinicServiceImpl(
            mock(PetRepository.class),
            mock(VetRepository.class),
            this.ownerRepository,
            mock(VisitRepository.class)
        );
    }

    @Test
    void filtersSortsAndProjectsAcrossAllPets() {
        LocalDate from = LocalDate.of(2026, 8, 8);
        Owner owner = new Owner();
        owner.setId(3);
        Pet zed = pet(30, "zed");
        Pet alpha = pet(10, "Alpha");
        alpha.addVisit(visit(from.minusDays(1), "past"));
        zed.addVisit(visit(from.plusDays(2), "Beta"));
        alpha.addVisit(visit(from.plusDays(2), "gamma"));
        alpha.addVisit(visit(from, "first"));
        zed.addVisit(visit(null, "undated"));
        owner.addPet(zed);
        owner.addPet(alpha);
        when(this.ownerRepository.findById(3)).thenReturn(owner);

        List<UpcomingVisit> visits = this.service.findUpcomingVisits(3, from, 50);

        assertThat(visits).extracting(UpcomingVisit::petId).containsExactly(10, 10, 30);
        assertThat(visits).extracting(UpcomingVisit::description)
            .containsExactly("first", "gamma", "Beta");
        assertThat(visits).isUnmodifiable();
        assertThat(owner.getPets()).containsExactly(alpha, zed);
    }

    @Test
    void appliesLimitOnlyAfterDeterministicTieOrdering() {
        LocalDate date = LocalDate.of(2026, 8, 8);
        Owner owner = new Owner();
        owner.setId(4);
        Pet pet = pet(40, "Milo");
        pet.addVisit(visit(date, "Zulu"));
        pet.addVisit(visit(date, "alpha"));
        owner.addPet(pet);
        when(this.ownerRepository.findById(4)).thenReturn(owner);

        List<UpcomingVisit> visits = this.service.findUpcomingVisits(4, date, 1);

        assertThat(visits).singleElement().extracting(UpcomingVisit::description).isEqualTo("alpha");
    }

    @Test
    void rejectsInvalidInputAndMissingOwner() {
        LocalDate from = LocalDate.of(2026, 8, 8);
        when(this.ownerRepository.findById(99)).thenReturn(null);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> this.service.findUpcomingVisits(1, null, 1));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> this.service.findUpcomingVisits(1, from, 0));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> this.service.findUpcomingVisits(1, from, 51));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> this.service.findUpcomingVisits(99, from, 1));
    }

    private static Pet pet(int id, String name) {
        Pet pet = new Pet();
        pet.setId(id);
        pet.setName(name);
        return pet;
    }

    private static Visit visit(LocalDate date, String description) {
        Visit visit = new Visit();
        visit.setDate(date);
        visit.setDescription(description);
        return visit;
    }
}
