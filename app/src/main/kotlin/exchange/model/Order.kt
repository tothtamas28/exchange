package exchange.model

import java.net.URL
import javax.persistence.*

@Entity
@Table(name = "orders")
data class Order(
    @ManyToOne
    val issuer: User,
    val type : OrderType,
    val limitPrice : Long,
    var filledQuantity : Long,
    var quantity : Long,
    var filledPrice : Long,
    var state : OrderState,
    var webhookURL: URL
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id = 0L
}