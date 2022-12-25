package me.lucasskywalker.apis;

import com.twitter.clientlib.TwitterCredentialsBearer;
import com.twitter.clientlib.ApiException;
import com.twitter.clientlib.api.TwitterApi;
import com.twitter.clientlib.model.*;
import me.lucasskywalker.BotMain;

import java.util.HashSet;

public class TwitterImpl {

    public void getLatestTweet() {
        TwitterCredentialsBearer credentialsBearer = new TwitterCredentialsBearer(BotMain.getConfig().get("BEARER_TOKEN"));
        TwitterApi api = new TwitterApi(credentialsBearer);

        String userID = "86395621";

        try {
            Get2UsersIdTweetsResponse response = api.tweets().usersIdTweets(userID).tweetFields(new HashSet<String>()).excludeInputFields().maxResults(5).execute();
            System.out.println(response.getData().get(1));
        } catch (ApiException e) {
            e.printStackTrace();
        }
    }
}
