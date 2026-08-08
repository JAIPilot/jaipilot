package org.springframework.samples.petclinic.service.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.repository.OwnerRepository;
import org.springframework.samples.petclinic.repository.PetRepository;
import org.springframework.samples.petclinic.repository.VetRepository;
import org.springframework.samples.petclinic.repository.VisitRepository;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.samples.petclinic.service.ClinicServiceImpl;
import org.springframework.samples.petclinic.web.VisitController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VisitSchedulingAcceptanceTests {

    private PetRepository petRepository;
    private VisitRepository visitRepository;
    private ClinicService service;

    @BeforeEach
    void setUp() {
        this.petRepository = mock(PetRepository.class);
        this.visitRepository = mock(VisitRepository.class);
        this.service = new ClinicServiceImpl(
            this.petRepository,
            mock(VetRepository.class),
            mock(OwnerRepository.class),
            this.visitRepository
        );
    }

    @Test
    void schedulesAndNormalizesAVisitAlreadyAttachedByMvc() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        Pet pet = pet(7);
        Visit visit = visit(today, "  annual checkup  ");
        pet.addVisit(visit);
        when(this.petRepository.findById(7)).thenReturn(pet);

        this.service.scheduleVisit(7, visit, today);

        assertThat(visit.getDescription()).isEqualTo("annual checkup");
        assertThat(visit.getPet()).isSameAs(pet);
        assertThat(pet.getVisits()).containsExactly(visit);
        verify(this.visitRepository).save(visit);
    }

    @Test
    void attachesAndSavesANewValidVisitExactlyOnce() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        Pet pet = pet(9);
        Visit visit = visit(today.plusDays(1), "vaccination");
        when(this.petRepository.findById(9)).thenReturn(pet);

        this.service.scheduleVisit(9, visit, today);

        assertThat(visit.getPet()).isSameAs(pet);
        assertThat(pet.getVisits()).containsExactly(visit);
        verify(this.visitRepository).save(visit);
    }

    @Test
    void rejectsAnotherVisitOnTheSameDateWithoutSaving() {
        LocalDate date = LocalDate.of(2026, 8, 10);
        Pet pet = pet(11);
        pet.addVisit(visit(date, "first"));
        Visit candidate = visit(date, "second");
        pet.addVisit(candidate);
        when(this.petRepository.findById(11)).thenReturn(pet);

        assertThatIllegalStateException()
            .isThrownBy(() -> this.service.scheduleVisit(11, candidate, date.minusDays(1)));

        verify(this.visitRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidInputsWithoutSaving() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        Pet pet = pet(13);
        when(this.petRepository.findById(13)).thenReturn(pet);
        when(this.petRepository.findById(99)).thenReturn(null);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> this.service.scheduleVisit(13, null, today));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> this.service.scheduleVisit(13, visit(today, "valid"), null));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> this.service.scheduleVisit(99, visit(today, "valid"), today));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> this.service.scheduleVisit(13, visit(null, "valid"), today));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> this.service.scheduleVisit(13, visit(today.minusDays(1), "valid"), today));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> this.service.scheduleVisit(13, visit(today, "  "), today));

        verify(this.visitRepository, never()).save(any());
    }

    @Test
    void mvcPostDelegatesToSchedulingInsteadOfDirectSave() throws Exception {
        ClinicService clinicService = mock(ClinicService.class);
        Pet pet = pet(17);
        when(clinicService.findPetById(17)).thenReturn(pet);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new VisitController(clinicService)).build();
        String date = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        mvc.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", 4, 17)
                .param("date", date)
                .param("description", "follow-up"))
            .andExpect(status().is3xxRedirection())
            .andExpect(view().name("redirect:/owners/{ownerId}"));

        verify(clinicService).scheduleVisit(eq(17), any(Visit.class), any(LocalDate.class));
        verify(clinicService, never()).saveVisit(any());
    }

    private static Pet pet(int id) {
        Pet pet = new Pet();
        pet.setId(id);
        pet.setName("Milo");
        return pet;
    }

    private static Visit visit(LocalDate date, String description) {
        Visit visit = new Visit();
        visit.setDate(date);
        visit.setDescription(description);
        return visit;
    }
}
