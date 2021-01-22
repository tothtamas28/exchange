package exchange.model

import javax.persistence.*

@Entity
data class Balance(
    @OneToOne
    val owner : User,
    var btc : Long,
    var usd : Long
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id = 0L

    fun amount(type : OrderType) : Long = when (type) {
        OrderType.BUY -> usd
        OrderType.SELL -> btc
    }
}