package com.alrashed.iptv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private EditText playlistUrl;
    private TextView status;
    private ListView channelsList;
    private PlayerView playerView;
    private ExoPlayer player;
    private final ArrayList<Channel> channels = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static class Channel {
        final String name;
        final String url;
        Channel(String name, String url) { this.name = name; this.url = url; }
        @Override public String toString() { return name; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 24, 43));
        getWindow().setNavigationBarColor(Color.rgb(13, 24, 43));
        buildScreen();
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(8));
        root.setBackgroundColor(Color.rgb(10, 18, 32));

        TextView title = new TextView(this);
        title.setText("آل راشد IPTV");
        title.setTextColor(Color.rgb(255, 190, 55));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView subtitle = new TextView(this);
        subtitle.setText("مشغّل قوائم M3U / M3U8");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(28)));

        playlistUrl = new EditText(this);
        playlistUrl.setSingleLine(true);
        playlistUrl.setTextColor(Color.WHITE);
        playlistUrl.setHintTextColor(Color.GRAY);
        playlistUrl.setHint("ضع رابط قائمة M3U هنا");
        playlistUrl.setTextSize(14);
        playlistUrl.setPadding(dp(10), 0, dp(10), 0);
        playlistUrl.setBackgroundColor(Color.rgb(28, 40, 59));
        root.addView(playlistUrl, new LinearLayout.LayoutParams(-1, dp(48)));

        Button load = new Button(this);
        load.setText("تحميل القنوات");
        load.setTextColor(Color.BLACK);
        load.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(255, 190, 55)));
        LinearLayout.LayoutParams loadParams = new LinearLayout.LayoutParams(-1, dp(46));
        loadParams.topMargin = dp(7);
        root.addView(load, loadParams);

        status = new TextView(this);
        status.setText("أدخل رابط قائمة M3U لعرض القنوات");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(34)));

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        root.addView(playerView, new LinearLayout.LayoutParams(-1, dp(205)));

        TextView listTitle = new TextView(this);
        listTitle.setText("القنوات");
        listTitle.setTextColor(Color.WHITE);
        listTitle.setTextSize(17);
        listTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        listTitle.setPadding(dp(4), dp(8), dp(4), dp(5));
        root.addView(listTitle);

        channelsList = new ListView(this);
        channelsList.setBackgroundColor(Color.rgb(17, 28, 44));
        channelsList.setDividerHeight(dp(1));
        channelsList.setAdapter(new ArrayAdapter<Channel>(this, android.R.layout.simple_list_item_1, channels) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                row.setTextColor(Color.WHITE);
                row.setTextSize(15);
                row.setPadding(dp(12), dp(8), dp(12), dp(8));
                row.setBackgroundColor(Color.rgb(17, 28, 44));
                return row;
            }
        });
        root.addView(channelsList, new LinearLayout.LayoutParams(-1, 0, 1));

        load.setOnClickListener(v -> loadPlaylist());
        channelsList.setOnItemClickListener((parent, view, position, id) -> playChannel(channels.get(position)));
        setContentView(root);
    }

    private void loadPlaylist() {
        String address = playlistUrl.getText().toString().trim();
        if (address.isEmpty() || !(address.startsWith("http://") || address.startsWith("https://"))) {
            Toast.makeText(this, "أدخل رابط M3U صحيحاً يبدأ بـ http أو https", Toast.LENGTH_LONG).show();
            return;
        }
        status.setText("جارٍ تحميل القائمة...");
        executor.execute(() -> {
            ArrayList<Channel> loaded = new ArrayList<>();
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "AlRashedIPTV/1.0");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                    String line;
                    String name = "قناة";
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("#EXTINF")) {
                            int comma = line.lastIndexOf(',');
                            name = comma >= 0 && comma < line.length() - 1 ? line.substring(comma + 1).trim() : "قناة";
                        } else if (!line.isEmpty() && !line.startsWith("#") &&
                                (line.startsWith("http://") || line.startsWith("https://"))) {
                            loaded.add(new Channel(name, line));
                            name = "قناة";
                        }
                    }
                }
                runOnUiThread(() -> {
                    channels.clear();
                    channels.addAll(loaded);
                    ((ArrayAdapter<?>) channelsList.getAdapter()).notifyDataSetChanged();
                    status.setText("تم تحميل " + loaded.size() + " قناة");
                    if (loaded.isEmpty()) Toast.makeText(this, "لم يتم العثور على قنوات. تحقق من الرابط.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("تعذّر تحميل القائمة؛ تحقق من الرابط والإنترنت"));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void playChannel(Channel channel) {
        if (player == null) return;
        status.setText("يتم تشغيل: " + channel.name);
        player.setMediaItem(MediaItem.fromUri(channel.url));
        player.prepare();
        player.play();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override protected void onDestroy() {
        if (player != null) player.release();
        executor.shutdownNow();
        super.onDestroy();
    }
}
