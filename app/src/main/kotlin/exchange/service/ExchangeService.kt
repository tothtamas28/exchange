package exchange.service

import exchange.data.BalanceRepository
import exchange.data.OrderRepository
import exchange.data.UserRepository
import exchange.model.*
import exchange.model.Currency
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URL
import javax.transaction.Transactional
import kotlin.math.min

@Service
class ExchangeService(
    val userRepository: UserRepository,
    val balanceRepository: BalanceRepository,
    val orderRepository: OrderRepository,
    @Value("\${cmc-api-key}")
    val apiKey: String
) {

    @Transactional
    fun ensureUserWithBalanceExists(username: String) : User = userRepository.findUserByUsername(username) ?: let {
        val user = userRepository.save(User(username))
        val balance = Balance(user, 0L, 0L)
        balanceRepository.save(balance)
        user
    }

    @Transactional
    fun deposit(user : User, amount : Long, currency : Currency) {
        val balance = balanceRepository.findBalanceByOwner(user)
            ?: throw RuntimeException("Balance for user ${user.username} not found")
        when (currency) {
            Currency.BTC -> balance.btc += amount
            Currency.USD -> balance.usd += amount
        }
    }

    @Transactional
    fun getBalanceOfUser(user : User) = balanceRepository.findBalanceByOwner(user)

    private data class SaleResult(val type : OrderType, val btc: Long, val usd: Long)

    private fun executeSale(quantity : Long, type : OrderType, balance : Balance, order : Order) : SaleResult {
        val orderBalance = balanceRepository.findBalanceByOwner(order.issuer)
            ?: throw RuntimeException("Balance for user ${order.issuer.username} not found")

        assert(type != OrderType.BUY || order.type != OrderType.BUY)
        assert(type != OrderType.SELL || order.type != OrderType.SELL)
        assert(order.type != OrderType.BUY || orderBalance.usd >= order.quantity * order.limitPrice)
        assert(order.type != OrderType.SELL || orderBalance.btc >= order.quantity)

        val price = order.limitPrice
        val affordedQuantity = when (type) {
            OrderType.BUY -> min(quantity,balance.usd / price)
            OrderType.SELL -> min(quantity, balance.btc)
        }
        val btc = min(affordedQuantity, order.quantity)
        val usd = btc * price
        val buyerBalance = when (type) {
            OrderType.BUY -> balance
            OrderType.SELL -> orderBalance
        }
        val sellerBalance = when (type) {
            OrderType.BUY -> orderBalance
            OrderType.SELL -> balance
        }

        sellerBalance.btc -= btc
        buyerBalance.btc += btc
        sellerBalance.usd += usd
        buyerBalance.usd -= usd

        order.filledQuantity += btc
        order.quantity -= btc
        order.filledPrice += usd
        order.state = if (order.quantity == 0L) { OrderState.FULFILLED } else { order.state }

        return SaleResult(order.type, btc, usd)
    }

    data class OrderResult(val type: OrderType, val btc: Long, val usd: Long, val updatedOrders: List<Order>)

    private fun executeOrders(quantity : Long, type : OrderType, balance : Balance, orders : List<Order>) : OrderResult {
        var btc = 0L
        var usd = 0L
        val updatedOrders = mutableListOf<Order>()
        for (order in orders) {
            val saleResult = executeSale(quantity - btc, type, balance, order)
            if (saleResult.btc == 0L) {
                assert(saleResult.usd == 0L)
                break
            }
            btc += saleResult.btc
            usd += saleResult.usd
            updatedOrders.add(order)
        }
        return OrderResult(type, btc, usd, updatedOrders)
    }

    @Transactional
    fun executeMarketOrder(user: User, quantity: Long, type: OrderType) : OrderResult? {
        val balance = balanceRepository.findBalanceByOwner(user)
            ?: throw RuntimeException("Balance for user ${user.username} not found")
        val orders = when (type) {
            OrderType.BUY -> orderRepository.findSellers()
            OrderType.SELL -> orderRepository.findBuyers()
        }
        return try {
            executeOrders(quantity, type, balance, orders)
        } catch (e : Exception) {
            // Assertion failure -- reaching this branch indicates a bug
            null
        }
    }

    data class StandingOrderResult(val order : Order, val updatedOrders: List<Order>)

    @Transactional
    fun createStandingOrder(user: User, quantity: Long, type: OrderType, limitPrice: Long, webhookURL: URL): StandingOrderResult? {
        val balance = balanceRepository.findBalanceByOwner(user)
            ?: throw RuntimeException("Balance for user ${user.username} not found")

        val cancel = if (type == OrderType.BUY) {
            val reserved = orderRepository.usdInOrders(user) ?: 0L
            balance.usd < reserved + quantity * limitPrice
        } else {
            val reserved = orderRepository.btcInOrders(user) ?: 0L
            balance.btc < reserved + quantity
        }

        if (cancel) {
            val order = Order(user, type, limitPrice, 0, quantity, 0, OrderState.CANCELLED, webhookURL)
            return StandingOrderResult(orderRepository.save(order), emptyList())
        }

        val orders = when (type) {
            OrderType.BUY -> orderRepository.findSellersAtPrice(limitPrice)
            OrderType.SELL -> orderRepository.findBuyersAtPrice(limitPrice)
        }

        return try {
            val orderResult = executeOrders(quantity, type, balance, orders)
            val btc = orderResult.btc
            val usd = orderResult.usd
            val updatedOrders = orderResult.updatedOrders
            val state = if (btc == quantity) {
                OrderState.FULFILLED
            } else {
                OrderState.LIVE
            }
            val order = Order(user, type, limitPrice, btc, quantity - btc, usd, state, webhookURL)
            StandingOrderResult(orderRepository.save(order), updatedOrders)
        } catch (e : Exception) {
            // Assertion failure -- reaching this branch indicates a bug
            null
        }
    }

    @Transactional
    fun findOrderOfUserById(user : User, id : Long) : Order? = orderRepository.findByIssuerAndId(user, id)

    @Transactional
    fun removeOrderOfUserById(user : User, id : Long) : Boolean {
        val order = orderRepository.findByIssuerAndId(user, id)
        return if (order == null) {
            false
        } else {
            orderRepository.deleteById(order.id)
            true
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getCMCExchangeRate() : Double {
        val restTemplate = RestTemplate()
        val url = "https://pro-api.coinmarketcap.com/v1/cryptocurrency/quotes/latest?symbol={symbol}"
        val headers = HttpHeaders()
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers.set("X-CMC_PRO_API_KEY", apiKey)
        val request = HttpEntity<Any>(headers)
        // TODO would be nice tor replace Map by some generic parsable JSON representation...
        val response = restTemplate.exchange(url, HttpMethod.GET, request, Map::class.java, "BTC")
        return if (response.statusCode == HttpStatus.OK) {
            try {
                val body = response.body!!
                val data = body["data"] as Map<String, Any>
                val btc = data["BTC"] as Map<String, Any>
                val quote = btc["quote"] as Map<String, Any>
                val usd = quote["USD"] as Map<String, Any>
                usd["price"] as Double
            } catch (e: Exception) {
                .0
            }
        } else {
            .0
        }
    }
}