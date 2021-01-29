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
@MockBean(classes = [UserRepository::class, BalanceRepository::class, OrderRepository::class])
class ExchangeApplicationTests {

	@Test
	fun contextLoads() {
	}

}
