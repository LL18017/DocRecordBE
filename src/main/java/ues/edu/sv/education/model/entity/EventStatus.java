package ues.edu.sv.education.model.entity;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "event_status")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "event_status_id")
    private Integer eventStatusId;

    @NotBlank(message = "El nombre no puede estar vacío")
    @NotNull(message = "El nombre es una propiedad obligatoria")
    @Column(name = "name", nullable = false)
    @Size(max = 100)
    private String name;

    @OneToMany(mappedBy = "eventStatus")
    private List<Event> events;

    public EventStatus(int id) {
    }
}
