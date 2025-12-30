In order for the DynamoThrottler to work you will need to create a DynamoDB table with the following:
* name: throttler
* partitionKey: clientKey (S)
* sortKey: throttlerTime (N)