package exchange.service

import exchange.data.BalanceRepository
import exchange.data.OrderRepository
import exchange.data.UserRepository
import exchange.model.User
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles

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
        // Arrange
        val username = "testuser"
        val oldUser = User(username)
        Mockito.`when`(userRepository.findUserByUsername(username)).thenReturn(oldUser)
        // Act
        val user = exchangeService.ensureUserWithBalanceExists(username)
        // Assert
        assertThat(user).isEqualTo(oldUser)
    }

    @Test
    fun ensureUserWithBalanceExistsWithNewUser() {
        // Arrange
        val username = "testuser"
        Mockito.`when`(userRepository.findUserByUsername(username)).thenReturn(null)
        Mockito.`when`(userRepository.save(Mockito.any(User::class.java)))
            .thenAnswer(Answer { invocation ->
                val user = invocation.arguments[0] as User
                user
            })
        // Act
        val user = exchangeService.ensureUserWithBalanceExists(username)
        // Assert
        assertThat(user.username).isEqualTo(username)
    }
}