package org.springframework.samples.petclinic.service.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.model.Specialty;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.repository.OwnerRepository;
import org.springframework.samples.petclinic.repository.PetRepository;
import org.springframework.samples.petclinic.repository.VetRepository;
import org.springframework.samples.petclinic.repository.VisitRepository;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.samples.petclinic.service.ClinicServiceImpl;

class VetSpecialtyAcceptanceTests {

    private VetRepository vetRepository;
    private ClinicService service;

    @BeforeEach
    void setUp() {
        this.vetRepository = mock(VetRepository.class);
        this.service = new ClinicServiceImpl(
            mock(PetRepository.class),
            this.vetRepository,
            mock(OwnerRepository.class),
            mock(VisitRepository.class)
        );
    }

    @Test
    void normalizesFiltersDeduplicatesAndSorts() {
        Vet zed = vet(3, "Zed", "Able", "surgery");
        Vet alphaB = vet(2, "alpha", "Baker", "SURGERY");
        Vet alphaA = vet(1, "Alpha", "Able", "surgery");
        Vet duplicateAlphaA = vet(1, "Alpha", "Able", "surgery");
        Vet dentist = vet(4, "Dental", "Only", "dentistry");
        when(this.vetRepository.findAll())
            .thenReturn(List.of(zed, duplicateAlphaA, dentist, alphaB, alphaA));

        List<Vet> selected = this.service.findVetsBySpecialty("  SuRgErY ", 10);

        assertThat(selected).extracting(Vet::getId).containsExactly(1, 3, 2);
        assertThat(selected).isUnmodifiable();
    }

    @Test
    void appliesLimitAfterSorting() {
        Vet later = vet(2, "Zulu", "Vet", "radiology");
        Vet first = vet(1, "Alpha", "Vet", "radiology");
        when(this.vetRepository.findAll()).thenReturn(List.of(later, first));

        assertThat(this.service.findVetsBySpecialty("radiology", 1)).containsExactly(first);
    }

    @Test
    void rejectsInvalidInput() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> this.service.findVetsBySpecialty(null, 1));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> this.service.findVetsBySpecialty("  ", 1));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> this.service.findVetsBySpecialty("surgery", 0));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> this.service.findVetsBySpecialty("surgery", 51));
    }

    private static Vet vet(int id, String firstName, String lastName, String specialtyName) {
        Specialty specialty = new Specialty();
        specialty.setName(specialtyName);
        Vet vet = new Vet();
        vet.setId(id);
        vet.setFirstName(firstName);
        vet.setLastName(lastName);
        vet.addSpecialty(specialty);
        return vet;
    }
}
