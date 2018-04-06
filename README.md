# How to run
Run mvn spring-boot:run in your terminal

# How to see the api
Type http://localhost:8080/swagger-ui.html in your browser

# Testing accounts
I've included a testing wallet with Testnet funds to allow people to try out-of-the-box.

To utilize them, use _PUT /wallet/unlock_ to load them into the in-memory map.

Mind it is a testing account in a Test network, i.e.: there is no point in stealing those funds :P

# Api Usage
## Transaction Send
1. Use _GET /wallet/mnemonic_ to get a new Mnemonic seed, try to save those, as they will help you to recover the wallet in case of need.
2. Use _POST /wallet_ to register a new Wallet (It requires the mnemonics from previous step)
3. Use _PUT /wallet/{walletId}/transaction/send_ to create and send a new Transaction.
4. Check the logs to see if the transaction is confirmed by a block, or just do polling on _GET /transaction/{transactionId}_ until it contains blockHash and blockNumber attributes

## Transaction Create and Broadcast
An alternative to the previous way of creating transactions is via _GET /wallet/{walletId}/transaction/create_ that will return a valid transaction, in case the user wants to verify the Fee cost.

Once it has been validated, the transaction can be send with _PUT /wallet/{walletId}/transaction/broadcast_

Unfortunately, BitcoinJ won't accept this transaction directly, only the outputs of it, so as much you can use Broadcast with the outputs of the transaction and, finger-crossed, the inputs will be the same :X

## Wallet with Mnemonics
1. Use _GET /wallet/mnemonic_ to obtain a list of mnemonic words.
2. Use _POST /wallet_ to create a Wallet using the mnemonic words.
3. Use _GET /wallet/{walletId}_ to get details of a wallet.

## Wallet Unlock
1. Use _PUT /wallet/unlock/{walletId}_ to unlock an existing Wallet.
    
   Unlock allows you to load an existing wallet file from the file system, could be any wallet created by this tool or created by any other tool using BitcoinJ.
2. Use _GET /wallet/{walletId}_ to get details of a wallet.

## Wallet's balance
1. Use _GET /wallet/mnemonic_ to obtain a list of mnemonic words.
2. Use _POST /wallet_ to register a new Wallet 
3. Use _GET /wallet/{walletId}/balance_ to see the wallet's balance in ETHER

## Payment Protocol
This tool includes a self-contained (Client and Server present) example of how to use Payment-Protocol.

1. Use _GET /wallet/mnemonic_ to obtain a list of mnemonic words.
2. Use _POST /wallet/mnemonic_ to create a Wallet using the mnemonic words.

   Or, alternatively, unlock an existing wallet with _PUT /wallet/unlock/{walletId}_
3. Fetch a payment request from your wallet using _POST /payment-client/payment-request_

   Hint: Use http://localhost:8080/payment-server/<your-walletId> to get a payment request meant for your wallet.
   You can set the amount to pay by adding the query parameter: _?amount=<your-amount>_; mind that amount is expected in Bitcoin, not in Satoshis
4. Once Payment-Request has been fetched, use _POST /payment-client/payment/{walletId}_ with the data coming from the previous endpoint.
   
   Mind that from the Transaction data you only need to set the Output part, not the entire transaction, I plan to improve this if I have time to do so...

Payment Protocol is meant for you to pay your providers in a safe way (PaymentClient*) and/or to allow your users to use the platform in a safe way (PaymentServer*)