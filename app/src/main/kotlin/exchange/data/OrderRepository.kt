package exchange.data

import exchange.model.Order
import exchange.model.OrderType
import exchange.model.User
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface OrderRepository : CrudRepository<Order, Long> {

    fun findByIssuerAndId(issuer : User, id : Long) : Order?

    @Query(value = "SELECT SUM(o.quantity * o.limitPrice) FROM Order o " +
            "WHERE o.state = 0 AND o.type = 0 AND o.issuer = :issuer")
    fun usdInOrders(issuer : User) : Long?

    @Query(value = "SELECT SUM(o.quantity) FROM Order o " +
            "WHERE o.state = 0 AND o.type = 1 AND o.issuer = :issuer")
    fun btcInOrders(issuer : User) : Long?

    @Query(value = "SELECT o FROM Order o " +
            "WHERE o.state = 0 AND o.type = 0 " +
            "ORDER BY o.limitPrice DESC")
    fun findBuyers() : List<Order>

    @Query(value = "SELECT o FROM Order o " +
            "WHERE o.state = 0 AND o.type = 1 " +
            "ORDER BY o.limitPrice ASC")
    fun findSellers() : List<Order>

    @Query(value = "SELECT o FROM Order o " +
                "WHERE o.state = 0 AND o.type = 0 AND o.limitPrice <= :price " +
                "ORDER BY o.limitPrice DESC")
    fun findBuyersAtPrice(price : Long) : List<Order>

    @Query(value = "SELECT o from Order o " +
            "WHERE o.state = 0 AND o.type = 1 AND o.limitPrice <= :price " +
            "ORDER BY o.limitPrice ASC")
    fun findSellersAtPrice(price : Long) : List<Order>
}