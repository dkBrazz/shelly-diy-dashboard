package org.dkbrazz.model.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "devices")
class Device(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(name = "external_id", unique = true, nullable = false)
    var externalId: String,

    var type: String? = null,
    var code: String? = null,
    var name: String? = null,
    var gen: String? = null,

    @Column(name = "created_at")
    var createdAt: OffsetDateTime = OffsetDateTime.now()
)
