package exchange

import exchange.data.BalanceRepository
import exchange.data.OrderRepository
import exchange.data.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest
@ActiveProfiles("test")
class ExchangeApplicationTests {

	@MockBean
	private lateinit var userRepository : UserRepository

	@MockBean
	private lateinit var balanceRepository : BalanceRepository

	@MockBean
	private lateinit var orderRepository : OrderRepository

	@Test
	fun contextLoads() {
	}

}
