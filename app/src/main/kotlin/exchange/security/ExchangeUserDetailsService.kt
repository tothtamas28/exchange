package exchange.security

import exchange.data.UserRepository
import exchange.model.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class ExchangeUserDetailsService(
    val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails? {
        val user = userRepository.findUserByUsername(username)
        return user?.let { ExchangeUserDetails(it) }
    }

}