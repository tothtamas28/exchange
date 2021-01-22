#!/bin/env python3

import requests
import json as json_mod
import config

def url(path):
    return f'http://{config.HOST}:{config.PORT}{path}'

def header(token):
    return {'Authorization': f'Bearer {token}'}

def request(method, path, token=None, json=None):
    full_url = url(path)
    args = {'method' : method, 'url' : full_url}
    if token is not None:
        args['headers'] = header(token)
    if json is not None:
        args['json'] = json

    print('Request')
    print(json_mod.dumps(args, indent=4))

    response = requests.request(**args)
    res = { 'status' : response.status_code, 'reason' : response.reason }
    try:
        res['json'] = response.json()
    except Exception:
        pass

    print('Response')
    print(json_mod.dumps(res, indent=4))

    print('----')
    return res

def register(username):
    response = request(method='post', path='/register', json={
        'username' : username
    })
    return response['json']['token']

def postBalance(token, topup_amount, currency):
    return request(method='post', path='/balance', token=token, json={
        'topup_amount' : topup_amount,
        'currency' : currency
    })

def getBalance(token):
    return request(method='get', path='/balance', token=token)

def postMarketOrder(token, quantity, type):
    return request(method='post', path='/market_order', token=token, json={
        'quantity' : quantity,
        'type' : type
    })

def postStandingOrder(token, quantity, type, limit_price, webhook_url):
    response = request(method='post', path='/standing_order', token=token, json={
        'quantity' : quantity,
        'type' : type,
        'limit_price' : limit_price,
        'webhook_url' : webhook_url
    })
    return response['json']['order_id']

def deleteStandingOrder(token, id):
    return request(method='delete', path=f'/standing_order/{id}', token=token)

def getStandingOrder(token, id):
    return request(method='get', path=f'/standing_order/{id}', token=token)

def test1():
    # Users A, B, C, D register.
    A = register('A')
    B = register('B')
    C = register('C')
    D = register('D')

    # User A deposits 1BTC, B deposits 10BTC, C deposits $250k and D deposits $300k.
    postBalance(A, 1, 'BTC')
    getBalance(A)

    postBalance(B, 10, 'BTC')
    getBalance(B)

    postBalance(C, 250000, 'USD')
    getBalance(C)

    postBalance(D, 300000, 'USD')
    getBalance(D)

    # User A creates a standing order, selling 10 BTC for a price of $10k.
    # Since A doesn't have that many BTCs, the order is immediately CANCELLED.
    standing1 = postStandingOrder(A, 10, 'SELL', 10000, 'https://google.com')
    getStandingOrder(A, standing1)

    # User A deposits 9 BTC.
    postBalance(A, 9, 'BTC')
    getBalance(A)

    # User A creates a standing order, selling 10 BTC for a price of $10k.
    # This time, the order is opened.
    standing2 = postStandingOrder(A, 10, 'SELL', 10000, 'https://sfdsgsdg.com')
    getStandingOrder(A, standing2)

    # User B creates a standing order, selling 10 BTC for a price of $20k.
    standing3 = postStandingOrder(B, 10, 'SELL', 20000, 'https://google.com')
    getStandingOrder(B, standing3)

    # User C creates a market order, buying 15 BTC.
    # Engine fulfills market order in such a way, it maximizes profit for market order creator.
    # Therefore in this case it buys BTC as cheaply as possible:
    # it'll use 10 cheaper and 5 more expensive BTC that are for sale at the moment.
    # The total cost of the trade will be $200k, leaving the user C with $50k and 15BTC on his account.
    # The average price of the trade is $13,333.
    postMarketOrder(C, 15, 'BUY')

    # After the transaction, both standing orders are notified via webhook,
    # about their state changes—the first standing order is FULFILLED
    # (avg_price = $10k, satisfied_quantity = 10, remaining quantity = 0),
    # the second is LIVE (avg_price = $20k,  satisfied_quantity = 5, remaining quantity = 5)
    getStandingOrder(A, standing2)
    getStandingOrder(B, standing3)

    # User D creates a standing order for buying 20 BTC at the price of $10,000.
    # Note that D has $300k so this is a valid order.
    # There are no BTCs so cheap, so the order sits in the order book in LIVE status.
    standing4 = postStandingOrder(D, 20, 'BUY', 10000, 'https://google.com')
    getStandingOrder(D, standing4)

    # User D creates another standing order for buying 10 BTC with a limit price of $25,000.
    # Since $200k out of D's $300k is reserved on the previous order, D doesn't have enough money to open this order:
    # the order immediately goes to 'CANCELLED' state.
    standing5 = postStandingOrder(D, 10, 'BUY', 25000, 'https://google.com')
    getStandingOrder(D, standing5)

    # User D deletes order 20 BTC @ $10K, ...
    deleteStandingOrder(D, standing4)

    # ..., and once again creates 10 BTC @ $25k order.
    # This time, D has enough money to open this standing order (it's a new order with new order_id)
    # and since there are still BTCs for just $20k available, this order is partially executed immediately.
    # Note that technically any price between $20k and $25k would be technically fine,
    # but the engine favors the later order, thus executing the order at $20k.
    # For the B's standing order, we have avg_price=$20k, for D's standing order it's the same.
    # Both orders are notified via web_hook call.
    standing6 = postStandingOrder(D, 10, 'BUY', 25000, 'https://google.com')
    getStandingOrder(D, standing6)


def test2():
    A = register('A')
    postBalance(A, 10, 'BTC')
    getBalance(A)


if __name__ == "__main__":
    test1()