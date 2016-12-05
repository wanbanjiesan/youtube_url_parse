package com.fq.youtubeparse;

import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        YouTubeUtility youTubeUtility = new YouTubeUtility();
        youTubeUtility.executeQueryYouTubeTask(YouTubeUtility.TYPE_YOUTUBE_VIDEO_SINGLE, "your youtube videoId");

        youTubeUtility.setOnQueryYoutubeSuccessListener(new YouTubeUtility.OnQueryYoutubeSuccessListener() {
            @Override
            public void onQueryYoutubeSuccess(Uri pResult) {

            }
        });


        youTubeUtility.setOnQueryYoutubeErrorListener(new YouTubeUtility.OnQueryYoutubeErrorListener() {
            @Override
            public void onQueryYoutubeError(int errorTag, String errorMsg, String description) {
                /**
                 * 此处注意，不一定在主线程，更新ui要注意
                 */
            }
        });
    }
}
