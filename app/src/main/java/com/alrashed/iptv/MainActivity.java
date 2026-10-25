package com.alrashed.iptv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final int bg=Color.rgb(10,17,30), panel=Color.rgb(24,36,54), gold=Color.rgb(255,190,55);
    private EditText addressField, userField, passField, macField, searchField;
    private Spinner sourceSpinner;
    private TextView status, nowPlaying, empty;
    private ListView listView;
    private PlayerView playerView;
    private ExoPlayer player;
    private final ArrayList<Channel> allChannels=new ArrayList<>(), visibleChannels=new ArrayList<>();
    private final Set<String> favorites=new HashSet<>();
    private boolean onlyFavorites=false;
    private SharedPreferences prefs;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();

    static class Channel {
        String name,url,group,logo,id;
        Channel(String n,String u,String g,String l,String i){name=n;url=u;group=g;logo=l;id=i;}
        String key(){return (id==null||id.isEmpty()?url:id)+"|"+name;}
        @Override public String toString(){return name+(group==null||group.isEmpty()?"":"  ·  "+group);}
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        prefs=getSharedPreferences("alrashed",MODE_PRIVATE);
        favorites.addAll(prefs.getStringSet("favorites",new HashSet<>()));
        buildUi();
        player=new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException error){
                status.setText("تعذّر التشغيل: "+error.getErrorCodeName()+" — قد يكون الرابط منتهيًا أو الصيغة غير مدعومة");
            }
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_BUFFERING) status.setText("جارٍ تحميل البث…");
                else if(state==Player.STATE_READY) status.setText("البث جاهز");
            }
        });
    }

    private void buildUi(){
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(bg);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(10),dp(14),dp(14)); scroll.addView(root);
        TextView title=text("آل راشد IPTV",26,gold,true); title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView sub=text("مشغّل مجاني للهاتف والتلفاز • M3U / Xtream / Stalker",13,Color.LTGRAY,false); sub.setGravity(Gravity.CENTER);
        root.addView(sub,new LinearLayout.LayoutParams(-1,dp(28)));

        sourceSpinner=new Spinner(this);
        ArrayAdapter<String> sources=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
            new String[]{"قائمة M3U / M3U8","Xtream Codes API","Stalker Portal"});
        sourceSpinner.setAdapter(sources); styleSpinner(sourceSpinner);
        root.addView(sourceSpinner,match(dp(48)));
        addressField=field("رابط قائمة M3U أو رابط السيرفر / Portal");
        root.addView(addressField,match(dp(48)));
        userField=field("اسم المستخدم (Xtream)"); root.addView(userField,match(dp(46)));
        passField=field("كلمة المرور (Xtream)"); passField.setInputType(129); root.addView(passField,match(dp(46)));
        macField=field("عنوان MAC لجهازك/اشتراكك (Stalker) مثال 00:1A:79:XX:XX:XX"); root.addView(macField,match(dp(46)));
        sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int pos,long id){
                userField.setVisibility(pos==1?View.VISIBLE:View.GONE);
                passField.setVisibility(pos==1?View.VISIBLE:View.GONE);
                macField.setVisibility(pos==2?View.VISIBLE:View.GONE);
                addressField.setHint(pos==0?"رابط قائمة M3U / M3U8":pos==1?"عنوان السيرفر مثل https://example.com:8080":"رابط Stalker Portal مثل https://example.com/c/");
            }
            @Override public void onNothingSelected(AdapterView<?> p){}
        });
        Button load=button("تحميل القنوات"); root.addView(load,match(dp(48)));
        load.setOnClickListener(v->loadSource());
        searchField=field("ابحث عن قناة أو مجموعة…"); root.addView(searchField,match(dp(44)));
        searchField.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int start,int before,int count){filterChannels();}
            public void afterTextChanged(Editable e){}
        });
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button all=button("كل القنوات"), fav=button("★ المفضلة");
        actions.addView(all,new LinearLayout.LayoutParams(0,dp(44),1)); actions.addView(fav,new LinearLayout.LayoutParams(0,dp(44),1));
        root.addView(actions); all.setOnClickListener(v->{onlyFavorites=false;filterChannels();});
        fav.setOnClickListener(v->{onlyFavorites=true;filterChannels();});
        nowPlaying=text("اختر قناة لبدء المشاهدة",14,gold,true); root.addView(nowPlaying,match(dp(34)));
        playerView=new PlayerView(this); playerView.setUseController(true); playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        root.addView(playerView,new LinearLayout.LayoutParams(-1,dp(220)));
        status=text("أدخل بيانات مصدر IPTV تملكه أو لديك إذن باستخدامه.",13,Color.LTGRAY,false);
        root.addView(status,match(dp(42)));
        TextView heading=text("القنوات",19,Color.WHITE,true); root.addView(heading,match(dp(36)));
        listView=new ListView(this); listView.setBackgroundColor(panel); listView.setDividerHeight(dp(1));
        listView.setFocusable(true); listView.setAdapter(new ChannelAdapter());
        root.addView(listView,new LinearLayout.LayoutParams(-1,dp(420)));
        listView.setOnItemClickListener((p,v,pos,id)->play(visibleChannels.get(pos)));
        listView.setOnItemLongClickListener((p,v,pos,id)->{Channel c=visibleChannels.get(pos); if(!favorites.add(c.key()))favorites.remove(c.key()); saveFavorites(); ((BaseAdapter)listView.getAdapter()).notifyDataSetChanged(); status.setText(favorites.contains(c.key())?"أضيفت إلى المفضلة":"أزيلت من المفضلة"); return true;});
        TextView hint=text("نصيحة: اضغط مطولًا على قناة لإضافتها إلى المفضلة أو إزالتها.",12,Color.LTGRAY,false); root.addView(hint,match(dp(32)));
        setContentView(scroll);
    }

    private class ChannelAdapter extends BaseAdapter {
        public int getCount(){return visibleChannels.size();}
        public Object getItem(int p){return visibleChannels.get(p);}
        public long getItemId(int p){return p;}
        public View getView(int p,View convert,ViewGroup parent){
            LinearLayout row=new LinearLayout(MainActivity.this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(10),dp(5),dp(10),dp(5)); row.setOrientation(LinearLayout.VERTICAL); row.setBackgroundColor(panel);
            Channel c=visibleChannels.get(p);
            TextView name=text((favorites.contains(c.key())?"★  ":"")+c.name,16,Color.WHITE,true);
            row.addView(name);
            if(c.group!=null&&!c.group.isEmpty())row.addView(text(c.group,12,Color.LTGRAY,false));
            row.setMinimumHeight(dp(54)); return row;
        }
    }

    private void loadSource(){
        final int mode=sourceSpinner.getSelectedItemPosition();
        final String address=addressField.getText().toString().trim();
        final String user=userField.getText().toString().trim(), pass=passField.getText().toString().trim(), mac=macField.getText().toString().trim();
        if(address.isEmpty()){toast("أدخل الرابط أولًا");return;}
        if(mode==1&&(user.isEmpty()||pass.isEmpty())){toast("أدخل اسم المستخدم وكلمة المرور");return;}
        if(mode==2&&mac.isEmpty()){toast("أدخل MAC المخصص لاشتراكك");return;}
        status.setText("جارٍ الاتصال وتحميل القنوات…");
        worker.execute(()->{
            try{
                ArrayList<Channel> result;
                if(mode==0) result=loadM3u(address);
                else if(mode==1) result=loadXtream(address,user,pass);
                else result=loadStalker(address,mac);
                runOnUiThread(()->{
                    allChannels.clear();allChannels.addAll(result);onlyFavorites=false;filterChannels();
                    status.setText("اكتمل التحميل: "+result.size()+" قناة");
                    if(result.isEmpty())toast("لم نجد قنوات. تحقق من الرابط وبيانات الاشتراك.");
                });
            }catch(Exception e){runOnUiThread(()->status.setText("فشل التحميل: "+safeMessage(e)+" — تحقق من بيانات المصدر والإنترنت"));}
        });
    }

    private ArrayList<Channel> loadM3u(String address)throws Exception{
        String body=request(address,null,null,20000);
        ArrayList<Channel> out=new ArrayList<>(); String name="قناة",group="",logo="",id="";
        for(String raw:body.split("\\r?\\n")){
            String line=raw.trim(); if(line.isEmpty())continue;
            if(line.startsWith("#EXTINF")){
                int comma=line.lastIndexOf(','); name=comma>=0?line.substring(comma+1).trim():attr(line,"tvg-name");
                if(name.isEmpty())name="قناة";
                group=attr(line,"group-title");logo=attr(line,"tvg-logo");id=attr(line,"tvg-id");
            }else if(line.startsWith("#EXTGRP:")){group=line.substring(8).trim();}
            else if(!line.startsWith("#")&&(line.startsWith("http://")||line.startsWith("https://"))){
                out.add(new Channel(name,line,group,logo,id));name="قناة";group="";logo="";id="";
            }
        }
        return out;
    }

    private ArrayList<Channel> loadXtream(String base,String user,String pass)throws Exception{
        base=base.replaceAll("/+$","");
        String api=base+"/player_api.php?username="+enc(user)+"&password="+enc(pass);
        JSONObject auth=new JSONObject(request(api,null,null,18000));
        if(auth.has("user_info")&&!auth.getJSONObject("user_info").optString("auth","1").equals("1"))throw new Exception("بيانات Xtream غير مقبولة");
        HashMap<String,String> groups=new HashMap<>();
        try{
            JSONArray cats=new JSONArray(request(api+"&action=get_live_categories",null,null,18000));
            for(int i=0;i<cats.length();i++){JSONObject o=cats.getJSONObject(i);groups.put(o.optString("category_id"),o.optString("category_name"));}
        }catch(Exception ignored){}
        JSONArray streams=new JSONArray(request(api+"&action=get_live_streams",null,null,25000));
        ArrayList<Channel> out=new ArrayList<>();
        for(int i=0;i<streams.length();i++){
            JSONObject o=streams.getJSONObject(i);String sid=o.optString("stream_id");
            String ext=o.optString("container_extension","ts");if(ext.isEmpty())ext="ts";
            String url=base+"/live/"+enc(user)+"/"+enc(pass)+"/"+sid+"."+ext;
            String cat=o.optString("category_id");
            out.add(new Channel(o.optString("name","قناة"),url,groups.getOrDefault(cat,""),o.optString("stream_icon",""),sid));
        }
        return out;
    }

    private ArrayList<Channel> loadStalker(String portal,String mac)throws Exception{
        portal=portal.trim(); if(!portal.endsWith("/"))portal+="/";
        String endpoint=portal+(portal.endsWith("server/load.php")?"":"server/load.php");
        String cookie="mac="+mac+"; stb_lang=en; timezone=UTC";
        String headers=""; 
        JSONObject handshake=new JSONObject(request(endpoint+"?type=stb&action=handshake&token=&JsHttpRequest=1-xml",cookie,null,18000));
        String token=handshake.optJSONObject("js")!=null?handshake.getJSONObject("js").optString("token",""):"";
        if(token.isEmpty())throw new Exception("لم ينجح تسجيل الدخول إلى Stalker Portal");
        String authCookie=cookie+";"; 
        JSONObject profile=new JSONObject(request(endpoint+"?type=stb&action=get_profile&hd=1&ver=ImageDescription%3A%200.2.18-r23-pub-250%3B%20ImageSDK%3A%200&num_banks=2&sn=&stb_type=MAG250&client_type=STB&image_version=218&video_out=hdmi& JsHttpRequest=1-xml",authCookie,token,18000));
        JSONArray channelsJson=null;
        String[] tries={
          endpoint+"?type=itv&action=get_all_channels&force_ch_link_check=&JsHttpRequest=1-xml",
          endpoint+"?type=itv&action=get_ordered_list&genre=*&force_ch_link_check=&fav=0&sortby=number&hd=0&not_ended=0&p=0&JsHttpRequest=1-xml"
        };
        Exception last=null;
        for(String u:tries){try{JSONObject j=new JSONObject(request(u,authCookie,token,25000));JSONObject js=j.optJSONObject("js");if(js!=null){channelsJson=js.optJSONArray("data");if(channelsJson==null)channelsJson=js.optJSONArray("channels");}if(channelsJson!=null)break;}catch(Exception e){last=e;}}
        if(channelsJson==null)throw new Exception(last==null?"Portal لم يُرجع قائمة قنوات":safeMessage(last));
        ArrayList<Channel> out=new ArrayList<>();
        for(int i=0;i<channelsJson.length();i++){
            JSONObject o=channelsJson.optJSONObject(i);if(o==null)continue;
            String cmd=o.optString("cmd","");String id=o.optString("id",o.optString("ch_id"));
            if(cmd.isEmpty())continue;
            String stream=cmd.replace("ffmpeg ","").replace("auto ","").trim();
            if(!stream.startsWith("http://")&&!stream.startsWith("https://"))stream=endpoint+"?type=itv&action=create_link&cmd="+enc(cmd)+"&JsHttpRequest=1-xml";
            out.add(new Channel(o.optString("name","قناة"),stream,o.optString("tv_genre_title",""),o.optString("logo",""),id));
        }
        return out;
    }

    private void play(Channel c){
        try{
            String url=c.url;
            if(url.contains("action=create_link")){
                status.setText("هذا النوع من روابط Stalker يحتاج إلى طلب جلسة تشغيل خاص؛ قد لا يعمل مباشرة على بعض البوابات.");
            }
            nowPlaying.setText("يُشغّل الآن: "+c.name);
            player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));player.prepare();player.play();
            status.setText("جارٍ بدء البث…");
        }catch(Exception e){status.setText("تعذّر تشغيل القناة: "+safeMessage(e));}
    }

    private void filterChannels(){
        String q=searchField==null?"":searchField.getText().toString().trim().toLowerCase(Locale.ROOT);
        visibleChannels.clear();
        for(Channel c:allChannels){
            if(onlyFavorites&&!favorites.contains(c.key()))continue;
            if(!q.isEmpty()&&!c.name.toLowerCase(Locale.ROOT).contains(q)&&!(c.group!=null&&c.group.toLowerCase(Locale.ROOT).contains(q)))continue;
            visibleChannels.add(c);
        }
        if(listView!=null)((BaseAdapter)listView.getAdapter()).notifyDataSetChanged();
    }
    private void saveFavorites(){prefs.edit().putStringSet("favorites",new HashSet<>(favorites)).apply();}
    private String attr(String line,String key){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:^|\\s)"+java.util.regex.Pattern.quote(key)+"=\"([^\"]*)\"").matcher(line);
        return m.find()?m.group(1):"";
    }
    private String enc(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return s;}}
    private String request(String url,String cookie,String token,int timeout)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url.replace(" ","%20")).openConnection();
        c.setConnectTimeout(timeout);c.setReadTimeout(timeout);c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 12) AlRashedIPTV/2.0");
        c.setRequestProperty("Accept","*/*");
        if(cookie!=null)c.setRequestProperty("Cookie",cookie);
        if(token!=null&&!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
        try(InputStream in=(c.getResponseCode()<400?c.getInputStream():c.getErrorStream());ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            String body=out.toString("UTF-8");
            if(c.getResponseCode()>=400)throw new Exception("HTTP "+c.getResponseCode());
            return body;
        }finally{c.disconnect();}
    }
    private String safeMessage(Exception e){String m=e.getMessage();return m==null?"خطأ غير معروف":m.length()>140?m.substring(0,140):m;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private EditText field(String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(14);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.GRAY);e.setHint(hint);e.setPadding(dp(10),0,dp(10),0);e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(gold));e.setSelectAllOnFocus(false);return e;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.BLACK);b.setAllCaps(false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(gold));return b;}
    private void styleSpinner(Spinner s){s.setBackgroundTintList(android.content.res.ColorStateList.valueOf(gold));}
    private LinearLayout.LayoutParams match(int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,h);p.bottomMargin=dp(5);return p;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onStop(){super.onStop();if(player!=null)player.pause();}
    @Override protected void onDestroy(){if(player!=null)player.release();worker.shutdownNow();super.onDestroy();}
}