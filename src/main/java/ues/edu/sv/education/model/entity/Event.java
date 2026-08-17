package ues.edu.sv.education.model.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "event_id")
    private Integer eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "event_type_id",
            nullable = false
    )
    private EventType eventType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_email", length = 500)
    private String userEmail;

    @Column(name = "ref")
    private String ref;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_status_id", referencedColumnName = "event_status_id", nullable = false)
    private EventStatus eventStatus;
}