package exchange.api

import exchange.model.Currency
import exchange.model.OrderState
import exchange.model.OrderType
import exchange.security.ExchangeUserDetails
import exchange.security.TokenManager
import exchange.service.ExchangeService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriBuilder
import reactor.core.publisher.Mono
import java.net.URL
import kotlin.math.roundToLong

@RestController
class ExchangeController(
    val exchangeService: ExchangeService,
    val tokenManager: TokenManager
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    data class RegisterRequest(val username: String)

    @PostMapping(path = ["/register"], consumes = ["application/json"])
    fun register(@RequestBody request : RegisterRequest) : RegisterResponse {
        logger.info("POST /register $request")

        // Authentication skipped in example, would be performed here
        // val userDetails = userDetailsService.loadUserByUsername(request.username)

        val user = exchangeService.ensureUserWithBalanceExists(request.username)
        val jwtToken = tokenManager.generateJwtToken(user)
        return RegisterResponse(jwtToken)
    }

    data class BalanceRequest(
        val topup_amount : Long,
        val currency : Currency
    )

    @PostMapping(path = ["/balance"], consumes = ["application/json"])
    fun postBalance(
        @AuthenticationPrincipal userDetails : ExchangeUserDetails,
        @RequestBody request : BalanceRequest
    ) : PostBalanceResponse {
        logger.info("POST /balance ${userDetails.username} $request")
        return try {
            exchangeService.deposit(userDetails.user, request.topup_amount, request.currency)
            PostBalanceResponse(true)
        } catch (e  :Exception) {
            PostBalanceResponse(false)
        }
    }

    @GetMapping(path = ["/balance"])
    fun getBalance(
        @AuthenticationPrincipal userDetails : ExchangeUserDetails
    ) : ResponseEntity<GetBalanceResponse> {
        logger.info("GET /balance ${userDetails.username}")
        val balance = exchangeService.getBalanceOfUser(userDetails.user) ?: return ResponseEntity.notFound().build()
        val rate = if (balance.usd > 0) {
            try {
                exchangeService.getCMCExchangeRate()
            } catch (e : Exception) {
                return ResponseEntity.status(500).build()
            }
        } else {
            .0
        }
        return ResponseEntity.ok(GetBalanceResponse(balance.usd, balance.btc, (balance.btc * rate).roundToLong()))
    }

    data class MarketOrderRequest(
        val quantity : Long,
        val type : OrderType
    )

    @PostMapping(path = ["/market_order"], consumes = ["application/json"])
    fun postMarketOrder(
        @AuthenticationPrincipal userDetails : ExchangeUserDetails,
        @RequestBody request : MarketOrderRequest,
    ) : ResponseEntity<PostMarketOrderResponse> {
        logger.info("POST /market_order ${userDetails.username} $request")
        val orderResult = exchangeService.executeMarketOrder(userDetails.user, request.quantity, request.type)
        return orderResult?.let {
            it.updatedOrders.forEach {
                callWebhook(it.webhookURL)
            }
            ResponseEntity.ok(PostMarketOrderResponse(it.btc, (it.usd / it.btc).toDouble()))
        } ?: ResponseEntity.status(500).build()
    }

    data class StandingOrderRequest(
        val quantity : Long,
        val type : OrderType,
        val limit_price : Long,
        val webhook_url : URL
    )

    @PostMapping(path = ["/standing_order"], consumes = ["application/json"])
    fun postStandingOrder(
        @AuthenticationPrincipal userDetails : ExchangeUserDetails,
        @RequestBody request : StandingOrderRequest
    ) : ResponseEntity<PostStandingOrderResponse> {
        logger.info("POST /standing_order ${userDetails.username} $request")
        val standingOrderResult = exchangeService.createStandingOrder(
            userDetails.user,
            request.quantity,
            request.type,
            request.limit_price,
            request.webhook_url
        )
        return standingOrderResult?.let {
            standingOrderResult.updatedOrders.forEach {
                callWebhook(it.webhookURL)
            }
            ResponseEntity.ok(PostStandingOrderResponse(standingOrderResult.order.id))
        } ?: ResponseEntity.status(500).build()
    }

    @DeleteMapping(path = ["/standing_order/{id}"])
    fun deleteStandingOrder(
        @AuthenticationPrincipal userDetails : ExchangeUserDetails,
        @PathVariable(name ="id") id : Long,
    ) : ResponseEntity<Any> {
        logger.info("DELETE /standing_order/$id ${userDetails.username}")
        val success = exchangeService.removeOrderOfUserById(userDetails.user, id)
        return if (success) {
            ResponseEntity.ok().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping(path = ["/standing_order/{id}"])
    fun getStandingOrder(
        @AuthenticationPrincipal userDetails : ExchangeUserDetails,
        @PathVariable(name ="id") id : Long,
    ) : ResponseEntity<GetStandingOrderResponse> {
        logger.info("GET /standing_order/$id ${userDetails.username}")
        return exchangeService.findOrderOfUserById(userDetails.user, id)?.let {
            ResponseEntity.ok(GetStandingOrderResponse(
                it.type,
                it.limitPrice,
                it.filledQuantity,
                it.quantity,
                if (it.filledQuantity > 0) { it.filledPrice / it.filledQuantity } else { 0L },
                it.state
            ))
        } ?: ResponseEntity.notFound().build()
    }

    private fun callWebhook(url : URL) {
        val client = WebClient.create()
        client.get().uri { builder : UriBuilder ->
            builder.path(url.host).build()
        }.retrieve().toBodilessEntity().onErrorResume {
            logger.info("Webhook $url failed: ${it.message}")
            Mono.justOrEmpty(null)
        }.subscribe {
            logger.info("Webhook $url copleted: status ${it.statusCode}")
        }
    }
}