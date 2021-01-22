package exchange.api

import exchange.model.Currency
import exchange.model.OrderState
import exchange.model.OrderType
import exchange.security.ExchangeUserDetails
import exchange.security.TokenManager
import exchange.service.ExchangeService
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
    data class RegisterRequest(val username: String)

    @PostMapping(path = ["/register"], consumes = ["application/json"])
    fun postRegister(@RequestBody request : RegisterRequest) : Any {
        data class Response(val token : String)

        // Authentication skipped in example, would be performed here
        // val userDetails = userDetailsService.loadUserByUsername(request.username)

        val user = exchangeService.ensureUserWithBalanceExists(request.username)
        val jwtToken = tokenManager.generateJwtToken(user)

        println("request: $request")
        return Response(jwtToken)
    }

    data class BalanceRequest(
        val topup_amount : Long,
        val currency : Currency
    )

    @PostMapping(path = ["/balance"], consumes = ["application/json"])
    fun postBalance(
        @AuthenticationPrincipal userDetails : ExchangeUserDetails,
        @RequestBody request : BalanceRequest
    ) : Any {
        data class Response(val success : Boolean)

        println("User: ${userDetails.username}, Request: $request")

        return try {
            exchangeService.deposit(userDetails.user, request.topup_amount, request.currency)
            Response(true)
        } catch (e  :Exception) {
            Response(false)
        }
    }

    @GetMapping(path = ["/balance"])
    fun getBalance(
        @AuthenticationPrincipal userDetails : ExchangeUserDetails
    ) : ResponseEntity<Any> {
        data class Response(
            val usd : Long,
            val btc : Long,
            val usd_equivalent : Long
        )
        val username = userDetails.username
        println("User: ${username}, Request: GET /balance")
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
        return ResponseEntity.ok(Response(balance.usd, balance.btc, (balance.btc * rate).roundToLong()))
    }

    data class MarketOrderRequest(
        val quantity : Long,
        val type : OrderType
    )

    @PostMapping(path = ["/market_order"], consumes = ["application/json"])
    fun postMarketOrder(
        @AuthenticationPrincipal userDetails : ExchangeUserDetails,
        @RequestBody request : MarketOrderRequest,
    ) : Any {
        data class Response(
            val quantity : Long,
            val  avg_price : Double
        )
        val orderResult = exchangeService.executeMarketOrder(userDetails.user, request.quantity, request.type)
        orderResult.updatedOrders.forEach {
            callWebhook(it.webhookURL)
        }
        // TODO webhooks
        println("User: ${userDetails.username}, Request: $request")
        return Response(orderResult.btc, (orderResult.usd / orderResult.btc).toDouble())
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
    ) : Any {
        data class Response(val order_id : Long)

        println("User: ${userDetails.username}, Request: $request")

        val standingOrderResult = exchangeService.createStandingOrder(
            userDetails.user,
            request.quantity,
            request.type,
            request.limit_price,
            request.webhook_url
        )
        standingOrderResult.updatedOrders.forEach {
            callWebhook(it.webhookURL)
        }
        return Response(standingOrderResult.order.id)
    }

    @DeleteMapping(path = ["/standing_order/{id}"])
    fun deleteStandingOrder(
        @AuthenticationPrincipal userDetails : ExchangeUserDetails,
        @PathVariable(name ="id") id : Long,
    ) : ResponseEntity<Any> {
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
    ) : ResponseEntity<Any> {
        data class Response(
            val type : OrderType,
            val limit_price : Long,
            val filled_quantity : Long,
            val quantity : Long,
            val avg_price : Long,
            val state : OrderState
        )

        return exchangeService.findOrderOfUserById(userDetails.user, id)?.let {
            ResponseEntity.ok(Response(
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
            println("Webhook call to URL $url failed: ${it.message}")
            Mono.justOrEmpty(null)
        }.subscribe {
            println("Webhook call to URL $url copleted with status ${it.statusCode}")
        }
    }
}