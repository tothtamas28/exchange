package exchange.service

import exchange.data.BalanceRepository
import exchange.data.OrderRepository
import exchange.data.UserRepository
import exchange.model.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import java.net.URL

@SpringBootTest
@ActiveProfiles("test")
class ExchangeServiceTests {

    @Autowired
    private lateinit var exchangeService: ExchangeService

    @MockBean
    private lateinit var userRepository : UserRepository

    @MockBean
    private lateinit var balanceRepository : BalanceRepository

    @MockBean
    private lateinit var orderRepository : OrderRepository

    @Test
    fun ensureUserWithBalanceExistsWithOldUser() {
        val username = "testuser"
        val oldUser = User(username)

        // Arrange
        Mockito.`when`(userRepository.findUserByUsername(username)).thenReturn(oldUser)
        // Act
        val user = exchangeService.ensureUserWithBalanceExists(username)
        // Assert
        assertThat(user).isEqualTo(oldUser)
    }

    @Test
    fun ensureUserWithBalanceExistsWithNewUser() {
        val username = "testuser"

        // Arrange
        Mockito.`when`(userRepository.findUserByUsername(username)).thenReturn(null)
        Mockito.`when`(userRepository.save(Mockito.any(User::class.java)))
            .thenAnswer(Answer { invocation -> invocation.arguments[0] as User })
        // Act
        val user = exchangeService.ensureUserWithBalanceExists(username)
        // Assert
        assertThat(user.username).isEqualTo(username)
    }

    @Test
    fun testDepositUsd() {
        val user = User("testuser")
        val usd = 25000L
        val btc = 2L
        val balance = Balance(user, btc, usd)
        val amount = 123L

        // Arrange
        Mockito.`when`(balanceRepository.findBalanceByOwner(user)).thenReturn(balance)
        // Act
        exchangeService.deposit(user, amount, Currency.USD)
        // Assert
        assertThat(balance.usd).isEqualTo(usd + amount)
    }

    @Test
    fun testGetBalanceOfUser() {
        val user = User("testuser")
        val userBalance = Balance(user, 1, 100)

        // Arrange
        Mockito.`when`(balanceRepository.findBalanceByOwner(user)).thenReturn(userBalance)
        // Act
        val balance = exchangeService.getBalanceOfUser(user)
        // Assert
        assertThat(balance).isEqualTo(userBalance)
    }

    @Test
    fun testExecuteMarketOrder() {
        val buyer = User("buyer")
        val buyerBalance = Balance(buyer, 0, 250000)
        val seller1 = User("seller1")
        val seller1Balance = Balance(seller1, 10, 0)
        val seller1Order = Order(seller1, OrderType.SELL, 10000, 0, 10, 0, OrderState.LIVE, URL("https://test.com"))
        val seller2 = User("seller2")
        val seller2Balance = Balance(seller2, 10, 0)
        val seller2Order = Order(seller2, OrderType.SELL, 20000, 0, 10, 0, OrderState.LIVE, URL("https://test.com"))
        val sellOrders = listOf(seller1Order, seller2Order)

        // Arrange
        Mockito.`when`(balanceRepository.findBalanceByOwner(buyer)).thenReturn(buyerBalance)
        Mockito.`when`(balanceRepository.findBalanceByOwner(seller1)).thenReturn(seller1Balance)
        Mockito.`when`(balanceRepository.findBalanceByOwner(seller2)).thenReturn(seller2Balance)
        Mockito.`when`(orderRepository.findSellers()).thenReturn(sellOrders)

        // Act
        val orderResult = exchangeService.executeMarketOrder(buyer, 15, OrderType.BUY)

        // Assert
        assertThat(buyerBalance.btc).isEqualTo(15)
        assertThat(buyerBalance.usd).isEqualTo(50000)
        assertThat(seller1Balance.btc).isEqualTo(0)
        assertThat(seller1Balance.usd).isEqualTo(100000)
        assertThat(seller2Balance.btc).isEqualTo(5)
        assertThat(seller2Balance.usd).isEqualTo(100000)
        assertThat(orderResult).isNotNull
        assertThat(orderResult!!.btc).isEqualTo(15)
        assertThat(orderResult!!.usd).isEqualTo(200000)
        assertThat(orderResult!!.type).isEqualTo(OrderType.BUY)
        assertThat(orderResult!!.updatedOrders).isEqualTo(sellOrders)
    }

    @Test
    fun testCreateStandingOrderCancelled() {
        val user = User("user")
        val btc = 9L
        val usd = 10000L
        val balance = Balance(user, btc, usd)
        val quantity = 10L
        val orderType = OrderType.SELL
        val limitPrice = 10000L
        val webhookURL = URL("http://test.com")

        // Arrange
        Mockito.`when`(balanceRepository.findBalanceByOwner(user)).thenReturn(balance)
        Mockito.`when`(orderRepository.save(Mockito.any(Order::class.java)))
            .thenAnswer(Answer { invocation -> invocation.arguments[0] as Order })

        // Act
        val orderResult = exchangeService.createStandingOrder(user, quantity, orderType,limitPrice, webhookURL)

        // Assert
        Mockito.verify(orderRepository).save(Mockito.argThat { matcher ->
            // For completeness add other properties in a similar fashion
            matcher.state.equals(OrderState.CANCELLED)
        })

        assertThat(balance.btc).isEqualTo(btc)
        assertThat(balance.usd).isEqualTo(usd)
        assertThat(orderResult).isNotNull
        assertThat(orderResult!!.order.issuer).isEqualTo(user)
        assertThat(orderResult!!.order.type).isEqualTo(orderType)
        assertThat(orderResult!!.order.limitPrice).isEqualTo(limitPrice)
        assertThat(orderResult!!.order.filledQuantity).isZero()
        assertThat(orderResult!!.order.quantity).isEqualTo(quantity)
        assertThat(orderResult!!.order.filledPrice).isZero()
        assertThat(orderResult!!.order.state).isEqualTo(OrderState.CANCELLED)
        assertThat(orderResult!!.order.webhookURL).isEqualTo(webhookURL)
        assertThat(orderResult!!.updatedOrders).isEmpty()
    }

}