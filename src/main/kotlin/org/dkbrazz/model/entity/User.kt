package org.dkbrazz.model.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(unique = true, nullable = false)
    var email: String,

    @Column(name = "created_at")
    var createdAt: OffsetDateTime = OffsetDateTime.now()
)
