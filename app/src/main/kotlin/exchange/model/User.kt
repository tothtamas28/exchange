package exchange.model

import javax.persistence.*

@Entity
@Table(name = "users")
data class User(
    @Column(unique = true)
    var username : String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id = 0L
}