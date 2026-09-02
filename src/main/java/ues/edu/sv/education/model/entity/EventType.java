package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "event_types")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_type_id")
    private Integer eventTypeId;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "description", length = 255)
    private String description;

    public EventType(int id) {
        this.eventTypeId=id;
    }
}