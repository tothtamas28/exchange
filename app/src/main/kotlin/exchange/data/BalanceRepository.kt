package exchange.data

import exchange.model.Balance
import exchange.model.User
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface BalanceRepository : CrudRepository<Balance, Long> {
    fun findBalanceByOwner(owner : User) : Balance?
}