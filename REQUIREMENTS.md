The [Twitter Streaming API](https://dev.twitter.com/docs/streaming-apis) provides [real-time access to public tweets](https://dev.twitter.com/docs/streaming-apis/streams/public). In this assignment you will build a Scala application that connects to the Streaming API and processes incoming tweets to compute various statistics.

The [sample endpoint](https://dev.twitter.com/docs/api/1.1/get/statuses/sample) provides a random sample of approximately 1% of the full tweet stream. Your app should consume this sample stream and keep track of the following:

* Total number of tweets received
* Average tweets per hour/minute/second
* Top [emojis](http://en.wikipedia.org/wiki/Emoji) in tweets
* Percent of tweets that contains emojis
* Top hashtags
* Percent of tweets that contain a url
* Percent of tweets that contain a photo url ([pic.twitter.com](http://pic.twitter.com/) or instagram)
* Top domains of urls in tweets

The [emoji-data](https://github.com/iamcal/emoji-data) project provides a convenient emoji.json file that you can use
 to determine which emoji unicode characters to look for in the tweet text.

Your app should also provide some way to report these values to a user (periodically log to terminal, return from RESTful web service, etc). If there are other interesting statistics you’d like to collect, that would be great. There is no need to store this data in a database; keeping everything in-memory is fine. That said, you should think about how you would persist data if that was a requirement.

It’s very important that when your system receives a tweet, you do not block while doing all of the tweet processing. [Twitter regularly sees 5700 tweets/second](https://blog.twitter.com/2013/new-tweets-per-second-record-and-how), so your app may likely receive 57 tweets/second, with higher burst rates. The app should also process tweets as concurrently as possible, to take advantage of all available computing resources. While this system doesn’t need to handle the full tweet stream, you should think about how you could scale up your app to handle such a high volume of tweets.

When you're finished, please put your project in a repository on either Github or Bitbucket and send us a link. We'll then do a pair programming session where we'll have some questions for you about your code and possibly make some additions to it.
