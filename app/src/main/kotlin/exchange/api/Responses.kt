package exchange.api

import exchange.model.OrderState
import exchange.model.OrderType

data class RegisterResponse(val token : String)

data class PostBalanceResponse(val success: Boolean)

data class GetBalanceResponse(val usd : Long, val btc : Long, val usd_equivalent : Long)

data class PostMarketOrderResponse(val quantity : Long, val  avg_price : Double)

data class PostStandingOrderResponse(val order_id : Long)

data class GetStandingOrderResponse(
    val type : OrderType,
    val limit_price : Long,
    val filled_quantity : Long,
    val quantity : Long,
    val avg_price : Long,
    val state : OrderState
)