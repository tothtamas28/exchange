package exchange.security

import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
class JwtFilter(
    val tokenManager: TokenManager,
    val userDetailsService: ExchangeUserDetailsService
) : OncePerRequestFilter() {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    // TODO add token expiration
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val tokenHeader : String? = request.getHeader("Authorization")
        if (tokenHeader != null && tokenHeader.startsWith("Bearer ")) {
            val token = tokenHeader.substring(7)
            val username = tokenManager.getUsernameFromToken(token)
            if (username == null) {
                logger.info("Unable to get username: $token")
            } else {
                if (SecurityContextHolder.getContext().authentication == null) {
                    val userDetails = userDetailsService.loadUserByUsername(username)
                    if (userDetails == null) {
                        logger.info("User not found: $username")
                    } else {
                        if (tokenManager.validateJwtToken(token, userDetails)) {
                            val authenticationToken =
                                UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                            authenticationToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                            SecurityContextHolder.getContext().authentication = authenticationToken
                        } else {
                            logger.info("Invalid token: $username")
                        }
                    }
                }
            }
        }
        filterChain.doFilter(request, response)
    }
}