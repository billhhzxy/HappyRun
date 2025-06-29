package com.example.happyrunning.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.blankj.utilcode.util.SPUtils;
import com.example.happyrunning.R;
import com.example.happyrunning.commons.bean.PathRecord;
import com.example.happyrunning.commons.bean.SportMotionRecord;
import com.example.happyrunning.commons.utils.LogUtils;
import com.example.happyrunning.commons.utils.Status_sp;
import com.example.happyrunning.commons.utils.UIhelper;
import com.example.happyrunning.commons.utils.Utils;
import com.example.happyrunning.db.DataManager;
import com.example.happyrunning.db.RealmHelper;
import com.example.happyrunning.sport_motion.MotionUtils;
import com.example.happyrunning.sport_motion.PathSmoothTool;
import com.example.happyrunning.ui.BaseActivity;
import com.example.happyrunning.ui.weight.CustomPopWindow;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.OnClick;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 描述: 运动结果
 */
public class SportResultActivity extends BaseActivity {

    @BindView(R.id.ivStar1)
    ImageView ivStar1;
    @BindView(R.id.ivStar2)
    ImageView ivStar2;
    @BindView(R.id.ivStar3)
    ImageView ivStar3;
    @BindView(R.id.tvResult)
    TextView tvResult;
    @BindView(R.id.tvDistancet)
    TextView tvDistancet;
    @BindView(R.id.tvDuration)
    TextView tvDuration;
    @BindView(R.id.tvCalorie)
    TextView tvCalorie;
    @BindView(R.id.mapView)
    MapView mapView;

    private DecimalFormat decimalFormat = new DecimalFormat("0.00");
    private DecimalFormat intFormat = new DecimalFormat("#");

    // 服务器接口地址
    private static final String SERVER_URL = "http://192.168.16.112:8080/api/users/run/data/add";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new GsonBuilder().create();

    private final int AMAP_LOADED = 0x0088;
    private final int SYNC_SUCCESS = 0x0089;
    private final int SYNC_FAILED = 0x0090;

