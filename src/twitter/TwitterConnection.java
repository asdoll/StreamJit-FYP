package twitter;

import twitter4j.FilterQuery;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterException;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.conf.ConfigurationBuilder;

public class TwitterConnection {

  public static void run() throws TwitterException {
	  ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
      configurationBuilder.setOAuthConsumerKey("A0qa2gDQTQIkVLaQwrgPy7o8X")
              .setOAuthConsumerSecret("FUflLLqJxCmlqrP1ev5ihnHq6PpK3eWDm6ljm4gEQjcGoPYQPr")
              .setOAuthAccessToken("2454581378-lEaaFQCFRbNKp5iM9K0qqDQj4FEcKsraudrFNQO")
              .setOAuthAccessTokenSecret("pmK3TdvszfRt0OsDwFwqSycRvIHHA4Gj5EM8r4Fz44f3r");
      TwitterStream twitterStream = new TwitterStreamFactory(configurationBuilder.build()).getInstance();
      twitterStream.setStatusListener(new StatusListener () {
          public void onStatus(Status status) {
              System.out.println(status.getText()); // print tweet text to console
           }

		@Override
		public void onDeletionNotice(StatusDeletionNotice arg0) {
			System.out.println("onDeletionNotice");
			
		}

		@Override
		public void onException(Exception arg0) {
			arg0.printStackTrace();
		}

		@Override
		public void onTrackLimitationNotice(int arg0) {
			System.out.println("onTrackLimitationNotice");
			
		}});
		FilterQuery tweetFilterQuery = new FilterQuery(); 
		twitterStream.filter(tweetFilterQuery);
  }

  public static void main(String[] args) {
    try {
    	TwitterConnection.run();
    } catch (TwitterException e) {
      System.out.println(e);
    }
    
  }
}