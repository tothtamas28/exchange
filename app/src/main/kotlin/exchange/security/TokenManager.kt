package exchange.security

import exchange.model.User
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import java.security.Key
import java.util.*
import io.jsonwebtoken.security.Keys

import io.jsonwebtoken.io.Decoders

// https://www.tutorialspoint.com/spring_security/spring_security_with_jwt.htm
// https://stackoverflow.com/questions/55102937/how-to-create-a-spring-security-key-for-signing-a-jwt-token

@Component
class TokenManager(
    @Value("\${secret}")
    val secret : String
) {

    private fun signingKey() : Key {
        val keyBytes = Decoders.BASE64.decode(this.secret)
        return Keys.hmacShaKeyFor(keyBytes)
    }

    fun generateJwtToken(user : User) : String {
        return Jwts.builder()
            .claim("username", user.username)
            .setIssuedAt(Date(System.currentTimeMillis()))
            .signWith(signingKey(), SignatureAlgorithm.HS512)
            .compact()
    }

    fun validateJwtToken(token : String, userDetails: UserDetails) : Boolean {
        val username = getUsernameFromToken(token)
        return if (username == null) {
            false
        } else {
            username == userDetails.username
        }
    }

    fun getUsernameFromToken(token : String) : String? {
        val claims = Jwts.parserBuilder().setSigningKey(signingKey()).build().parseClaimsJws(token).body
        return claims.get("username", String::class.java)
    }

}