    private Handler handler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case AMAP_LOADED:
                    setupRecord();
                    break;
                case SYNC_SUCCESS:
                    showToast("数据同步成功");
                    break;
                case SYNC_FAILED:
                    showToast("数据同步失败: " + (String) msg.obj);
                    break;
                default:
                    break;
            }
        }
    };

    private AMap aMap;

    private PathRecord pathRecord = null;

    private DataManager dataManager = null;

    private ExecutorService mThreadPool;
    private List<LatLng> mOriginLatLngList;
    private Marker mOriginStartMarker, mOriginEndMarker;
    private Polyline mOriginPolyline;
    private PathSmoothTool mpathSmoothTool = null;
    private PolylineOptions polylineOptions;

    public static String SPORT_START = "SPORT_START";
    public static String SPORT_END = "SPORT_END";

    public static void StartActivity(Activity activity, long mStartTime, long mEndTime) {
        Intent intent = new Intent();
        intent.putExtra(SPORT_START, mStartTime);
        intent.putExtra(SPORT_END, mEndTime);
        intent.setClass(activity, SportResultActivity.class);
        activity.startActivity(intent);
    }

    @Override
    public int getLayoutId() {
        return R.layout.activity_sportresult;
    }

    @Override
    public void initData(Bundle savedInstanceState) {

        mapView.onCreate(savedInstanceState);// 此方法必须重写

        dataManager = new DataManager(new RealmHelper());

        if (!getIntent().hasExtra(SPORT_START) || !getIntent().hasExtra(SPORT_END)) {
            Utils.showToast(this, "参数错误!");
            finish();
        }

        int threadPoolSize = Runtime.getRuntime().availableProcessors() * 2 + 3;
        mThreadPool = Executors.newFixedThreadPool(threadPoolSize);

        initPolyline();

        if (aMap == null)
            aMap = mapView.getMap();

        setUpMap();
    }

    private void initPolyline() {
        polylineOptions = new PolylineOptions();
        polylineOptions.color(getResources().getColor(R.color.colorAccent));
        polylineOptions.width(20f);
        polylineOptions.useGradient(true);

        mpathSmoothTool = new PathSmoothTool();
        mpathSmoothTool.setIntensity(4);
    }

    private void setupRecord() {
        try {
            SportMotionRecord records = dataManager.queryRecord(
                    Integer.parseInt(SPUtils.getInstance().getString(Status_sp.USERID, "0")),
                    getIntent().getLongExtra(SPORT_START, 0),
                    getIntent().getLongExtra(SPORT_END, 0));
            if (null != records) {
                pathRecord = new PathRecord();
                pathRecord.setId(records.getId());
                pathRecord.setmDistance(records.getDistance());
                pathRecord.setmDuration(records.getDuration());
                pathRecord.setmPathLinePoints(MotionUtils.parseLatLngLocations(records.getPathLine()));
                pathRecord.setmStartPoint(MotionUtils.parseLatLngLocation(records.getStratPoint()));
                pathRecord.setmEndPoint(MotionUtils.parseLatLngLocation(records.getEndPoint()));
                pathRecord.setmStartTime(records.getmStartTime());
                pathRecord.setmEndTime(records.getmEndTime());
                pathRecord.setmCalorie(records.getCalorie());
                pathRecord.setmSpeed(records.getSpeed());
                pathRecord.setmDistribution(records.getDistribution());
                pathRecord.setmDateTag(records.getDateTag());

                upDataUI();
                // 数据展示完成后，同步数据到服务器
                syncDataToServer();
            } else {
                pathRecord = null;
                showToast("获取运动数据失败!");
            }
        } catch (Exception e) {
            pathRecord = null;
            showToast("获取运动数据失败!");
            LogUtils.e("获取运动数据失败", e);
        }
    }

    private void upDataUI() {
        tvDistancet.setText(decimalFormat.format(pathRecord.getmDistance() / 1000d));
        tvDuration.setText(MotionUtils.formatseconds(pathRecord.getmDuration()));
        tvCalorie.setText(intFormat.format(pathRecord.getmCalorie()));

        //评分规则：依次判断 距离大于0 ★；运动时间大于40分钟 ★★；速度在3~6km/h之间 ★★★
        if (pathRecord.getmDuration() > (40 * 60) && pathRecord.getmSpeed() > 3) {
            ivStar1.setImageResource(R.mipmap.small_star);
            ivStar2.setImageResource(R.mipmap.big_star);
            ivStar3.setImageResource(R.mipmap.small_star);
            tvResult.setText("跑步效果完美");
        } else if (pathRecord.getmDuration() > (40 * 60)) {
            ivStar1.setImageResource(R.mipmap.small_star);
            ivStar2.setImageResource(R.mipmap.big_star);
            ivStar3.setImageResource(R.mipmap.small_no_star);
            tvResult.setText("跑步效果不错");
        } else {
            ivStar1.setImageResource(R.mipmap.small_star);
            ivStar2.setImageResource(R.mipmap.big_no_star);
            ivStar3.setImageResource(R.mipmap.small_no_star);
            tvResult.setText("跑步效果一般");
        }

        {
            List<LatLng> recordList = pathRecord.getmPathLinePoints();
            LatLng startLatLng = pathRecord.getmStartPoint();
            LatLng endLatLng = pathRecord.getmEndPoint();
            if (recordList == null || startLatLng == null || endLatLng == null) {
                return;
            }
            mOriginLatLngList = mpathSmoothTool.pathOptimize(recordList);
            addOriginTrace(startLatLng, endLatLng, mOriginLatLngList);
        }
    }

    @Override
    public void initListener() {
        aMap.setOnMapLoadedListener(() -> {
            Message msg = handler.obtainMessage();
            msg.what = AMAP_LOADED;
            handler.sendMessage(msg);
        });
    }

    @OnClick({R.id.tvResult, R.id.ll_share})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.tvResult:
                new CustomPopWindow.PopupWindowBuilder(this)
                        .setView(R.layout.layout_sport_result_tip)
                        .setFocusable(true)
                        .setOutsideTouchable(true)
                        .create()
                        .showAsDropDown(tvResult, -200, 10);
                break;
            case R.id.ll_share:
                if (null != pathRecord) {
                    systemShareTxt();
                } else {
                    showToast("获取运动数据失败!");
                }
                break;
            default:
                break;
        }
    }

    /**
     * 设置一些amap的属性
     */
    private void setUpMap() {
        // 自定义系统定位小蓝点
        MyLocationStyle myLocationStyle = new MyLocationStyle();
        myLocationStyle.strokeColor(Color.TRANSPARENT);// 设置圆形的边框颜色
        myLocationStyle.radiusFillColor(Color.argb(0, 0, 0, 0));// 设置圆形的填充颜色
        myLocationStyle.strokeWidth(1.0f);// 设置圆形的边框粗细
        aMap.setMyLocationStyle(myLocationStyle);
        aMap.getUiSettings().setMyLocationButtonEnabled(false);// 设置默认定位按钮是否显示
        aMap.getUiSettings().setScaleControlsEnabled(true);// 设置比例尺显示
        aMap.getUiSettings().setZoomControlsEnabled(false);// 设置默认缩放按钮是否显示
        aMap.getUiSettings().setCompassEnabled(false);// 设置默认指南针是否显示
        aMap.setMyLocationEnabled(false);// 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false
    }

    /**
     * 地图上添加原始轨迹线路及起终点、轨迹动画小人
     *
     * @param startPoint
     * @param endPoint
     * @param originList
     */
    private void addOriginTrace(LatLng startPoint, LatLng endPoint,
                                List<LatLng> originList) {
        polylineOptions.addAll(originList);
        mOriginPolyline = aMap.addPolyline(polylineOptions);
        mOriginStartMarker = aMap.addMarker(new MarkerOptions().position(
                startPoint).icon(
                BitmapDescriptorFactory.fromResource(R.drawable.sport_start)));
        mOriginEndMarker = aMap.addMarker(new MarkerOptions().position(
                endPoint).icon(
                BitmapDescriptorFactory.fromResource(R.drawable.sport_end)));

        try {
            aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(getBounds(), 16));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private LatLngBounds getBounds() {
        LatLngBounds.Builder b = LatLngBounds.builder();
        if (mOriginLatLngList == null) {
            return b.build();
        }
        for (LatLng latLng : mOriginLatLngList) {
            b.include(latLng);
        }
        return b.build();
    }

    /**
     * 调用系统分享文本
     */
    private void systemShareTxt() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, UIhelper.getString(R.string.app_name) + "运动");
        intent.putExtra(Intent.EXTRA_TEXT, "我在" + UIhelper.getString(R.string.app_name) + "运动跑了" + decimalFormat.format(pathRecord.getmDistance())
                + "公里,运动了" + decimalFormat.format(pathRecord.getmDuration() / 60) + "分钟!快来加入吧!");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(Intent.createChooser(intent, "分享到"));
    }

    /**
     * 调用系统分享图片
     */
    private void systemSharePic(String imagePath) {
        //由文件得到uri
        Uri imageUri = Uri.fromFile(new File(imagePath));
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
        shareIntent.setType("image/*");
        startActivity(Intent.createChooser(shareIntent, "分享到"));
    }

    /**
     * 同步数据到服务器
     */
    private void syncDataToServer() {
        if (pathRecord == null) {
            showToast("没有可同步的运动数据");
            return;
        }

        // 构建UserRunDataDto对象
        UserRunDataDto dto = new UserRunDataDto();
        dto.setPhone(SPUtils.getInstance().getString(Status_sp.PHONE, "")); // 从SP获取手机号
        dto.setSpeed(decimalFormat.format(pathRecord.getmSpeed())); // 平均时速(公里/小时)
        dto.setRate(decimalFormat.format(pathRecord.getmDistribution())); // 平均配速(分钟/公里)
        dto.setKilometer(decimalFormat.format(pathRecord.getmDistance() / 1000d)); // 公里数
        dto.setCalorie(intFormat.format(pathRecord.getmCalorie())); // 卡路里
        dto.setTotalTime(MotionUtils.formatseconds(pathRecord.getmDuration())); // 总时间格式化为00:00:00
        dto.setCreateTime(formatDate(new Date(pathRecord.getmEndTime()))); // 创建时间格式转换

        // 转换为JSON
        String json = gson.toJson(dto);
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(SERVER_URL)
                .post(body)
                .build();

        // 发送请求
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    handler.sendEmptyMessage(SYNC_SUCCESS);
                } else {
                    handler.obtainMessage(SYNC_FAILED, "请求失败，状态码：" + response.code()).sendToTarget();
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                handler.obtainMessage(SYNC_FAILED, "网络错误：" + e.getMessage()).sendToTarget();
            }
        });
    }

    /**
     * 格式化时间为00:00:00格式
     */
    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;

        return String.format(Locale.CHINA, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * 格式化日期为2025-06-18 23:34:56格式
     */
    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        return sdf.format(date);
    }

    /**
     * UserRunDataDto数据传输对象（整合在活动内部）
     */
    private class UserRunDataDto implements Serializable {
        private Long id;
        private String phone;
        private String speed;
        private String rate;
        private String kilometer;
        private String calorie;
        private String totalTime;
        private String createTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getSpeed() { return speed; }
        public void setSpeed(String speed) { this.speed = speed; }
        public String getRate() { return rate; }
        public void setRate(String rate) { this.rate = rate; }
        public String getKilometer() { return kilometer; }
        public void setKilometer(String kilometer) { this.kilometer = kilometer; }
        public String getCalorie() { return calorie; }
        public void setCalorie(String calorie) { this.calorie = calorie; }
        public String getTotalTime() { return totalTime; }
        public void setTotalTime(String totalTime) { this.totalTime = totalTime; }
        public String getCreateTime() { return createTime; }
        public void setCreateTime(String createTime) { this.createTime = createTime; }
    }

    @Override
    protected void onDestroy() {
        mapView.onDestroy();

        if (mThreadPool != null)
            mThreadPool.shutdownNow();

        if (null != dataManager)
            dataManager.closeRealm();

        super.onDestroy();
    }

    @Override
    public void onResume() {
        mapView.onResume();
        super.onResume();
    }

    @Override
    public void onPause() {
        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}